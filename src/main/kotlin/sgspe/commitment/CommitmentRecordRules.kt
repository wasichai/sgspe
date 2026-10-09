package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de datos de los compromisos y sus hijos, puras para las pruebas unitarias
object CommitmentData {
    // RN-ENG-001: los talleres y las reuniones informativas no generan compromisos salvo con justificación (ASM-007)
    val WORKSHOP_TYPES = setOf("TALLER", "REUNION INFORMATIVA")

    // un alta o un cambio del origen se juzga; los demás cambios no
    fun originJudged(
        kind: RecordChangeKind,
        changed: Set<String>
    ): Boolean = kind == RecordChangeKind.CREATED || "origin_engagement" in changed || "origin_justification" in changed

    // RN-ENG-001 sobre el registro de después: null si pasa
    fun workshopFinding(
        originType: String?,
        justification: Any?,
        blocked: Boolean
    ): Finding? {
        if (originType !in WORKSHOP_TYPES) return null
        if (blocked) {
            return Finding(
                "WORKSHOP_COMMITMENT_BLOCKED",
                "El proyecto no admite compromisos nacidos de un taller o de una reunión informativa (RN-ENG-001)",
                "origin_engagement"
            )
        }
        if (Values.text(justification) != null) return null
        return Finding(
            "JUSTIFICATION_REQUIRED",
            "Un compromiso que nace de un taller o de una reunión informativa exige justificación (origin_justification)",
            "origin_justification",
            status = HttpStatus.BAD_REQUEST
        )
    }

    // si agregar la arista from → to cierra un ciclo: to llega a from siguiendo las dependencias. `next` da los compromisos de los
    // que depende uno; `max` acota los compromisos que se visitan (un grafo más grande falla cerrado, como un ciclo)
    suspend fun createsCycle(
        from: UUID,
        to: UUID,
        max: Int,
        next: suspend (UUID) -> List<UUID>
    ): CycleCheck {
        if (from == to) return CycleCheck.CYCLE
        val seen = mutableSetOf(to)
        val frontier = ArrayDeque(listOf(to))
        while (frontier.isNotEmpty()) {
            for (other in next(frontier.removeFirst())) {
                if (other == from) return CycleCheck.CYCLE
                if (seen.add(other)) {
                    if (seen.size > max) return CycleCheck.TOO_LARGE
                    frontier.addLast(other)
                }
            }
        }
        return CycleCheck.NONE
    }

    enum class CycleCheck { NONE, CYCLE, TOO_LARGE }
}

