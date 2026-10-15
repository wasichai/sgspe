package sgspe.deliverable

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RuleException
import sgspe.common.StoredFiles
import sgspe.common.Values
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.toCodedException
import wasichai.core.common.FieldViolation
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import java.time.Instant
import java.util.UUID

// las reglas de BC-15 que la API genérica no expresa (especificación de F8 §3.5, deliverables.md §8.6, api.md §4.12):
// - deliverable: deliverable_type no cambia después del alta (400 FIELD_NOT_EDITABLE); current_version solo la escribe el
//   código de sgspe con la marca (400 DERIVED_FIELD; la ficha la llama derivada y DerivedFieldsGuard no mira relaciones,
//   decisión 13 del plan 8.1); submit_for_review, resubmit y approve piden el borrador LISTO (409 STATE_MISMATCH sobre draft)
//   y ninguna sección con updated_at posterior a draft_generated_at (409 SECTIONS_CHANGED, un error por sección); approve
//   además la acción APPROVE (403 ACTION_REQUIRED)
// - deliverable_section: con origen ASISTIDO POR IA, toda escritura lleva motivo (400 FIELD_REASON_REQUIRED), y una sección
//   asistida no vuelve a otro origen ni se borra (422 AI_ORIGIN_LOCKED; decisión 8 del plan 8.2b: el cerrojo dura toda la vida
//   del entregable)
// - deliverable_version: una INTERNO trae docx y docx_sha256 (400 FIELD_REQUIRED) y cada *_sha256 es el del descriptor de su
//   archivo (400 SHA256_MISMATCH); la derivada EXTERNO no trae docx ni model
@Component
@Order(GuardOrder.DOMAIN)
class DeliverableWriteGuard(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val view: WriteView,
    private val files: StoredFiles
) : RecordWriteGuard {
    override suspend fun beforeWrite(change: RecordWrite) {
        when (change.objectName) {
            Deliverables.DELIVERABLE -> deliverable(change)
            Deliverables.SECTION -> section(change)
            Deliverables.VERSION -> version(change)
        }
    }

    private suspend fun deliverable(change: RecordWrite) {
        when (change.kind) {
            RecordChangeKind.UPDATED -> {
                if (change.isLink()) return
                val sent = change.attributes.orEmpty()
                val before = change.before.orEmpty()
                if (Deliverables.TYPE in sent && Deliverables.TYPE in view.changed(change)) {
                    throw Finding(
                        "FIELD_NOT_EDITABLE",
                        "El tipo de un entregable no cambia después del alta",
                        Deliverables.TYPE,
                        status = HttpStatus.BAD_REQUEST
                    ).toCodedException()
                }
                if (!WriteMark.present() && Deliverables.CURRENT_VERSION in sent &&
                    !Values.same(ref(sent[Deliverables.CURRENT_VERSION]), ref(before[Deliverables.CURRENT_VERSION]))
                ) {
                    throw Finding(
                        "DERIVED_FIELD",
                        "El campo current_version es derivado: lo escribe la emisión",
                        Deliverables.CURRENT_VERSION,
                        status = HttpStatus.BAD_REQUEST
                    ).toCodedException()
                }
            }
            RecordChangeKind.TRANSITIONED ->
                if (change.transition in REVIEWED) {
                    if (change.transition == Deliverables.APPROVE_TRANSITION) approver()
                    reviewed(change.recordId ?: return, change.before.orEmpty())
                }
            else -> Unit
        }
    }

    private suspend fun approver() {
        val user = callers.userOrNull() ?: return
        if (!permissions.has(user, Deliverables.APPROVE, Deliverables.DELIVERABLE)) {
            throw Finding("ACTION_REQUIRED", "Aprobar un entregable exige la acción APPROVE sobre deliverable", "record", status = HttpStatus.FORBIDDEN)
                .toCodedException()
        }
    }

    // el borrador LISTO y ninguna sección cambiada después de congelar el modelo (también lo repite la emisión)
    suspend fun reviewed(
        deliverableId: UUID,
        attributes: Map<String, Any?>
    ) {
        if (attributes[Deliverables.STATUS]?.toString() != Deliverables.READY) {
            throw Finding("STATE_MISMATCH", "El último borrador no está LISTO: pida uno y espere a que termine", "draft").toCodedException()
        }
        val frozen = Deliverables.instant(attributes[Deliverables.GENERATED_AT]) ?: Instant.EPOCH
        val changed =
            records
                .all(Deliverables.SECTION, mapOf(Deliverables.DELIVERABLE to deliverableId.toString()))
                .filter { it.updatedAt?.isAfter(frozen) == true }
                .mapNotNull { it.attributes[Deliverables.SECTION_KEY]?.toString() }
                .sorted()
        if (changed.isNotEmpty()) throw sectionsChanged(changed)
    }

    private suspend fun section(change: RecordWrite) {
        // borrar una sección asistida y crearla de nuevo con la misma clave la sacaría de ai_assisted_sections
        if (change.kind == RecordChangeKind.DELETED && change.before?.get(Deliverables.CONTENT_ORIGIN)?.toString() == Deliverables.AI_ASSISTED) {
            throw locked("Una sección asistida por IA no se borra: la versión emitida debe listarla")
        }
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return
        if (change.isLink()) return
        val after = view.after(change)
        val before = change.before.orEmpty()
        val wasAssisted = before[Deliverables.CONTENT_ORIGIN]?.toString() == Deliverables.AI_ASSISTED
        val isAssisted = after[Deliverables.CONTENT_ORIGIN]?.toString() == Deliverables.AI_ASSISTED
        if (wasAssisted && !isAssisted) throw locked("Una sección asistida por IA no vuelve a otro origen: la versión emitida debe listarla")
        if ((wasAssisted || isAssisted) && change.reason.isNullOrBlank() && view.changed(change, after).isNotEmpty()) {
            throw Finding(
                "FIELD_REASON_REQUIRED",
                "Escribir una sección asistida por IA exige un motivo (X-Change-Reason)",
                "reason",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
    }

    private suspend fun version(change: RecordWrite) {
        if (change.kind != RecordChangeKind.CREATED) return
        val a = change.attributes.orEmpty()
        if (a[Deliverables.LEVEL]?.toString() == Deliverables.INTERNAL) {
            listOf(Deliverables.DOCX, "docx_sha256").firstOrNull { Values.text(fileId(a[it]) ?: a[it]) == null }?.let {
                throw Finding(
                    "FIELD_REQUIRED",
                    "Una versión INTERNO conserva el DOCX editable y su SHA-256",
                    it,
                    status = HttpStatus.BAD_REQUEST
                ).toCodedException()
            }
        }
        for ((field, hash) in HASHED) {
            val id = fileId(a[field]) ?: continue
            val stored = files.find(change.organizationId, id)?.sha256
            if (stored == null || stored != Values.text(a[hash])) {
                throw Finding("SHA256_MISMATCH", "El SHA-256 de $field no es el de su archivo", hash, status = HttpStatus.BAD_REQUEST).toCodedException()
            }
        }
    }

    private fun locked(message: String) =
        Finding("AI_ORIGIN_LOCKED", message, Deliverables.CONTENT_ORIGIN, status = HttpStatus.UNPROCESSABLE_CONTENT).toCodedException()

    private fun ref(value: Any?): String? = (value as? Map<*, *>)?.get("id")?.toString() ?: Values.text(value)

    private fun fileId(value: Any?): UUID? = ref(value)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        val REVIEWED: Set<String> = setOf(Deliverables.SUBMIT, Deliverables.RESUBMIT, Deliverables.APPROVE_TRANSITION)
        private val HASHED = listOf(Deliverables.DOCX to "docx_sha256", Deliverables.PDF to "pdf_sha256", Deliverables.MODEL to "model_sha256")

        fun sectionsChanged(keys: List<String>): RuleException =
            RuleException(
                HttpStatus.CONFLICT,
                "Cambiaron secciones después de congelar el borrador: pida uno nuevo",
                keys.map { FieldViolation("${Deliverables.SECTION}.$it", "SECTIONS_CHANGED") }
            )
    }
}
