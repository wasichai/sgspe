package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// la exportación risk-register (api.md §4.13, ASM-101, deliverables.md §10): la matriz de riesgos con sus revisiones, medidas e
// impactos, leída como quien exporta (un campo ilegible sale «no disponible»), con el nivel de divulgación aplicado antes de
// escribir y su fila de export_log antes de entregar el archivo. exige READ y EXPORT sobre social_risk y CREATE sobre export_log
// antes de leer nada; los proyectos siguen ExportProjects. la lectura es acotada: sgspe.exports.max-rows por conjunto y, para
// los grupos de interés de cada riesgo (M2M), MAX_RELATED riesgos y MAX_PER_LINK vínculos por riesgo; pasarse es 400
// TOO_MANY_RECORDS sobre filters.project. PUBLICO cuenta los riesgos activos
@Service
class RiskRegister(
    private val records: Records,
    private val permissions: Permissions,
    private val callers: Callers,
    private val metadata: MetadataService,
    private val log: ExportLog,
    private val clock: Clock,
    private val catalog: ModelCatalog,
    private val scope: ExportProjects,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    private class Filters(
        val states: List<String>,
        val categories: List<String>,
        val levels: List<String>,
        val includeClosed: Boolean
    )

    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        permissions.require(
            user,
            "Exportar la matriz de riesgos",
            Actions.READ to RiskSheets.RISK,
            EXPORT to RiskSheets.RISK,
            Actions.CREATE to ExportLog.OBJECT
        )
        StrictBody.requireOnly(body, setOf("filters", "disclosureLevel", "checklistConfirmed"))
        val raw = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(raw, FILTERS, "filters.")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        val filters = filters(raw)
        val requested = uuids(raw["project"], "filters.project").distinct()
        val projects = scope.resolve(user, requested)
        val readability = Readability(metadata)
        val readable = RiskSheets.REQUIRES.filter { readability.canRead(it) && RiskSheets.allowed(it, disclosure) }.toSet()
        val risks = if (projects?.isEmpty() == true) emptyList() else risks(projects, filters, disclosure)
        val sheets = sheets(risks, readable, readability, disclosure)
        val export = withContext(Dispatchers.IO) { ExportFile.create("matriz-de-riesgos-${disclosure.name.lowercase()}.xlsx", clock.instant()) }
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, RiskSheets.RISK, "XLSX", disclosure, description(projects, raw, disclosure), risks.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    // los riesgos de los proyectos con los filtros en la consulta; sin includeClosed (o en PUBLICO, que cuenta los activos), sin los
    // cerrados
    private suspend fun risks(
        projects: List<UUID>?,
        filters: Filters,
        disclosure: Disclosure
    ): List<RecordResponse> {
        val all = catalog.states(RiskSheets.RISK)
        val wanted = filters.states.ifEmpty { all }.filter { it != RiskSheets.CLOSED || (filters.includeClosed && disclosure != Disclosure.PUBLICO) }
        if (wanted.isEmpty()) return emptyList()
        val criteria =
            listOfNotNull(
                projects?.let { Criteria.anyOf("project", it) },
                Criteria.inState(wanted),
                filters.categories.takeIf { it.isNotEmpty() }?.let { Criteria.textIn("category", it) },
                filters.levels.takeIf { it.isNotEmpty() }?.let { Criteria.textIn("risk_level", it) }
            )
        return records.all(RiskSheets.RISK, criteria = criteria, max = maxRows, field = TOO_MANY_FIELD)
    }

    private suspend fun sheets(
        risks: List<RecordResponse>,
        readable: Set<String>,
        readability: Readability,
        disclosure: Disclosure
    ): List<XlsxSheet> {
        if (disclosure == Disclosure.PUBLICO) {
            return RiskSheets.aggregates(
                risks.map { it.attributes },
                readable,
                catalog.enumOf(RiskSheets.RISK, "category"),
                catalog.enumOf(RiskSheets.RISK, "treatment_decision")
            )
        }
        if (risks.size > MAX_RELATED) throw Records.tooMany(RiskSheets.RISK, MAX_RELATED, TOO_MANY_FIELD)
        val ids = risks.map { it.id }

        // un hijo que quien exporta no puede leer queda vacío (sus celdas salen «no disponible»)
        suspend fun children(objectName: String): List<RecordResponse>? = if (readability.canRead(objectName)) readIn(objectName, ids) else null
        val assessments = children(RiskSheets.ASSESSMENT)
        val measures = children(RiskSheets.MEASURE)
        val impacts = children(RiskSheets.IMPACT)
        val stakeholders =
            if (readability.canRead("stakeholder")) risks.associate { it.id to linked(it.id) } else null
        val names = if (disclosure == Disclosure.INTERNO) stakeholderNames(stakeholders?.values?.flatten().orEmpty(), readable) else emptyMap()
        val today = LocalDate.now(clock.withZone(LIMA))
        val lines =
            risks.map { r ->
                val last =
                    assessments
                        ?.filter { Values.text(it.attributes[RiskSheets.RISK]) == r.id && Values.bool(it.attributes["residual"]) != true }
                        ?.maxWithOrNull(compareBy({ Values.text(it.attributes["assessment_date"]) }, { it.createdAt }))
                val mine = measures?.filter { Values.text(it.attributes[RiskSheets.RISK]) == r.id }
                RiskLine(
                    r.id,
                    r.state,
                    r.attributes,
                    stakeholders?.get(r.id)?.map { it.id to names[it.id] },
                    last?.let { Values.text(it.attributes["assessment_date"]) },
                    last?.let { Values.text(it.attributes["trend"]) },
                    mine?.let { list ->
                        MeasureCount(
                            list.size,
                            list.count { Values.bool(it.attributes["completed"]) == true },
                            list.count { m ->
                                Values.bool(m.attributes["completed"]) != true &&
                                    Values.text(m.attributes["planned_date"])?.let { LocalDate.parse(it).isBefore(today) } == true
                            }
                        )
                    }
                )
            }
        val context = RiskContext(readable, linkedNames(risks, readable), risks.associate { it.id to (Values.text(it.attributes["code"]) ?: "") })
        val byDate =
            compareBy<RecordResponse>({ Values.text(it.attributes[RiskSheets.RISK]) }, { Values.text(it.attributes["assessment_date"]) }, { it.createdAt })
        val sheets =
            mutableListOf(
                RiskSheets.matrix(lines, context, disclosure),
                RiskSheets.assessments(assessments.orEmpty().sortedWith(byDate).map { it.attributes }, context, disclosure)
            )
        if (disclosure == Disclosure.INTERNO) sheets += RiskSheets.measures(measures.orEmpty().map { it.attributes }, context)
        sheets += RiskSheets.impacts(impacts.orEmpty().map { it.attributes }, context, disclosure)
        return sheets
    }

    // el nombre de cada vínculo de los riesgos que nombra la matriz, si quien exporta lo lee
    private suspend fun linkedNames(
        risks: List<RecordResponse>,
        readable: Set<String>
    ): Map<String, String> =
        RiskSheets.LINKED
            .flatMap { (field, target) ->
                val (objectName, nameField) = target
                if ("$objectName.$nameField" !in readable) return@flatMap emptyList()
                readIds(objectName, risks.mapNotNull { Values.text(it.attributes[field]) }).mapNotNull { r ->
                    Values.text(r.attributes[nameField])?.let { r.id to it }
                }
            }.toMap()

    // el nombre de cada grupo de interés en INTERNO: la organización o la persona, si quien exporta lee esos nombres
    private suspend fun stakeholderNames(
        stakeholders: List<RecordResponse>,
        readable: Set<String>
    ): Map<String, String> {
        val organizations =
            if ("organization.name" in readable) {
                readIds("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) })
                    .mapNotNull { o -> Values.text(o.attributes["name"])?.let { o.id to it } }
                    .toMap()
            } else {
                emptyMap()
            }
        val surnames = "person.surnames" in readable
        val people =
            if ("person.given_names" in readable) {
                readIds("person", stakeholders.mapNotNull { Values.text(it.attributes["person"]) }).associate { p ->
                    p.id to
                        listOfNotNull(Values.text(p.attributes["given_names"]), if (surnames) Values.text(p.attributes["surnames"]) else null).joinToString(" ")
                }
            } else {
                emptyMap()
            }
        return stakeholders
            .mapNotNull { s ->
                val name = Values.text(s.attributes["organization"])?.let { organizations[it] } ?: Values.text(s.attributes["person"])?.let { people[it] }
                name?.ifBlank { null }?.let { s.id to it }
            }.toMap()
    }

    // ---- lectura acotada ----

    private suspend fun linked(risk: String): List<RecordResponse> =
        records.related(RiskSheets.RISK, UUID.fromString(risk), STAKEHOLDERS, max = MAX_PER_LINK, field = TOO_MANY_FIELD)

    private suspend fun readIn(
        objectName: String,
        risks: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in risks.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += records.all(objectName, criteria = listOf(Criteria.anyOf(RiskSheets.RISK, chunk)), max = maxRows - out.size, field = TOO_MANY_FIELD)
        }
        return out
    }

    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += records.all(objectName, ids = chunk, max = maxRows - out.size, field = TOO_MANY_FIELD)
        }
        return out
    }

    // ---- el cuerpo ----

    private fun filters(raw: Map<String, Any?>): Filters {
        val states = texts(raw["state"], "filters.state", catalog.states(RiskSheets.RISK))
        val categories = texts(raw["category"], "filters.category", catalog.enumOf(RiskSheets.RISK, "category"))
        val levels = texts(raw["riskLevel"], "filters.riskLevel", catalog.enumOf(RiskSheets.RISK, "risk_level"))
        val include = raw["includeClosed"]
        if (include != null && include !is Boolean) throw bad("INVALID_FILTER", "El filtro filters.includeClosed es verdadero o falso", "filters.includeClosed")
        return Filters(states, categories, levels, include == true)
    }

    // una lista de textos (o uno solo) del catálogo `known`
    private fun texts(
        raw: Any?,
        field: String,
        known: List<String>
    ): List<String> {
        val values =
            when (raw) {
                null -> return emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values
            .map { value ->
                val text = value as? String
                if (text == null || text !in known) throw bad("INVALID_FILTER", "El filtro $field debe ser uno de ${known.joinToString(", ")}", field)
                text
            }.distinct()
    }

    private fun description(
        projects: List<UUID>?,
        filters: Map<String, Any?>,
        disclosure: Disclosure
    ): String =
        buildString {
            append("filtros: proyectos=")
            append(projects?.joinToString(",") ?: "todos")
            filters.filterKeys { it != "project" }.toSortedMap().forEach { (key, value) -> append("; ").append(key).append('=').append(value) }
            if (disclosure == Disclosure.EXTERNO) append("; lista de verificación de EXTERNO confirmada")
        }

    private fun uuids(
        raw: Any?,
        field: String
    ): List<UUID> {
        val values =
            when (raw) {
                null -> emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values.map { value -> Records.uuid(value) ?: throw bad("INVALID_FILTER", "El filtro $field debe ser un UUID o una lista de UUID", field) }
    }

    private fun bad(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.BAD_REQUEST).toCodedException()

    companion object {
        const val EXPORT = "EXPORT"
        const val STAKEHOLDERS = "social_risk_stakeholder"
        private const val TOO_MANY_FIELD = "filters.project"
        val FILTERS = setOf("project", "state", "category", "riskLevel", "includeClosed")

        // los riesgos cuyos grupos de interés se leen uno por uno, y los vínculos por riesgo
        const val MAX_RELATED = 2_000
        private const val CHUNK = 500
        private const val MAX_PER_LINK = 200
    }
}