// CommitmentWriteGuard, alta y edición (domain-model §7.1): RN-ENG-001 al crear o cambiar el origen, la fecha de
// vencimiento que desde approved solo cambia con una reprogramación (RF-COM-003), las dependencias sin ciclos (RF-COM-004) y
// las entregas que ya sostienen un cumplimiento (api.md §4.5: corregirlas exige reopen_execution)
@Component
class CommitmentRecordRules(
    private val records: Records,
    private val view: WriteView
) : CommitmentRule {
    override val order = CommitmentRuleOrder.RECORD

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when (change.objectName) {
            Commitments.COMMITMENT -> commitment(change)
            Commitments.DEPENDENCY -> dependency(change)
            Commitments.DELIVERY -> delivery(change)
            else -> emptyList()
        }

    private suspend fun commitment(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change, after)
        val findings = mutableListOf<Finding>()
        if (CommitmentData.originJudged(change.kind, changed)) {
            val origin = Records.uuid(after["origin_engagement"])
            if (origin != null) {
                // un origen que no se lee (no existe o está fuera del alcance) no pasa en silencio (P12)
                val engagement = readAs("engagement") { records.find("engagement", origin) } ?: throw NotReadable("engagement")
                val type = engagement.attributes["engagement_type"]
                val blocked =
                    Values.text(type) in CommitmentData.WORKSHOP_TYPES &&
                        ProjectParameters.policy(project(after), "policy_workshop_commitments_blocked")
                CommitmentData.workshopFinding(Values.text(type), after["origin_justification"], blocked)?.let { findings += it }
            }
        }
        val id = change.recordId
        if (change.kind == RecordChangeKind.UPDATED && id != null && "due_date" in changed && !WriteMark.present()) {
            // el estado de antes: wasichai 0.6.0 no lo trae en `before` (solo los atributos), así que se lee como quien escribe y
            // sin lectura falla cerrado
            val state =
                readAs(Commitments.COMMITMENT) { records.find(Commitments.COMMITMENT, id) }?.state
                    ?: throw NotReadable(Commitments.COMMITMENT)
            if (state in Commitments.AFTER_APPROVAL) {
                findings +=
                    Finding(
                        "DUE_DATE_RESCHEDULE_ONLY",
                        "Desde la aprobación, la fecha de vencimiento solo cambia con una reprogramación (POST /api/sgspe/commitments/{id}/reschedules)",
                        "due_date"
                    )
            }
        }
        return findings
    }

    // RF-COM-004. una dependencia que no bloquea (blocking = false) también cuenta para el ciclo. límite aceptado en F3
    // (recogido para la Tarea 23): la búsqueda lee como quien escribe, así que no ve dependencias de proyectos fuera de su
    // alcance, y no se serializa: dos altas simultáneas a → b y b → a pueden pasar las dos
    private suspend fun dependency(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val from = Records.uuid(after["commitment"]) ?: return emptyList()
        val to = Records.uuid(after["depends_on"]) ?: return emptyList()
        if (from == to) {
            return listOf(Finding("DEPENDENCY_SELF", "Un compromiso no depende de sí mismo", "depends_on", status = HttpStatus.BAD_REQUEST))
        }
        val result =
            CommitmentData.createsCycle(from, to, MAX_GRAPH) { node ->
                readAs(Commitments.DEPENDENCY) {
                    records
                        .all(Commitments.DEPENDENCY, filters = mapOf("commitment" to node.toString()), max = MAX_GRAPH)
                        .filter { it.id != change.recordId?.toString() }
                        .mapNotNull { Records.uuid(it.attributes["depends_on"]) }
                }
            }
        return when (result) {
            CommitmentData.CycleCheck.NONE -> emptyList()
            CommitmentData.CycleCheck.CYCLE ->
                listOf(Finding("DEPENDENCY_CYCLE", "La dependencia cerraría un ciclo entre compromisos (RF-COM-004)", "depends_on"))
            CommitmentData.CycleCheck.TOO_LARGE ->
                listOf(Finding("DEPENDENCY_GRAPH_TOO_LARGE", "Las dependencias encadenan más de $MAX_GRAPH compromisos: no se pueden verificar", "depends_on"))
        }
    }

    // una entrega cuyo compromiso ya está cumplido sostiene esa transición: no cambia, no se vincula ni se desvincula hasta
    // reopen_execution. el compromiso es el de antes: la corrección (Deliveries.patch) nunca lo cambia. su estado no viene en
    // `before` (wasichai 0.6.0): se lee como quien escribe y, si no se lee, falla cerrado (P12)
    private suspend fun delivery(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.UPDATED && change.kind != RecordChangeKind.DELETED) return emptyList()
        val commitment = Records.uuid(change.before?.get("commitment")) ?: return emptyList()
        val state =
            readAs(Commitments.COMMITMENT) { records.find(Commitments.COMMITMENT, commitment) }?.state
                ?: throw NotReadable(Commitments.COMMITMENT)
        if (state !in Commitments.FULFILLED) return emptyList()
        return listOf(
            Finding(
                "DELIVERY_FROZEN",
                "La entrega sostiene el cumplimiento del compromiso: devuélvalo a ejecución (reopen_execution) para corregirla",
                "commitment"
            )
        )
    }

    // el proyecto del registro, para sus políticas (ASM-019); sin lectura de project, NotReadable
    private suspend fun project(after: Map<String, Any?>): Map<String, Any?> {
        val id = Records.uuid(after["project"]) ?: return emptyMap()
        return readAs("project") { records.find("project", id) }?.attributes.orEmpty()
    }

    companion object {
        // los compromisos que la búsqueda de ciclos visita como mucho
        const val MAX_GRAPH = 500
    }
}
