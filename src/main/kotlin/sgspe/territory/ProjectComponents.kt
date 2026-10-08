package sgspe.territory

import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.IfMatch
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RuleException
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import sgspe.scope.Assignments
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

data class ComponentResult(
    val component: RecordResponse,
    val previousVersion: RecordResponse?,
    val distanceM: BigDecimal?
)

// el alta y la versión manual de un componente (api.md §4.2, RQ-GIS-010): project_component es apiOnly y nunca se
// sobrescribe. un código nuevo en el proyecto es la versión 1; un código que ya existe con otras coordenadas es una
// versión nueva (version_number siguiente, previous_version) y la anterior pasa a superseded, en una transacción
// (ComponentVersions, la misma lógica de la importación); con las mismas coordenadas (menos de 1 cm) es un 409 SAME_COORDINATES:
// sus datos se cambian con PATCH. la distancia se mide en el working_crs del proyecto o, si no lo tiene, en la zona del
// centroide. el PATCH cambia datos descriptivos, nunca coordenadas ni el código.
// todo se escribe como quien llama (con su permiso, su alcance y la marca de Records), nunca como la plataforma.
// el alta y el PATCH toman el candado de los componentes del proyecto, el mismo que la confirmación de una importación
// (TerritoryImports.componentLock): un comando y una importación no se pisan, y la clave única (proyecto, código, versión) no
// llega a chocar; si chocara por otra vía, es un 409 con código. las cuentas de PostGIS que no dependen de lo guardado van
// antes de abrir la transacción (un fallo de PostGIS dentro la dejaría abortada, P20); solo la distancia contra la versión
// vigente se mide dentro, y su fallo revierte todo y sale como 400 INVALID_GEOMETRY
@Service
class ProjectComponents(
    private val records: Records,
    private val versions: ComponentVersions,
    private val versionCheck: Versions,
    private val geo: GeoSql,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val callers: Callers,
    private val properties: ImportProperties,
    private val assignments: Assignments
) {
    suspend fun register(
        body: Map<String, Any?>,
        reason: String?
    ): ComponentResult {
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        val attributes = StrictBody.objectOf(body, "attributes")
        val geometries = StrictBody.objectOf(body, "geometries")
        StrictBody.requireOnly(attributes, CREATABLE, "attributes.")
        StrictBody.requireOnly(geometries, GEOMETRY_TYPES.keys, "geometries.")
        // «falta» y «mal formado» llevan códigos distintos: el cliente sabe si olvidó el campo o lo mandó roto
        val project =
            if (Values.text(attributes["project"]) == null) {
                throw rejected("FIELD_REQUIRED", "Falta el proyecto", "attributes.project")
            } else {
                Records.uuid(attributes["project"]) ?: throw rejected("INVALID_VALUE", "El proyecto debe ser un UUID", "attributes.project")
            }
        val code = Values.text(attributes["code"]) ?: throw rejected("FIELD_REQUIRED", "Falta el código del componente", "attributes.code")
        checkNumbers(attributes)
        val sent = geometries.filterValues { it != null }
        if (sent.isEmpty()) throw rejected("GEOMETRY_REQUIRED", "Falta la geometría: envíe una de location, trace o footprint", "geometries")
        if (sent.size > 1) throw rejected("GEOMETRY_REQUIRED", "Envíe una sola geometría: location, trace o footprint", "geometries")
        val (field, geometry) = sent.entries.single().let { it.key to geometryOf(it.key, it.value) }
        val user = callers.require()
        permissions.require(user, "Registrar un componente", Actions.CREATE to COMPONENT, Actions.UPDATE to COMPONENT)
        requireAssigned(user, project)
        val projectRecord =
            records.find("project", project) ?: throw rejected("PROJECT_NOT_FOUND", "El proyecto no existe o no tiene acceso a él", "attributes.project")
        val srid = measure(projectRecord, field, geometry)
        val data = attributes + mapOf("code" to code)
        return try {
            transactions.locked(TerritoryImports.componentLock(user.organizationId, project)) {
                val all =
                    records
                        .all(COMPONENT, filters = mapOf("project" to project.toString()), criteria = listOf(Criteria.textIn("code", listOf(code))))
                        .filter { Values.text(it.attributes["code"]) == code }
                // la versión siguiente sale de la mayor que existe, vigente o no: la clave única no admite repetir un número
                val current = all.maxByOrNull { Values.decimal(it.attributes["version_number"]) ?: BigDecimal.ZERO }
                if (current == null) {
                    return@locked ComponentResult(versions.first(project, data, reason, mapOf(field to geometry)), null, null)
                }
                val stored = Records.geometriesOf(current)
                val previous =
                    stored[field] ?: stored.values.firstOrNull { it != null }
                        ?: throw Finding(
                            "NO_CURRENT_GEOMETRY",
                            "La versión vigente de $code no tiene geometría: corríjala antes de versionarla",
                            "geometries"
                        ).toCodedException()
                val distance =
                    try {
                        geo.distanceM(previous, geometry, srid)
                    } catch (e: InvalidGeometryException) {
                        throw unprocessable(field, e)
                    } catch (e: IllegalArgumentException) {
                        throw unprocessable(field, e)
                    }
                if (stored[field] != null && distance < properties.unchangedToleranceM) {
                    throw Finding(
                        "SAME_COORDINATES",
                        "$code ya existe con las mismas coordenadas: cambie sus datos con PATCH",
                        "geometries.$field"
                    ).toCodedException()
                }
                val (created, superseded) = versions.next(project, current, data, reason, mapOf(field to geometry))
                ComponentResult(created, superseded, distance)
            }
        } catch (_: DuplicateKeyException) {
            throw Finding(
                "VERSION_CONFLICT",
                "Otra escritura registró esa versión de $code mientras se guardaba: vuelva a cargarlo",
                "attributes.code"
            ).toCodedException()
        }
    }

    suspend fun change(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): RecordResponse {
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        if (body.containsKey("geometries")) {
            throw rejected(
                "COORDINATES_NOT_EDITABLE",
                "Las coordenadas no se cambian aquí: una geometría nueva es una versión nueva (POST /api/sgspe/project-components)",
                "geometries"
            )
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        if (attributes.isEmpty()) throw rejected("FIELD_REQUIRED", "Envíe al menos un campo en attributes", "attributes")
        val locked = attributes.keys.filter { it !in PATCHABLE }.sorted()
        if (locked.isNotEmpty()) {
            throw RuleException(
                HttpStatus.BAD_REQUEST,
                "No se cambia con PATCH: ${locked.joinToString(", ")}; se admiten ${PATCHABLE.sorted().joinToString(", ")}",
                locked.map { FieldViolation("attributes.$it", "FIELD_NOT_EDITABLE") }
            )
        }
        checkNumbers(attributes)
        val expected = IfMatch.required(ifMatch)
        val user = callers.require()
        permissions.require(user, "Cambiar un componente", Actions.UPDATE to COMPONENT)
        // el proyecto no cambia nunca, así que el candado de sus componentes se toma sobre el que se leyó
        val project =
            Records.uuid(records.get(COMPONENT, id).attributes["project"])
                ?: throw rejected("PROJECT_NOT_FOUND", "El componente no tiene proyecto", "attributes.project")
        requireAssigned(user, project)
        return transactions.locked(TerritoryImports.componentLock(user.organizationId, project)) {
            val current = records.get(COMPONENT, id)
            // una versión reemplazada es historia: cambiarla reescribiría lo que el mapa y los reportes ya mostraron
            // (gis.md §2.4 y §4.4, RQ-GIS-010 «nunca sobrescribe»). va antes de If-Match para que un If-Match viejo de una
            // versión reemplazada diga la causa real, y dentro del candado, tras releer: una versión nueva pudo caer antes
            if (Values.bool(current.attributes["superseded"]) == true) {
                throw Finding(
                    "COMPONENT_SUPERSEDED",
                    "El componente es una versión reemplazada y no se cambia: cambie la vigente",
                    "attributes"
                ).toCodedException()
            }
            versionCheck.check(COMPONENT, id, expected, current.updatedAt, "El componente")
            // solo lo que cambia se escribe (P11): un PATCH que no cambia nada no toca el updatedAt
            val changes = attributes.filter { (name, value) -> !Values.same(current.attributes[name], value) }
            if (changes.isEmpty()) current else records.patch(COMPONENT, id, changes, reason)
        }
    }

    // el alcance de escritura (opción A, security.md §9.2) se comprueba antes de gastar cuentas de PostGIS y también cuando el
    // PATCH no cambia nada y no llega a escribir: ProjectScopeGuard lo exige igual en la escritura, y el resultado no depende
    // de los datos. mismo código y campo que la guarda
    private suspend fun requireAssigned(
        user: AuthenticatedUser,
        project: UUID
    ) {
        if (user.isAdmin || assignments.isAssigned(user.userId, project)) return
        throw Finding(
            "NOT_ASSIGNED",
            "No tiene una asignación vigente al proyecto $project: solo su equipo escribe en él",
            "project",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
    }

    // todo lo que se mide antes de la transacción: la geometría es válida y está en longitud y latitud, y PostGIS puede
    // llevarla al CRS de cálculo. devuelve ese CRS: el working_crs del proyecto, o la zona del centroide, o la central
    private suspend fun measure(
        project: RecordResponse,
        field: String,
        geometry: Map<String, Any?>
    ): Int {
        if (!inRange(geometry["coordinates"])) {
            throw rejected("INVALID_GEOMETRY", "Geometría inválida: las coordenadas deben ser longitud y latitud en EPSG:4326", "geometries.$field")
        }
        try {
            val validity = geo.validity(geometry)
            if (!validity.valid) throw rejected("INVALID_GEOMETRY", "Geometría inválida: ${validity.reason ?: "no es válida"}", "geometries.$field")
            val srid =
                Crs.srid(Values.text(project.attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
                    ?: geo.centroidZone(geometry)
                    ?: DEFAULT_SRID
            geo.utm(geometry, srid)
            return srid
        } catch (e: InvalidGeometryException) {
            throw unprocessable(field, e)
        } catch (e: IllegalArgumentException) {
            throw unprocessable(field, e)
        }
    }

    private fun unprocessable(
        field: String,
        e: Exception
    ): RuleException = rejected("INVALID_GEOMETRY", ImportRules.unprocessable(e).replaceFirstChar { it.uppercase() }, "geometries.$field")

    // los rangos de la importación (azimut, inclinación, profundidad, cota): fuera de ellos es un 400 sobre el campo
    private fun checkNumbers(attributes: Map<String, Any?>) {
        for (name in NUMERIC) {
            val raw = attributes[name]
            if (Values.text(raw) == null) continue
            val value = Values.decimal(raw) ?: throw rejected("INVALID_NUMBER", "$name debe ser un número", "attributes.$name")
            ImportRules.numberIssue(name, value)?.let { throw rejected("OUT_OF_RANGE", "$name $it", "attributes.$name") }
        }
    }

    private fun rejected(
        code: String,
        message: String,
        field: String
    ): RuleException = RuleException(HttpStatus.BAD_REQUEST, message, listOf(FieldViolation(field, code)))

    @Suppress("UNCHECKED_CAST")
    private fun geometryOf(
        field: String,
        value: Any?
    ): Map<String, Any?> {
        val geometry = value as? Map<String, Any?> ?: throw rejected("INVALID_GEOMETRY", "La geometría debe ser un GeoJSON EPSG:4326", "geometries.$field")
        if (geometry["type"] != GEOMETRY_TYPES[field]) {
            throw rejected("INVALID_GEOMETRY", "$field es un ${GEOMETRY_TYPES[field]}", "geometries.$field")
        }
        return geometry
    }

    // cada posición del GeoJSON es longitud, latitud (y quizá cota) finitas y dentro de rango
    private fun inRange(coordinates: Any?): Boolean {
        val list = coordinates as? List<*> ?: return false
        if (list.isEmpty()) return false
        if (list.first() is Number) {
            val x = (list.getOrNull(0) as? Number)?.toDouble() ?: return false
            val y = (list.getOrNull(1) as? Number)?.toDouble() ?: return false
            return list.all { it is Number } && x.isFinite() && y.isFinite() && x in -MAX_LONGITUDE..MAX_LONGITUDE && y in -MAX_LATITUDE..MAX_LATITUDE
        }
        return list.all { inRange(it) }
    }

    companion object {
        const val COMPONENT = ComponentVersions.COMPONENT
        val GEOMETRY_TYPES = mapOf("location" to "Point", "trace" to "LineString", "footprint" to "Polygon")
        val PATCHABLE = setOf("component_type", "drill_type", "component_status", "azimuth", "dip", "depth_m", "exploration_campaign", "source_reference")
        val CREATABLE = PATCHABLE + setOf("project", "code", "source_crs", "source_easting", "source_northing", "source_elevation")
        private val NUMERIC = listOf("azimuth", "dip", "depth_m", "source_elevation")
        private const val MAX_LONGITUDE = 180.0
        private const val MAX_LATITUDE = 90.0

        // sin working_crs ni zona del centroide: la zona central del Perú, como la importación
        private const val DEFAULT_SRID = 32718
    }
}

// api.md §4.2. Idempotency-Key se acepta y se ignora en F2 (§3.5)
@RestController
@RequestMapping("/api/sgspe/project-components")
class ProjectComponentsController(
    private val components: ProjectComponents
) {
    @PostMapping
    suspend fun register(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ComponentResult> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val result = components.register(body, ChangeReason.fromHeader(reason))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/objects/project_component/records/${result.component.id}"))
            .body(result)
    }

    @PatchMapping("/{id}")
    suspend fun change(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): RecordResponse {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return components.change(id, body, ChangeReason.fromHeader(reason), ifMatch)
    }
}
