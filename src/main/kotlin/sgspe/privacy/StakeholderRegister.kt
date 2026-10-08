package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Positions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Validity
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// un archivo listo para descargar, en una carpeta propia de permisos 0700 (el XLSX lleva datos personales). stream() lo envía
// y lo borra con su carpeta cuando el flujo termina, falla o se cancela (Flux.using); discard() lo borra sin enviarlo
class ExportFile(
    val file: File,
    val filename: String
) {
    fun stream(): Flux<DataBuffer> =
        Flux.using(
            { file },
            { DataBufferUtils.read(FileSystemResource(it), DefaultDataBufferFactory.sharedInstance, BUFFER_BYTES) },
            { discard() }
        )

    fun discard() {
        file.delete()
        file.parentFile?.delete()
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
        private val PRIVATE_DIRECTORY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        private val PRIVATE_FILE = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

        // un archivo vacío dentro de una carpeta nueva solo de quien corre el servidor
        fun create(filename: String): ExportFile {
            val directory = Files.createTempDirectory("sgspe-export-", PRIVATE_DIRECTORY)
            return ExportFile(Files.createFile(directory.resolve("registro.xlsx"), PRIVATE_FILE).toFile(), filename)
        }
    }
}

// la exportación stakeholder-register (api.md §4.13, RQ-REP-022, deliverables.md §10): el registro de grupos de interés con
// su evaluación vigente, leído con Records como quien exporta (solo los registros y campos que puede leer; un campo
// ilegible sale «no disponible»), con el nivel de divulgación aplicado antes de escribir. exige READ y EXPORT sobre
// stakeholder y CREATE sobre export_log antes de leer nada. EXTERNO exige checklistConfirmed; con un solo proyecto,
// export_log lleva ese proyecto y quien no es ADMIN debe estar asignado a él. la fila de export_log se escribe antes de
// entregar el archivo: no hay salida sin registro (RQ-AUD-011), y si algo falla antes, no hay archivo ni fila.
// la lectura es acotada (sgspe.exports.max-rows): pasarse es un 400 TOO_MANY_RECORDS. los errores llevan su código en
// errors[0].message (P15)
@Service
class StakeholderRegister(
    private val records: Records,
    private val permissions: Permissions,
    private val callers: Callers,
    private val metadata: MetadataService,
    private val log: ExportLog,
    private val clock: Clock,
    private val catalog: ModelCatalog,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        permissions.require(
            user,
            "Exportar el registro de grupos de interés",
            Actions.READ to STAKEHOLDER,
            EXPORT to STAKEHOLDER,
            Actions.CREATE to ExportLog.OBJECT
        )
        StrictBody.requireOnly(body, setOf("filters", "disclosureLevel", "checklistConfirmed"))
        val filters = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(filters, FILTERS, "filters.")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        // sin repetidos: [p, p] es un solo proyecto, con su asignación y su fila de export_log
        val requested = uuids(filters["project"], "filters.project").distinct()
        val locality = uuids(filters["locality"], "filters.locality").distinct()
        val equality = equality(filters)
        val includeRetired = includeRetired(filters)
        // «mis proyectos» si no se pide ninguno (la forma que fija la Fase 2.3): las asignaciones vigentes de quien exporta;
        // el ADMIN, todos (null). con un solo proyecto pedido, quien no es ADMIN debe estar asignado a él
        val projects: List<UUID>? =
            when {
                requested.isNotEmpty() -> requested.also { requireAssigned(user, it) }
                user.isAdmin -> null
                else -> assignedProjects(user)
            }
        val lines = if (projects?.isEmpty() == true) emptyList() else stakeholders(projects, locality, equality, includeRetired)
        val sheets = sheets(lines, disclosure)
        val export = ExportFile.create("registro-grupos-de-interes-${disclosure.name.lowercase()}.xlsx")
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, STAKEHOLDER, "XLSX", disclosure, description(projects, filters, disclosure), lines.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    // ---- quién y qué proyectos ----

    // los proyectos de las asignaciones vigentes de quien exporta, hoy en America/Lima (security.md §9.2). privacy solo
    // depende de common (architecture.md §5.3): lee project_assignment con Records y aplica la vigencia de common. sin READ
    // sobre project_assignment no tiene asignaciones que leer
    private suspend fun assignedProjects(user: AuthenticatedUser): List<UUID> {
        val today = LocalDate.now(clock.withZone(LIMA))
        val rows =
            try {
                read("project_assignment", mapOf("member_user_id" to user.userId.toString()))
            } catch (_: ForbiddenException) {
                return emptyList()
            }
        return rows
            .filter { row ->
                Records.uuid(row.attributes["member_user_id"]) == user.userId &&
                    Validity.inForce(
                        Values.bool(row.attributes["active"]) == true,
                        Values.text(row.attributes["start_date"])?.let(LocalDate::parse),
                        Values.text(row.attributes["end_date"])?.let(LocalDate::parse),
                        today
                    )
            }.mapNotNull { Records.uuid(it.attributes["project"]) }
            .distinct()
    }

    // alcance opción A (security.md §9.2): exportar un solo proyecto exige una asignación vigente a él, salvo el ADMIN. se
    // comprueba antes de leer nada; ProjectScopeGuard lo vuelve a exigir al escribir la fila de export_log
    private suspend fun requireAssigned(
        user: AuthenticatedUser,
        requested: List<UUID>
    ) {
        val single = requested.singleOrNull() ?: return
        if (user.isAdmin || single in assignedProjects(user)) return
        throw Finding(
            "NOT_ASSIGNED",
            "Exportar un proyecto exige una asignación vigente a él",
            "filters.project",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
    }

    // ---- lectura ----

    private suspend fun stakeholders(
        projects: List<UUID>?,
        locality: List<UUID>,
        equality: Map<String, String>,
        includeRetired: Boolean
    ): List<StakeholderLine> {
        val criteria =
            listOfNotNull(
                projects?.let { Criteria.anyOf("project", it) },
                locality.takeIf { it.isNotEmpty() }?.let { Criteria.anyOf("locality", it) }
            )
        return read(STAKEHOLDER, equality, criteria)
            .filter { includeRetired || it.state != RETIRED }
            .map { StakeholderLine(it.id, it.state, it.attributes) }
    }

    // los filtros de catálogo: el valor debe ser uno del catálogo del campo; si no, 400 INVALID_FILTER sobre el filtro (una
    // exportación vacía en silencio dejaría una fila de export_log de cero filas sin que nadie sepa por qué)
    private fun equality(filters: Map<String, Any?>): Map<String, String> =
        EQUALITY
            .mapNotNull { (name, field) ->
                val raw = filters[name] ?: return@mapNotNull null
                val allowed = catalog.enumOf(STAKEHOLDER, field)
                if (raw !is String || (allowed.isNotEmpty() && raw !in allowed)) {
                    throw bad("INVALID_FILTER", "El filtro filters.$name debe ser uno de ${allowed.joinToString(", ")}", "filters.$name")
                }
                field to raw
            }.toMap()

    private fun includeRetired(filters: Map<String, Any?>): Boolean =
        when (val raw = filters["includeRetired"]) {
            null -> false
            is Boolean -> raw
            else -> throw bad("INVALID_FILTER", "El filtro filters.includeRetired debe ser true o false", "filters.includeRetired")
        }

    // los registros que cumplen los filtros, por cursor y ordenados por id, como quien exporta, hasta el tope: pasarse es un
    // 400 TOO_MANY_RECORDS sobre filters.project (Records.all). `already` son las filas ya leídas del mismo conjunto
    private suspend fun read(
        objectName: String,
        equal: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null,
        already: Int = 0
    ): List<RecordResponse> = records.all(objectName, equal, criteria, ids, max = maxRows - already, field = TOO_MANY_FIELD)

    // los registros de `objectName` con esos ids, en tramos de CHUNK; el tope cuenta el conjunto entero
    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) out += read(objectName, ids = chunk, already = out.size)
        return out
    }

    // ---- hojas ----

    private suspend fun sheets(
        lines: List<StakeholderLine>,
        disclosure: Disclosure
    ): List<XlsxSheet> {
        val readability = Readability(metadata)
        val readable = RegisterSheets.REQUIRES.filter { readability.canRead(it) }.toSet()
        if (disclosure == Disclosure.PUBLICO) {
            return RegisterSheets.aggregates(lines, RegisterContext(readable, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
        }

        fun ids(field: String) = lines.mapNotNull { Values.text(it.attributes[field]) }

        suspend fun names(
            objectName: String,
            path: String,
            field: String,
            ids: Collection<String>
        ): Map<String, String> =
            if (path in readable && ids.isNotEmpty()) {
                readIds(objectName, ids).associate { it.id to Values.text(it.attributes[field]).orEmpty() }
            } else {
                emptyMap()
            }

        val people = if (disclosure == Disclosure.INTERNO) people(readable, ids("person")) else emptyMap()
        val positions = positions(readable, ids("person"))
        val organizations = names("organization", "organization.name", "name", ids("organization") + positions.organizationIds)
        val context =
            RegisterContext(
                readable = readable,
                projects = names("project", "project.code", "code", ids("project")),
                people = people,
                organizations = organizations,
                localities = names("locality", "locality.name", "name", ids("locality")),
                positions = positions.labels(organizations)
            )
        return RegisterSheets.register(lines, context, disclosure)
    }

    // el nombre de cada persona, solo si quien exporta puede leer sus nombres (permiso de campo, security.md §7.9): sin
    // lectura de given_names no se pide ningún registro de person
    private suspend fun people(
        readable: Set<String>,
        ids: Collection<String>
    ): Map<String, String> {
        if (ids.isEmpty() || "person.given_names" !in readable) return emptyMap()
        val surnames = "person.surnames" in readable
        return readIds("person", ids)
            .mapNotNull { p ->
                val name =
                    listOfNotNull(Values.text(p.attributes["given_names"]), if (surnames) Values.text(p.attributes["surnames"]) else null)
                        .joinToString(" ")
                if (name.isBlank()) null else p.id to name
            }.toMap()
    }

    // los cargos VIGENTE de cada persona (Positions); vacío si quien exporta no lee el estado ni el título del cargo
    private class CurrentPositions(
        val byPerson: Map<String, List<Positions.Current>>
    ) {
        val organizationIds: List<String> get() = byPerson.values.flatten().mapNotNull { it.organization }

        // «cargo, organización» de cada persona (Positions.label)
        fun labels(organizations: Map<String, String>): Map<String, List<String>> =
            byPerson.mapValues { (_, list) -> list.map { Positions.label(it, organizations) }.filter { it.isNotBlank() } }
    }

    private suspend fun positions(
        readable: Set<String>,
        people: Collection<String>
    ): CurrentPositions {
        if (people.isEmpty() || "${Positions.OBJECT}.${Positions.STATUS}" !in readable || "${Positions.OBJECT}.position_title" !in readable) {
            return CurrentPositions(emptyMap())
        }
        val rows = mutableListOf<RecordResponse>()
        for (chunk in people.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            rows += read(Positions.OBJECT, mapOf(Positions.STATUS to Positions.CURRENT), listOf(Criteria.anyOf("person", chunk)), already = rows.size)
        }
        return CurrentPositions(rows.mapNotNull(Positions::current).groupBy { it.person })
    }

    // ---- el cuerpo ----

    private fun description(
        projects: List<UUID>?,
        filters: Map<String, Any?>,
        disclosure: Disclosure
    ): String =
        buildString {
            append("filtros: proyectos=")
            append(
                when {
                    projects == null -> "todos"
                    projects.size > MAX_LISTED -> "${projects.size} proyectos"
                    else -> projects.joinToString(",")
                }
            )
            filters.filterKeys { it != "project" }.toSortedMap().forEach { (key, value) -> append("; ").append(key).append('=').append(value) }
            if (disclosure == Disclosure.EXTERNO) append("; lista de verificación de EXTERNO confirmada")
        }

    private fun text(
        filters: Map<String, Any?>,
        name: String
    ): String? = Values.text(filters[name])

    // una lista de UUID (o uno solo) de un filtro; otra cosa es un 400 que nombra el filtro
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
        const val STAKEHOLDER = "stakeholder"
        const val EXPORT = "EXPORT"
        private const val TOO_MANY_FIELD = "filters.project"

        // filtro de la petición -> campo de stakeholder cuyo catálogo lo valida
        private val EQUALITY = listOf("scope" to "stakeholder_scope", "stakeholderType" to "stakeholder_type", "influenceGroup" to "influence_group")
        val FILTERS = setOf("project", "scope", "stakeholderType", "influenceGroup", "locality", "includeRetired")

        private const val RETIRED = "retired"
        private const val CHUNK = 500
        private const val MAX_LISTED = 20
    }
}
