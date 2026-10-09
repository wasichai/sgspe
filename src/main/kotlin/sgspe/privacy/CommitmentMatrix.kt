package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.util.UUID

// la exportación commitment-matrix (api.md §4.13, RF-COM-001, RQ-REP-022, deliverables.md §10): la matriz de compromisos con los
// campos de DOC-01 §9.3.1.2, leída como quien exporta (un campo ilegible sale «no disponible»), con el nivel de divulgación
// aplicado antes de escribir y su fila de export_log antes de entregar el archivo. exige READ y EXPORT sobre commitment y CREATE
// sobre export_log antes de leer nada; los proyectos siguen ExportProjects. la lectura es acotada: sgspe.exports.max-rows por
// conjunto y, para las lecturas por registro (los solicitantes y las evidencias de cada entrega), MAX_RELATED registros; pasarse
// es 400 TOO_MANY_RECORDS sobre filters.project. nunca sale el documento de identidad de los solicitantes (CON-014)
@Service
class CommitmentMatrix(
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
    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        permissions.require(user, "Exportar la matriz de compromisos", Actions.READ to COMMITMENT, EXPORT to COMMITMENT, Actions.CREATE to ExportLog.OBJECT)
        StrictBody.requireOnly(body, setOf("filters", "disclosureLevel", "checklistConfirmed"))
        val filters = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(filters, FILTERS, "filters.")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        val requested = uuids(filters["project"], "filters.project").distinct()
        val campaigns = uuids(filters["explorationCampaign"], "filters.explorationCampaign").distinct()
        // un filtro que no se aplica ensancharía la exportación en silencio y export_log describiría otro archivo
        if (campaigns.size > 1) {
            throw bad("INVALID_FILTER", "El filtro filters.explorationCampaign admite una sola campaña", "filters.explorationCampaign")
        }
        val campaign = campaigns.singleOrNull()
        val states = states(filters["state"])
        val projects = scope.resolve(user, requested)
        val commitments = if (projects?.isEmpty() == true) emptyList() else commitments(projects, campaign, states)
        val sheets = sheets(commitments, disclosure)
        val export = withContext(Dispatchers.IO) { ExportFile.create("matriz-de-compromisos-${disclosure.name.lowercase()}.xlsx", clock.instant()) }
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, COMMITMENT, "XLSX", disclosure, description(projects, filters, disclosure), commitments.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    private suspend fun commitments(
        projects: List<UUID>?,
        campaign: UUID?,
        states: List<String>
    ): List<RecordResponse> {
        val criteria = listOfNotNull(projects?.let { Criteria.anyOf("project", it) }, states.takeIf { it.isNotEmpty() }?.let { Criteria.inState(it) })
        val equal = campaign?.let { mapOf("exploration_campaign" to it.toString()) }.orEmpty()
        return read(COMMITMENT, equal, criteria)
    }

    private suspend fun sheets(
        commitments: List<RecordResponse>,
        disclosure: Disclosure
    ): List<XlsxSheet> {
        val readability = Readability(metadata)
        val readable =
            MatrixSheets.REQUIRES
                .filter { readability.canRead(it) }
                .filter { disclosure == Disclosure.INTERNO || it !in MatrixSheets.RESTRICTED }
                .toSet()
        if (disclosure == Disclosure.PUBLICO) {
            val lines = commitments.map { MatrixLine(it.id, it.state, it.attributes, emptyList(), emptyList(), emptyList(), 0) }
            return MatrixSheets.aggregates(
                lines,
                readable,
                catalog.states(COMMITMENT),
                catalog.enumOf(COMMITMENT, "commitment_theme"),
                catalog.enumOf(COMMITMENT, "resource_type")
            )
        }
        if (commitments.size > MAX_RELATED) throw Records.tooMany(COMMITMENT, MAX_RELATED, TOO_MANY_FIELD)
        val ids = commitments.map { it.id }

        // un hijo que quien exporta no puede leer queda vacío, como un campo ilegible: la matriz sale igual
        // (null = no se lee: la celda sale «no disponible», no vacía ni en cero)
        suspend fun children(objectName: String): List<RecordResponse>? = if (readability.canRead(objectName)) readIn(objectName, ids) else null
        val milestones = children("commitment_milestone")?.groupBy { Values.text(it.attributes["commitment"]) }
        val reschedules = children("commitment_reschedule")?.groupBy { Values.text(it.attributes["commitment"]) }
        val actions = children("commitment_action")?.groupingBy { Values.text(it.attributes["commitment"]) }?.eachCount()
        val deliveries = children(MatrixSheets.DELIVERY).orEmpty()
        if (deliveries.size > MAX_RELATED) throw Records.tooMany(MatrixSheets.DELIVERY, MAX_RELATED, TOO_MANY_FIELD)
        // Records no ofrece un filtro por vínculo M2M: una lectura por compromiso, cada una con su tope (MAX_PER_LINK)
        val requesters =
            if (readability.canRead("stakeholder")) {
                commitments.associate { it.id to linked(COMMITMENT, it.id, "commitment_stakeholder") }
            } else {
                null
            }
        val names = if (disclosure == Disclosure.INTERNO) requesterNames(requesters?.values?.flatten().orEmpty(), readable) else emptyMap()
        val lines =
            commitments.map { c ->
                MatrixLine(
                    c.id,
                    c.state,
                    c.attributes,
                    requesters
                        ?.get(c.id)
                        .orEmpty()
                        .map { it.id to names[it.id] }
                        .takeIf { requesters != null },
                    milestones?.get(c.id).orEmpty().map {
                        "${Values.text(
                            it.attributes["description"]
                        ).orEmpty()} (${Values.text(it.attributes["due_date"]).orEmpty()})"
                    },
                    reschedules?.get(c.id).orEmpty().map {
                        "${Values.text(it.attributes["previous_due_date"]).orEmpty()} → ${Values.text(it.attributes["new_due_date"]).orEmpty()}"
                    },
                    actions?.let { it[c.id] ?: 0 }
                )
            }
        val codeReadable = "commitment.code" in readable
        val codes = commitments.associate { it.id to (Values.text(it.attributes["code"]) ?: NO_CODE) }
        val localities =
            if (disclosure == Disclosure.EXTERNO && "locality.name" in readable) {
                readIds("locality", commitments.mapNotNull { Values.text(it.attributes["locality"]) }).associate { it.id to Values.text(it.attributes["name"]) }
            } else {
                emptyMap()
            }
        val evidenceReadable = "evidence.evidence_type" in readable
        val matrixDeliveries =
            deliveries.map { d ->
                val types =
                    if (evidenceReadable) {
                        linked(MatrixSheets.DELIVERY, d.id, "evidence_delivery").mapNotNull { Values.text(it.attributes["evidence_type"]) }
                    } else {
                        null
                    }
                val commitmentId = Values.text(d.attributes["commitment"])
                MatrixDelivery(
                    if (codeReadable) codes[commitmentId] ?: NO_CODE else NOT_AVAILABLE,
                    d.attributes,
                    types,
                    commitments.firstOrNull { it.id == commitmentId }?.let { localities[Values.text(it.attributes["locality"])] }
                )
            }
        val projects = if ("project.code" in readable) readIds("project", commitments.mapNotNull { Values.text(it.attributes["project"]) }) else emptyList()
        return listOf(
            MatrixSheets.matrix(lines, projects.associate { it.id to Values.text(it.attributes["code"]).orEmpty() }, readable, disclosure),
            MatrixSheets.deliveries(matrixDeliveries, readable, disclosure)
        )
    }

    // el nombre de cada solicitante en INTERNO: la organización o la persona si quien exporta lee esos nombres; si no, null y
    // la matriz lo cita por su código GI-
    private suspend fun requesterNames(
        stakeholders: List<RecordResponse>,
        readable: Set<String>
    ): Map<String, String> {
        val organizations =
            if ("organization.name" in readable) {
                readIds("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) }).associate {
                    it.id to
                        Values.text(it.attributes["name"])
                }
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
                val name =
                    Values.text(s.attributes["organization"])?.let { organizations[it] } ?: Values.text(s.attributes["person"])?.let { people[it] }
                name?.ifBlank { null }?.let { s.id to it }
            }.toMap()
    }

    // ---- lectura acotada ----

    // los registros vinculados a uno (M2M), con su propio tope: pasarse es 400 TOO_MANY_RECORDS
    private suspend fun linked(
        objectName: String,
        id: String,
        relationship: String
    ): List<RecordResponse> =
        records.related(objectName, UUID.fromString(id), relationship).also {
            if (it.size > MAX_PER_LINK) throw Records.tooMany(relationship, MAX_PER_LINK, TOO_MANY_FIELD)
        }

    private suspend fun read(
        objectName: String,
        equal: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null,
        already: Int = 0
    ): List<RecordResponse> = records.all(objectName, equal, criteria, ids, max = maxRows - already, field = TOO_MANY_FIELD)

    // los hijos de los compromisos (por su campo commitment), en tramos de CHUNK; el tope cuenta el conjunto entero
    private suspend fun readIn(
        objectName: String,
        commitments: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in commitments.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += read(objectName, criteria = listOf(Criteria.anyOf(COMMITMENT, chunk)), already = out.size)
        }
        return out
    }

    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) out += read(objectName, ids = chunk, already = out.size)
        return out
    }

    // ---- el cuerpo ----

    private fun states(raw: Any?): List<String> {
        val values =
            when (raw) {
                null -> return emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        val known = catalog.states(COMMITMENT)
        return values
            .map { value ->
                val state = value as? String
                if (state == null ||
                    state !in known
                ) {
                    throw bad("INVALID_FILTER", "El filtro filters.state debe ser uno de ${known.joinToString(", ")}", "filters.state")
                }
                state
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
        const val COMMITMENT = "commitment"
        const val EXPORT = "EXPORT"
        private const val TOO_MANY_FIELD = "filters.project"
        val FILTERS = setOf("project", "explorationCampaign", "state")

        // los compromisos y las entregas cuyos solicitantes y evidencias se leen uno por uno
        const val MAX_RELATED = 2_000
        private const val CHUNK = 500
        private const val MAX_PER_LINK = 200
        private const val NO_CODE = "(sin código)"
    }
}
