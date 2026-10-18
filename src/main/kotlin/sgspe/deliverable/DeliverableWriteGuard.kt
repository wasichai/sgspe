package sgspe.deliverable

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.RuleException
import sgspe.common.StoredFiles
import sgspe.common.Values
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import sgspe.common.toCodedException
import sgspe.privacy.Mentions
import wasichai.core.common.FieldViolation
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.Instant
import java.util.UUID

// las reglas de BC-15 que la API genérica no expresa (especificación de F8 §3.5, deliverables.md §8.6, api.md §4.12):
// - deliverable: deliverable_type no cambia después del alta (400 FIELD_NOT_EDITABLE); current_version solo la escribe el
//   código de sgspe con la marca (400 DERIVED_FIELD, también si un PUT la omite y la dejaría vacía; la ficha la llama derivada
//   y DerivedFieldsGuard no mira relaciones, decisión 13 del plan 8.1); submit_for_review, resubmit y approve piden el
//   borrador LISTO (409 STATE_MISMATCH sobre draft) y ninguna sección con updated_at posterior a draft_generated_at (409 SECTIONS_CHANGED, un error por sección); approve
//   además la acción APPROVE (403 ACTION_REQUIRED)
// - deliverable_section: con origen ASISTIDO POR IA, toda escritura lleva motivo (400 FIELD_REASON_REQUIRED), y una sección
//   asistida no vuelve a otro origen ni se borra (422 AI_ORIGIN_LOCKED; decisión 8 del plan 8.2b: el cerrojo dura toda la vida
//   del entregable); section_key y deliverable no cambian después del alta (400 FIELD_NOT_EDITABLE), o el cambio sacaría la
//   sección del cerrojo; una mención nueva de content ([[person:<id>]], [[position:<id>]] o [[stakeholder:<id>]], las que
//   el contenido anterior no traía) exige que quien escribe lea el registro y, si cita a una persona, sus nombres (422
//   MENTION_NOT_READABLE sobre content, #81): JOB-05 las resuelve como la plataforma. no existir y no leerse dan el mismo error
// - deliverable_version: una INTERNO trae docx y docx_sha256 (400 FIELD_REQUIRED) y cada *_sha256 es el del descriptor de su
//   archivo (400 SHA256_MISMATCH); la derivada EXTERNO no trae docx ni model
@Component
@Order(GuardOrder.DOMAIN)
class DeliverableWriteGuard(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val view: WriteView,
    private val files: StoredFiles,
    private val metadata: MetadataService,
    private val clock: Clock
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
                // también omitida: el PUT de wasichai deja vacía la relación que el cuerpo no trae, y DerivedFieldsKeeper solo
                // repone derivados; reenviarla tal como se leyó no es cambiarla
                if (!WriteMark.present() &&
                    !Values.same(ref(view.after(change)[Deliverables.CURRENT_VERSION]), ref(before[Deliverables.CURRENT_VERSION]))
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
        // tras issue y start_revision el borrador sigue LISTO con el modelo ya emitido: volver a revisarlo crearía versiones con
        // la misma revisión (RQ-REP-016, ASM-027). una emisión posterior al intento pide un borrador nuevo
        if (Deliverables.issuedSince(records, deliverableId, frozen, clock.instant())) {
            throw Finding("STATE_MISMATCH", "El último borrador ya se emitió: pida uno nuevo para esta revisión", "draft").toCodedException()
        }
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
        // renombrar la clave o mover la sección a otro entregable sacaría la sección asistida de ai_assisted_sections, como
        // borrarla y crearla de nuevo: ni la clave ni el entregable cambian después del alta
        if (change.kind == RecordChangeKind.UPDATED) {
            val moved = view.changed(change, after)
            listOf(Deliverables.SECTION_KEY, Deliverables.DELIVERABLE).firstOrNull { it in moved }?.let {
                throw Finding(
                    "FIELD_NOT_EDITABLE",
                    "La clave y el entregable de una sección no cambian después del alta",
                    it,
                    status = HttpStatus.BAD_REQUEST
                ).toCodedException()
            }
        }
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
        mentions(Values.text(before[Deliverables.CONTENT]).orEmpty(), Values.text(after[Deliverables.CONTENT]).orEmpty())
    }

    private suspend fun mentions(
        before: String,
        after: String
    ) {
        callers.userOrNull() ?: return
        val added = Mentions.keys(after) - Mentions.keys(before).toSet()
        if (added.isEmpty()) return
        val readability = Readability(metadata)
        for (key in added) {
            if (!readable(key, readability)) {
                throw Finding(
                    "MENTION_NOT_READABLE",
                    "La sección cita un registro que quien escribe no puede leer, o a una persona cuyos nombres no lee",
                    Deliverables.CONTENT,
                    status = HttpStatus.UNPROCESSABLE_CONTENT
                ).toCodedException()
            }
        }
    }

    // el registro de la mención como quien escribe y, para una persona (también la titular de un cargo o de un grupo de interés),
    // sus nombres: lo que INTERNO imprimirá
    private suspend fun readable(
        key: String,
        readability: Readability
    ): Boolean {
        val objectName = key.substringBefore(':')
        val id = UUID.fromString(key.substringAfter(':'))
        val row =
            try {
                records.readable(objectName, id)
            } catch (_: NotReadable) {
                return false
            }
        val person =
            if (objectName == PERSON) id else ref(row.attributes[PERSON])?.let(UUID::fromString) ?: return true
        if (objectName != PERSON) {
            try {
                records.readable(PERSON, person)
            } catch (_: NotReadable) {
                return false
            }
        }
        return readability.missing(NAMES).isEmpty()
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
        private const val PERSON = "person"
        private val NAMES = listOf("$PERSON.given_names", "$PERSON.surnames")
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
