package sgspe.territory

import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Values
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.gis.GeometryType
import wasichai.gis.geometryType
import java.math.BigDecimal

data class GeometryZone(
    val srid: Int?,
    val outOfZone: Boolean,
    val outOfRegion: Boolean
)

data class GeometryCheck(
    val valid: Boolean,
    val reason: String?,
    val typeMatches: Boolean,
    val normalized: Map<String, Any?>?,
    val repaired: Map<String, Any?>?,
    val areaHaBefore: BigDecimal?,
    val areaHaAfter: BigDecimal?,
    val zone: GeometryZone
)

// las reglas puras de la validación previa: el tipo GeoJSON de un campo, qué se extrae de la reparación y la zona y la región
// (gis.md §4.3: envolvente del Perú de ImportProperties; zona del working_crs del proyecto)
object GeometryCheckRules {
    fun geoJsonType(type: GeometryType): String =
        when (type) {
            GeometryType.POINT -> "Point"
            GeometryType.LINESTRING -> "LineString"
            GeometryType.POLYGON -> "Polygon"
            GeometryType.MULTIPOINT -> "MultiPoint"
            GeometryType.MULTILINESTRING -> "MultiLineString"
            GeometryType.MULTIPOLYGON -> "MultiPolygon"
        }

    fun extractDimension(type: GeometryType): Int =
        when (type) {
            GeometryType.POINT, GeometryType.MULTIPOINT -> 1
            GeometryType.LINESTRING, GeometryType.MULTILINESTRING -> 2
            GeometryType.POLYGON, GeometryType.MULTIPOLYGON -> 3
        }

    // una geometría absurda (más vértices que MAX_VERTICES o anidada más de MAX_DEPTH niveles) es un 400: no llega a PostGIS
    const val MAX_VERTICES = 200_000
    private const val MAX_DEPTH = 8

    fun tooLarge(geometry: Map<String, Any?>): Boolean {
        var vertices = 0

        fun walk(
            node: Any?,
            depth: Int
        ): Boolean {
            if (depth > MAX_DEPTH) return true
            val list = node as? List<*> ?: return false
            if (list.isNotEmpty() && list.all { it is Number }) {
                vertices++
                return vertices > MAX_VERTICES
            }
            return list.any { walk(it, depth + 1) }
        }
        return walk(geometry["coordinates"], 0)
    }

    fun outOfRegion(
        centroid: Pair<Double, Double>?,
        properties: ImportProperties
    ): Boolean {
        val (x, y) = centroid ?: return true
        return x < properties.minLongitude || x > properties.maxLongitude || y < properties.minLatitude || y > properties.maxLatitude
    }

    fun outOfZone(
        centroid: Pair<Double, Double>?,
        workingSrid: Int?
    ): Boolean = workingSrid != null && (centroid == null || GeoSql.zoneOf(centroid.first, centroid.second) != workingSrid)
}

// POST /api/sgspe/territory/geometry-checks (especificación de F6 §3.6, gis.md §8, RQ-GIS-011): antes de guardar una geometría
// editada (S13, S14), su tipo contra el geometryType del campo, ST_IsValidReason en español, la reparación propuesta con
// ST_MakeValid y su cambio de área en el CRS de cálculo (el working_crs del proyecto o la zona del centroide, ASM-064), la
// normalización de un MultiPolygon de una parte y la zona y la región. no escribe nada; exige READ sobre el objeto
@Service
class GeometryChecks(
    private val geo: GeoSql,
    private val callers: Callers,
    private val permissions: Permissions,
    private val definitions: ObjectDefinitions,
    private val records: Records,
    private val properties: ImportProperties
) {
    suspend fun check(body: Map<String, Any?>): GeometryCheck {
        StrictBody.requireOnly(body, setOf("geometry", "object", "field", "project"))
        val user = callers.require()
        val objectName = body["object"] as? String ?: throw ValidationException("Falta el objeto", "object", "es el objeto del campo")
        permissions.require(user, "Validar una geometría de $objectName", Actions.READ to objectName)
        val fieldName = body["field"] as? String ?: throw ValidationException("Falta el campo", "field", "es el campo de geometría")
        val expected =
            definitions
                .current(user.organizationId, objectName)
                .fields
                .firstOrNull { it.name == fieldName }
                ?.geometryType
                ?: throw ValidationException("Campo inválido", "field", "no es una geometría de $objectName")
        val geometry =
            StrictBody.objectOf(body, "geometry").takeIf { it["type"] is String }
                ?: throw ValidationException("Geometría inválida", "geometry", "debe ser un GeoJSON con type y coordinates")
        if (GeometryCheckRules.tooLarge(geometry)) {
            throw ValidationException("Geometría demasiado grande", "geometry", "excede los vértices o el anidamiento que admite la validación")
        }
        val workingSrid =
            body["project"]?.let { raw ->
                val id = Records.uuid(raw) ?: throw ValidationException("Proyecto inválido", "project", "debe ser el id de un proyecto")
                Crs.srid(Values.text(records.get("project", id).attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
            }
        return try {
            val type = GeometryCheckRules.geoJsonType(expected)
            val validity = geo.validity(geometry)
            val normalized = geo.asType(geometry, type)
            val repaired =
                if (validity.valid) null else geo.repair(geometry, GeometryCheckRules.extractDimension(expected))?.let { geo.asType(it, type) ?: it }
            val centroid = geo.centroid(geometry)
            val srid = workingSrid ?: centroid?.let { GeoSql.zoneOf(it.first, it.second) }
            GeometryCheck(
                valid = validity.valid,
                reason = validity.reason,
                typeMatches = normalized != null,
                normalized = normalized,
                repaired = repaired,
                areaHaBefore = srid?.let { geo.areaHa(geometry, it) },
                areaHaAfter = if (repaired != null && srid != null) geo.areaHa(repaired, srid) else null,
                zone = GeometryZone(srid, GeometryCheckRules.outOfZone(centroid, workingSrid), GeometryCheckRules.outOfRegion(centroid, properties))
            )
        } catch (e: InvalidGeometryException) {
            throw ValidationException("Geometría inválida", "geometry", ImportRules.unprocessable(e))
        }
    }
}

@RestController
@RequestMapping("/api/sgspe/territory/geometry-checks")
class GeometryChecksController(
    private val checks: GeometryChecks
) {
    @PostMapping
    suspend fun check(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): GeometryCheck {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return checks.check(body)
    }
}
