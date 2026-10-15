package sgspe.deliverable

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// lo que T1 lee una vez por ensamblado (especificación de F8 §3.8): el proyecto, el estudio, el AIDS del entregable (aprobado:
// si ya no lo está, FALLIDO con DELIVERABLE_PARAMETERS_MISSING sobre area_of_influence) y el AIIS aprobado más reciente del
// proyecto, el periodo y el corte, la organización autora, las personas citadas y los textos de registro de cada sección para
// el aviso de datos personales. lo que falta se escribe «Sin dato», nunca tumba el ensamblado (Review Focus 5)
class DiagnosisScene private constructor(
    val source: ReportSource,
    val input: AssemblyInput,
    val project: SourceRow,
    val study: SourceRow?,
    val aids: SourceRow,
    val aiis: SourceRow?,
    val start: LocalDate,
    val end: LocalDate,
    val cutoff: LocalDate,
    val author: String?
) {
    val projectId: UUID = UUID.fromString(project.id)
    val people = ReportPeople(source, projectId)
    val recordTexts = linkedMapOf<String, MutableList<String>>()
    val byProject: Map<String, String> = mapOf(Deliverables.PROJECT to project.id)
    private val memberCache = mutableMapOf<String, List<SourceRow>>()
    private var stakeholderCache: List<SourceRow>? = null

    fun keep(
        section: String,
        vararg values: String?
    ) {
        recordTexts.getOrPut(section) { mutableListOf() } += values.filterNotNull()
    }

    fun day(raw: String?): LocalDate? =
        raw?.let { text ->
            runCatching { LocalDate.parse(text.take(10)) }.getOrNull() ?: runCatching { OffsetDateTime.parse(text).toLocalDate() }.getOrNull()
        }

    fun inPeriod(raw: String?): Boolean = day(raw)?.let { !it.isBefore(start) && !it.isAfter(end) } ?: false

    fun upToCutoff(raw: String?): Boolean = day(raw)?.let { !it.isAfter(cutoff) } ?: false

    suspend fun members(area: SourceRow?): List<SourceRow> =
        area?.let { memberCache.getOrPut(it.id) { source.all("area_of_influence_member", mapOf(AREA to it.id)) } }.orEmpty()

    // las localidades miembros de un área, sin repetir y por nombre
    suspend fun localities(area: SourceRow?): List<SourceRow> =
        members(area)
            .mapNotNull { it.ref("locality") }
            .distinct()
            .mapNotNull { source.one("locality", it) }
            .sortedBy { it.text("name") }

    // los ámbitos del capítulo 5 con sus localidades (AIDS, AIIS), sin los vacíos
    suspend fun scopes(): List<Pair<String, List<SourceRow>>> =
        listOf(AIDS_TITLE to localities(aids), AIIS_TITLE to localities(aiis)).filter { it.second.isNotEmpty() }

    // el perfil más reciente de una localidad para el estudio del entregable (o cualquiera si no hay estudio)
    suspend fun profile(locality: SourceRow): SourceRow? {
        val filters = mapOf("locality" to locality.id) + (study?.let { mapOf("study" to it.id) } ?: emptyMap())
        return source.all("locality_profile", filters).maxByOrNull { it.text("data_date").orEmpty() }
    }

    suspend fun stakeholders(): List<SourceRow> = stakeholderCache ?: source.all("stakeholder", byProject).also { stakeholderCache = it }

    // la última evaluación de un grupo de interés hasta el corte
    suspend fun latestAssessment(stakeholder: SourceRow): SourceRow? =
        source
            .all("stakeholder_assessment", mapOf("stakeholder" to stakeholder.id))
            .filter { upToCutoff(it.text("assessment_date")) }
            .maxByOrNull { it.text("assessment_date").orEmpty() }

    companion object {
        const val SIN_DATO = "Sin dato"
        const val AREA = "area_of_influence"
        const val APPROVED = "approved"
        const val AIDS = "AIDS"
        const val AIIS = "AIIS"
        const val AIDS_TITLE = "Área de influencia directa social (AIDS)"
        const val AIIS_TITLE = "Área de influencia indirecta social (AIIS)"

        // el valor de un campo o «Sin dato»
        fun or(
            row: SourceRow?,
            field: String
        ): String = row?.text(field) ?: SIN_DATO

        suspend fun load(
            input: AssemblyInput,
            source: ReportSource
        ): DiagnosisScene {
            val d = input.deliverable
            val project =
                d.ref(Deliverables.PROJECT)?.let { source.one(Deliverables.PROJECT, it) }
                    ?: throw DraftFailure(DeliverableDrafts.PARAMETERS_MISSING, Deliverables.PROJECT)
            val aids =
                d.ref(AREA)?.let { source.one(AREA, it) }?.takeIf { it.state == APPROVED && it.text("area_type") == AIDS }
                    ?: throw DraftFailure(DeliverableDrafts.PARAMETERS_MISSING, AREA)
            val aiis =
                source
                    .all(AREA, mapOf(Deliverables.PROJECT to project.id, "area_type" to AIIS))
                    .filter { it.state == APPROVED }
                    .maxByOrNull { (it.attributes["version_number"] as? Number)?.toInt() ?: 0 }
            val study = d.ref("study")?.let { source.one("study", it) }
            val end = LocalDate.parse(d.text(Deliverables.PERIOD_END)!!.take(10))
            return DiagnosisScene(
                source = source,
                input = input,
                project = project,
                study = study,
                aids = aids,
                aiis = aiis,
                start = LocalDate.parse(d.text(Deliverables.PERIOD_START)!!.take(10)),
                end = end,
                cutoff = d.text(Deliverables.CUTOFF)?.let { LocalDate.parse(it.take(10)) } ?: end,
                author = d.ref("prepared_by_organization")?.let { source.one("organization", it)?.text("name") }
            )
        }
    }
}
