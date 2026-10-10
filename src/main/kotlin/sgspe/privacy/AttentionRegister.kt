package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Raised
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
import java.time.LocalDate
import java.util.UUID

// la exportación attention-register (api.md §4.13, RF-GRV-005, ASM-101, deliverables.md §10): las quejas, consultas y solicitudes
// leídas como quien exporta (una QCS confidencial que no lee no existe para él; un campo ilegible sale «no disponible»), con el
// nivel de divulgación aplicado antes de escribir y su fila de export_log antes de entregar el archivo. exige READ y EXPORT
// sobre los tres objetos y CREATE sobre export_log antes de leer nada: un registro unificado con un tipo de menos engañaría.
// EXTERNO excluye toda QCS confidencial, también para quien las lee (security.md §9.6). los proyectos siguen ExportProjects; la
// lectura es acotada por sgspe.exports.max-rows por conjunto (400 TOO_MANY_RECORDS sobre filters.project). export_log cuenta
// planteamientos (AttentionCounting)
@Service
class AttentionRegister(
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
    // los filtros ya validados
    private class Filters(
        val periodStart: LocalDate?,
        val periodEnd: LocalDate?,
        val types: List<String>,
        val states: List<String>,
        val channel: String?,
        val category: String?,
        val includeReclassified: Boolean
    )

    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        val grants = Attention.OBJECTS.flatMap { listOf(Actions.READ to it, EXPORT to it) } + (Actions.CREATE to ExportLog.OBJECT)
        permissions.require(user, "Exportar las quejas, consultas y solicitudes", *grants.toTypedArray())
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
        val readable =
            AttentionSheets.REQUIRES
                .filter { readability.canRead(it) }
                .filter { disclosure == Disclosure.INTERNO || it.substringAfter('.') !in AttentionSheets.FREE_TEXT }
                .toSet()
        val read = if (projects?.isEmpty() == true) emptyList() else read(projects, filters)
        // EXTERNO: nunca una confidencial; si quien exporta no lee la marca de un tipo, ninguna de ese tipo (falla cerrada)
        val visible =
            if (disclosure == Disclosure.EXTERNO) {
                read.filter { "${it.type}.${Attention.CONFIDENTIAL}" in readable && !Attention.isConfidential(it.attributes) }
            } else {
                read
            }
        val counted = AttentionCounting.counted(visible)
        val lines = if (filters.includeReclassified) visible else counted
        val confidentialIncluded = !permissions.confidentialHidden(user)
        val sheets = sheets(lines, counted, readable, disclosure, filters.types, confidentialIncluded)
        val export = withContext(Dispatchers.IO) { ExportFile.create("quejas-consultas-solicitudes-${disclosure.name.lowercase()}.xlsx", clock.instant()) }
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, OBJECT_NAME, "XLSX", disclosure, description(projects, raw, disclosure), counted.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    private suspend fun sheets(
        lines: List<Raised>,
        counted: List<Raised>,
        readable: Set<String>,
        disclosure: Disclosure,
        types: List<String>,
        confidentialIncluded: Boolean
    ): List<XlsxSheet> {
        if (disclosure == Disclosure.PUBLICO) {
            return AttentionSheets.aggregates(
                counted,
                readable,
                Attention.OBJECTS.associateWith { catalog.states(it) },
                catalog.enumValues("qcs_channel"),
                catalog.enumValues("qcs_category"),
                confidentialIncluded
            )
        }
        val context = context(lines, readable, disclosure)
        val byType = lines.groupBy { it.type }
        val sheets = types.map { type -> AttentionSheets.records(type, byType[type].orEmpty(), context, disclosure) }
        return if (disclosure == Disclosure.INTERNO) {
            sheets + AttentionSheets.summary(types.associateWith { type -> AttentionCounting.totals(counted.filter { it.type == type }) }, readable)
        } else {
            sheets + AttentionSheets.notes(disclosure, confidentialIncluded = false)
        }
    }

    // las QCS de los tipos pedidos, con los filtros en la consulta; un tipo sin estados pedidos que le apliquen no se lee
    private suspend fun read(
        projects: List<UUID>?,
        filters: Filters
    ): List<Raised> {
        val out = mutableListOf<Raised>()
        for (type in filters.types) {
            val states = if (filters.states.isEmpty()) null else filters.states.filter { it in catalog.states(type) }
            if (states?.isEmpty() == true) continue
            val criteria =
                listOfNotNull(
                    projects?.let { Criteria.anyOf("project", it) },
                    states?.let { Criteria.inState(it) },
                    period(filters)
                )
            val equal = listOfNotNull(filters.channel?.let { "channel" to it }, filters.category?.let { "category" to it }).toMap()
            out += records.all(type, equal, criteria, max = maxRows - out.size, field = TOO_MANY_FIELD).map { Raised(type, it.id, it.state, it.attributes) }
        }
        return out
    }

    // el periodo filtra received_at en fechas de Lima: desde el inicio del primer día hasta el fin del último
    private fun period(filters: Filters): RecordCriterion? {
        if (filters.periodStart == null && filters.periodEnd == null) return null
        val from = (filters.periodStart ?: LocalDate.of(1900, 1, 1)).atStartOfDay(LIMA).toInstant()
        val until = (filters.periodEnd ?: LocalDate.of(9999, 1, 1)).plusDays(1).atStartOfDay(LIMA).toInstant()
        return Criteria.between(Attention.RECEIVED_AT, from, until)
    }

    // los nombres y códigos que muestran las hojas, leídos como quien exporta y solo si los puede leer. en EXTERNO no hace falta
    // ningún nombre de persona ni de grupo de interés: van por código
    private suspend fun context(
        lines: List<Raised>,
        readable: Set<String>,
        disclosure: Disclosure
    ): AttentionContext {
        val internal = disclosure == Disclosure.INTERNO

        fun ids(field: String) = lines.mapNotNull { Values.text(it.attributes[field]) }
        val stakeholders = if (internal) readIds("stakeholder", ids("stakeholder")) else emptyList()
        val organizations =
            if (internal &&
                "organization.name" in readable
            ) {
                names("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) })
            } else {
                emptyMap()
            }
        val stakeholderPeople =
            if (internal &&
                "person.given_names" in readable
            ) {
                personNames(stakeholders.mapNotNull { Values.text(it.attributes["person"]) }, readable)
            } else {
                emptyMap()
            }
        val stakeholderNames =
            stakeholders
                .mapNotNull { s ->
                    val name =
                        Values.text(s.attributes["organization"])?.let { organizations[it] }
                            ?: Values.text(s.attributes["person"])?.let { stakeholderPeople[it] }
                    name?.ifBlank { null }?.let { s.id to it }
                }.toMap()
        val people = if (internal && "person.given_names" in readable) personNames(ids("person"), readable) else emptyMap()
        val places =
            (if ("locality.name" in readable) names("locality", ids("locality")) else emptyMap()) +
                (if ("community.name" in readable) names("community", ids("community")) else emptyMap())
        val topics = if ("topic.name" in readable) names("topic", ids("topic")) else emptyMap()
        val codes =
            Attention.OBJECTS
                .flatMap { source ->
                    val pointing =
                        lines.flatMap { line ->
                            Attention.RECLASSIFIED_FROM
                                .getValue(line.type)
                                .filterValues { it == source }
                                .keys
                                .mapNotNull { Values.text(line.attributes[it]) }
                        }
                    if ("$source.code" in readable) originalCodes(source, pointing, readable, internal).toList() else emptyList()
                }.toMap() + (if ("commitment.code" in readable) names("commitment", ids("created_commitment"), "code") else emptyMap())
        return AttentionContext(readable, stakeholderNames, people, places, topics, codes)
    }

    // los códigos de los originales de una reclasificación. en EXTERNO solo los que pasan su mismo filtro (marca legible y no
    // puesta): el código de un original confidencial no sale ni en «Reclasificada de», y su celda queda vacía
    private suspend fun originalCodes(
        source: String,
        ids: Collection<String>,
        readable: Set<String>,
        internal: Boolean
    ): Map<String, String> =
        readIds(source, ids)
            .mapNotNull { r ->
                val shown = internal || ("$source.${Attention.CONFIDENTIAL}" in readable && !Attention.isConfidential(r.attributes))
                Values.text(r.attributes["code"])?.let { r.id to (if (shown) it else "") }
            }.toMap()

    private suspend fun personNames(
        ids: Collection<String>,
        readable: Set<String>
    ): Map<String, String> {
        val surnames = "person.surnames" in readable
        return readIds("person", ids).associate { p ->
            p.id to listOfNotNull(Values.text(p.attributes["given_names"]), if (surnames) Values.text(p.attributes["surnames"]) else null).joinToString(" ")
        }
    }

    private suspend fun names(
        objectName: String,
        ids: Collection<String>,
        field: String = "name"
    ): Map<String, String> = readIds(objectName, ids).mapNotNull { r -> Values.text(r.attributes[field])?.let { r.id to it } }.toMap()

    // por ids, en tramos de CHUNK y con el tope del conjunto; sin READ sobre el objeto, nada (la celda sale «no disponible»)
    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        if (!Readability(metadata).canRead(objectName)) return emptyList()
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += records.all(objectName, ids = chunk, max = maxRows - out.size, field = TOO_MANY_FIELD)
        }
        return out
    }

    // ---- el cuerpo ----

    private fun filters(raw: Map<String, Any?>): Filters {
        val types = texts(raw["type"], "filters.type")
        if (types.any { it !in Attention.OBJECTS }) {
            throw bad("INVALID_FILTER", "El filtro filters.type debe ser uno de ${Attention.OBJECTS.joinToString(", ")}", "filters.type")
        }
        val known = Attention.OBJECTS.flatMap { catalog.states(it) }.distinct()
        val states = texts(raw["state"], "filters.state")
        if (states.any { it !in known }) throw bad("INVALID_FILTER", "El filtro filters.state debe ser un estado de WF-E1, WF-E2 o WF-E3", "filters.state")
        val start = date(raw["periodStart"], "filters.periodStart")
        val end = date(raw["periodEnd"], "filters.periodEnd")
        if (start != null && end != null &&
            start.isAfter(end)
        ) {
            throw bad("INVALID_FILTER", "periodStart no puede ser posterior a periodEnd", "filters.periodStart")
        }
        val include = raw["includeReclassified"]
        if (include != null &&
            include !is Boolean
        ) {
            throw bad("INVALID_FILTER", "El filtro filters.includeReclassified es verdadero o falso", "filters.includeReclassified")
        }
        return Filters(
            start,
            end,
            types.distinct().ifEmpty { Attention.OBJECTS },
            states.distinct(),
            enumValue(raw["channel"], "qcs_channel", "filters.channel"),
            enumValue(raw["category"], "qcs_category", "filters.category"),
            include == true
        )
    }

    private fun texts(
        raw: Any?,
        field: String
    ): List<String> {
        val values =
            when (raw) {
                null -> return emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values.map { it as? String ?: throw bad("INVALID_FILTER", "El filtro $field es una lista de textos", field) }
    }

    private fun date(
        raw: Any?,
        field: String
    ): LocalDate? {
        if (raw == null) return null
        return runCatching { LocalDate.parse(raw.toString()) }.getOrNull() ?: throw bad("INVALID_FILTER", "El filtro $field es una fecha AAAA-MM-DD", field)
    }

    private fun enumValue(
        raw: Any?,
        enum: String,
        field: String
    ): String? {
        if (raw == null) return null
        val value = raw as? String
        if (value == null || value !in catalog.enumValues(enum)) throw bad("INVALID_FILTER", "El filtro $field debe ser un valor del catálogo $enum", field)
        return value
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

        // export_log.object_name de esta exportación: los tres objetos
        val OBJECT_NAME = Attention.OBJECTS.joinToString(",")
        private const val TOO_MANY_FIELD = "filters.project"
        val FILTERS = setOf("project", "periodStart", "periodEnd", "type", "state", "channel", "category", "includeReclassified")
        private const val CHUNK = 500
    }
}
