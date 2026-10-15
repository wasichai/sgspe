package sgspe.deliverable

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.PseudonymMarkers
import sgspe.common.Records
import sgspe.common.RuleException
import sgspe.common.readable
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// lo que AI-04 lee de una sección de entregable: la sección y su entregable, el proyecto, la clave, el título, la guía, el
// periodo, el texto ya armado como la persona y su última escritura (la referencia de la entrada)
data class SectionSource(
    val section: UUID,
    val deliverable: UUID,
    val project: UUID?,
    val key: String,
    val title: String,
    val guide: String,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val text: String,
    val updatedAt: String?
)

// las secciones de entregable para AI-04 (especificación de F8 §3.9): la fuente de un borrador, como la persona que pide,
// y la adopción de una sugerencia (adopt). sgspe.ai depende de este servicio, nunca al revés. la fuente ensambla el entregable
// entero con un ReportSource de persona y toma los bloques de la sección (decisión 16): exactamente lo que el tipo lee para
// ella. una sección que no lee es el 404 de wasichai; una que el tipo no marca con IA, 422 SECTION_NOT_ASSISTED sobre
// target.recordId; un entregable sin sus parámetros, 422 DELIVERABLE_PARAMETERS_MISSING. nunca lee el modelo congelado
@Service
class DeliverableSections(
    private val records: Records,
    private val types: DeliverableTypes,
    private val sources: ReportSources,
    private val callers: Callers,
    private val permissions: Permissions,
    private val clock: Clock
) {
    // `nameable`: si la seudonimización de la corrida reemplaza entero un nombre (SectionText)
    suspend fun source(
        sectionId: UUID,
        nameable: (String) -> Boolean
    ): SectionSource {
        val section = records.get(Deliverables.SECTION, sectionId)
        val deliverableId = Records.uuid(section.attributes[Deliverables.DELIVERABLE]) ?: throw notAssisted()
        val deliverable = records.get(Deliverables.DELIVERABLE, deliverableId)
        val key = section.attributes[Deliverables.SECTION_KEY]?.toString()
        val type = types.of(deliverable.attributes[Deliverables.TYPE]?.toString()) ?: throw notAssisted()
        val spec = type.sections.firstOrNull { it.key == key && it.assisted } ?: throw notAssisted()
        val project = Records.uuid(deliverable.attributes[Deliverables.PROJECT])
        val projectRow = project?.let { records.get(Deliverables.PROJECT, it) }
        val missing = type.missingParameters(deliverable.attributes, projectRow?.attributes.orEmpty())
        if (missing.isNotEmpty()) throw parametersMissing(missing)
        val sections = records.all(Deliverables.SECTION, mapOf(Deliverables.DELIVERABLE to deliverableId.toString()))
        val input =
            AssemblyInput(
                deliverable = SourceRow(deliverable.id, deliverable.state, deliverable.attributes),
                sections = sections.map { SourceRow(it.id, it.state, it.attributes) },
                templateId = null,
                templateSha256 = "",
                draftRevision = "",
                issueRevision = "",
                today = LocalDate.now(clock.withZone(LIMA))
            )
        val assembled =
            try {
                type.assemble(input, sources.person(type))
            } catch (e: DraftFailure) {
                throw parametersMissing(listOf(e.section ?: Deliverables.TYPE))
            }
        val blocks =
            assembled.model.sections
                .first { it.key == spec.key }
                .blocks
        return SectionSource(
            section = sectionId,
            deliverable = deliverableId,
            project = project,
            key = spec.key,
            title = spec.title,
            guide = spec.guide,
            periodStart =
                deliverable.attributes[Deliverables.PERIOD_START]
                    ?.toString()
                    ?.take(10)
                    ?.let(LocalDate::parse),
            periodEnd =
                deliverable.attributes[Deliverables.PERIOD_END]
                    ?.toString()
                    ?.take(10)
                    ?.let(LocalDate::parse),
            text = SectionText.render(blocks, assembled.model.people, nameable),
            updatedAt = section.updatedAt?.toString()
        )
    }

    // la adopción de una sugerencia en una sección (especificación de F8 §3.9; ASM-137), dentro de la transacción de WF-K de
    // SuggestionAcceptance y como quien acepta: el texto revisado con sus marcadores convertidos, UPDATE sobre la sección, la
    // sección leída por quien acepta (403 OBJECT_NOT_READABLE) y del proyecto de la sugerencia (409 PROJECT_MISMATCH); escribe
    // content, content_origin ASISTIDO POR IA y ai_suggestion con el motivo, y DeliverableWriteGuard cierra el origen
    suspend fun adopt(
        sectionId: UUID,
        content: Any?,
        suggestion: UUID,
        project: UUID?,
        reason: String
    ): RecordResponse {
        val text =
            when (content) {
                null -> null
                is String -> content.trim().ifEmpty { null }
                else -> throw Finding("INVALID_VALUE", "El texto de la sección es un texto", CONTENT_FIELD, status = HttpStatus.BAD_REQUEST).toCodedException()
            } ?: throw Finding("FIELD_REQUIRED", "Falta el texto revisado de la sección", CONTENT_FIELD, status = HttpStatus.BAD_REQUEST).toCodedException()
        val converted = markers(text)
        permissions.require(callers.require(), "Usar la sugerencia en la sección", Actions.UPDATE to Deliverables.SECTION)
        val section =
            try {
                records.readable(Deliverables.SECTION, sectionId)
            } catch (_: NotReadable) {
                throw Finding(
                    "OBJECT_NOT_READABLE",
                    "La sección no existe o quien acepta no puede leerla",
                    "attributes.section",
                    status = HttpStatus.FORBIDDEN
                ).toCodedException()
            }
        if (project != null) {
            val deliverable = records.get(Deliverables.DELIVERABLE, Records.uuid(section.attributes[Deliverables.DELIVERABLE])!!)
            if (Records.uuid(deliverable.attributes[Deliverables.PROJECT]) != project) {
                throw Finding(
                    "PROJECT_MISMATCH",
                    "La sección es de otro proyecto que la sugerencia",
                    "attributes.section",
                    status = HttpStatus.CONFLICT
                ).toCodedException()
            }
        }
        return records.patch(
            Deliverables.SECTION,
            sectionId,
            mapOf(Deliverables.CONTENT to converted, Deliverables.CONTENT_ORIGIN to Deliverables.AI_ASSISTED, AI_SUGGESTION to suggestion.toString()),
            reason
        )
    }

    companion object {
        const val SECTION_NOT_ASSISTED = "SECTION_NOT_ASSISTED"
        const val AI_SUGGESTION = "ai_suggestion"
        private const val CONTENT_FIELD = "attributes.content"

        // con las comillas angulares que SectionText pone alrededor de un nombre, solo si las dos envuelven el marcador
        private val STABLE_PERSON = Regex("""«\[PERSONA:([0-9a-fA-F-]{36})]»|\[PERSONA:([0-9a-fA-F-]{36})]""")
        private val UNRESOLVED_PERSON = Regex("""\[PERSONA(_\d+)?]""")

        // [PERSONA:<id>] → [[person:<id>]] (§2.2.9), sin las «» que lo envuelven; [PERSONA] o [PERSONA_n] sin id, 422
        // UNRESOLVED_PSEUDONYM; cualquier otro marcador, 400 PSEUDONYM_NOT_ALLOWED (decisión 19 del plan 8.2c). nada se
        // escribe si falla
        fun markers(text: String): String {
            val converted =
                STABLE_PERSON.replace(text) { match ->
                    val id = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    "[[person:${id.lowercase()}]]"
                }
            if (UNRESOLVED_PERSON.containsMatchIn(converted)) {
                throw Finding(
                    "UNRESOLVED_PSEUDONYM",
                    "Reemplace [PERSONA] por la persona citada o quite la mención antes de usar el texto",
                    CONTENT_FIELD,
                    status = HttpStatus.UNPROCESSABLE_CONTENT
                ).toCodedException()
            }
            if (PseudonymMarkers.has(converted)) throw PseudonymMarkers.notAllowed("content")
            return converted
        }

        fun notAssisted(): WasichaiException =
            Finding(
                SECTION_NOT_ASSISTED,
                "El tipo de entregable no marca esta sección para borradores de IA",
                "target.recordId",
                status = HttpStatus.UNPROCESSABLE_CONTENT
            ).toCodedException()

        private fun parametersMissing(fields: List<String>): RuleException =
            RuleException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Faltan parámetros del tipo para redactar la sección",
                fields.map { FieldViolation(it, DeliverableDrafts.PARAMETERS_MISSING) }
            )
    }
}
