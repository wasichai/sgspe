package sgspe.deliverable

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Records
import sgspe.common.RuleException
import sgspe.common.toCodedException
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException
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
// y (Tarea 15 del plan 8.2c) la adopción. sgspe.ai depende de este servicio, nunca al revés. la fuente ensambla el entregable
// entero con un ReportSource de persona y toma los bloques de la sección (decisión 16): exactamente lo que el tipo lee para
// ella. una sección que no lee es el 404 de wasichai; una que el tipo no marca con IA, 422 SECTION_NOT_ASSISTED sobre
// target.recordId; un entregable sin sus parámetros, 422 DELIVERABLE_PARAMETERS_MISSING. nunca lee el modelo congelado
@Service
class DeliverableSections(
    private val records: Records,
    private val types: DeliverableTypes,
    private val sources: ReportSources,
    private val clock: Clock
) {
    suspend fun source(sectionId: UUID): SectionSource {
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
            text = SectionText.render(blocks, assembled.model.people),
            updatedAt = section.updatedAt?.toString()
        )
    }

    companion object {
        const val SECTION_NOT_ASSISTED = "SECTION_NOT_ASSISTED"

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
