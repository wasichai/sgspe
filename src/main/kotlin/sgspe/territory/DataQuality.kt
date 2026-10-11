package sgspe.territory

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.TenantModel
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.util.UUID

// las cinco revisiones del listado de calidad (gis.md §8, RQ-GIS-011, RQ-GIS-015, especificación de F6 §3.6)
enum class QualityCheck { INVALID, OUT_OF_ZONE, STALE_AREA, NO_TERRITORY, NO_GEOMETRY }

// una fila tal como sale del SQL, antes de armar su detalle
data class QualityRaw(
    val objectName: String,
    val id: UUID,
    val label: String?,
    val field: String?,
    val reason: String?,
    val zone: Int?,
    val expected: Int?,
    val stored: BigDecimal?,
    val computed: BigDecimal?
)

data class QualityRow(
    @get:JsonProperty("object") val objectName: String,
    val id: UUID,
    val label: String?,
    val field: String?,
    val detail: String?
)

data class QualityPage(
    val check: String,
    val content: List<QualityRow>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean
)

// qué lee cada revisión y su SQL sobre las tablas de SpatialGate (cada una ya filtrada por organización y por todos los
// RecordReadScope; ASM-122). los nombres de tabla y columna salen de GateTables; los valores, todos enlazados. una fila por
// registro y campo; varias tablas en un solo UNION ALL ordenado por objeto, id y campo
object DataQualityChecks {
    private const val PROJECT = "project"
    private const val AREA_OF_INFLUENCE = "area_of_influence"
    val MANAGEMENT = listOf("grievance", "inquiry", "stakeholder_request", "commitment", "social_risk", "incident")

    // las geometrías revisadas: las de las capas LYR-01 a LYR-16 y la ubicación de los registros de gestión
    private val GEOMETRIES: Map<String, List<String>> =
        linkedMapOf(
            "administrative_unit" to listOf("boundary"),
            "locality" to listOf("location", "boundary"),
            "road_access" to listOf("trace"),
            "community" to listOf("territory"),
            "community_sector" to listOf("boundary"),
            "mining_concession" to listOf("boundary"),
            "environmental_feature" to listOf("location", "trace", "boundary"),
            PROJECT to listOf("location", "interest_area"),
            "project_component" to listOf("location", "trace", "footprint"),
            AREA_OF_INFLUENCE to listOf("boundary"),
            "land_parcel" to listOf("boundary"),
            "school" to listOf("location"),
            "health_facility" to listOf("location")
        ) + MANAGEMENT.associateWith { listOf("location") }

    // los que tienen proyecto: su zona esperada es la del working_crs de su proyecto
    private val BY_PROJECT: Set<String> = setOf("project_component", AREA_OF_INFLUENCE) + MANAGEMENT
    private val WITHOUT_GEOMETRY: Map<String, List<String>> = linkedMapOf("locality" to listOf("location", "boundary"), "community" to listOf("territory"))

    // el texto que nombra un registro en la fila: su código o su nombre (road_access no tiene ninguno)
    private val CODED: Set<String> = setOf("land_parcel", "mining_concession", PROJECT, "project_component") + MANAGEMENT
    private val LABEL: Map<String, String> =
        GEOMETRIES.keys.filter { it !in setOf("road_access", AREA_OF_INFLUENCE) }.associateWith { if (it in CODED) "code" else "name" }

    fun objects(check: QualityCheck): List<String> =
        when (check) {
            QualityCheck.INVALID, QualityCheck.OUT_OF_ZONE -> GEOMETRIES.keys.toList()
            QualityCheck.STALE_AREA -> Areas.GEOMETRY.keys.toList()
            QualityCheck.NO_TERRITORY -> MANAGEMENT
            QualityCheck.NO_GEOMETRY -> WITHOUT_GEOMETRY.keys.toList()
        }

    fun fields(
        check: QualityCheck,
        objectName: String,
        byProject: Boolean
    ): Set<String> =
        buildSet {
            LABEL[objectName]?.let(::add)
            when (check) {
                QualityCheck.INVALID -> addAll(GEOMETRIES.getValue(objectName))
                QualityCheck.OUT_OF_ZONE -> {
                    addAll(GEOMETRIES.getValue(objectName))
                    if (objectName in BY_PROJECT) add(PROJECT)
                    if (objectName == PROJECT) add("working_crs")
                }
                QualityCheck.STALE_AREA -> {
                    add(Areas.GEOMETRY.getValue(objectName))
                    add(Areas.FIELD)
                    if (objectName == AREA_OF_INFLUENCE) add(PROJECT)
                }
                QualityCheck.NO_TERRITORY -> addAll(listOf("locality", "community", "location"))
                QualityCheck.NO_GEOMETRY -> addAll(WITHOUT_GEOMETRY.getValue(objectName))
            }
            if (byProject && objectName in BY_PROJECT) add(PROJECT)
        }

    fun needsProject(
        check: QualityCheck,
        objects: List<String>
    ): Boolean = (check == QualityCheck.OUT_OF_ZONE && objects.any { it in BY_PROJECT }) || (check == QualityCheck.STALE_AREA && AREA_OF_INFLUENCE in objects)

    fun sql(
        tables: GateTables,
        bind: (Any) -> String,
        check: QualityCheck,
        objects: List<String>,
        project: UUID?,
        envelope: ImportProperties,
        limit: Int,
        offset: Int
    ): String {
        val parts =
            objects.flatMap { objectName ->
                when (check) {
                    QualityCheck.INVALID -> GEOMETRIES.getValue(objectName).map { invalid(tables, bind, objectName, it, project) }
                    QualityCheck.OUT_OF_ZONE -> GEOMETRIES.getValue(objectName).map { outOfZone(tables, bind, objectName, it, project, envelope) }
                    QualityCheck.STALE_AREA -> listOf(staleArea(tables, bind, objectName, project))
                    QualityCheck.NO_TERRITORY -> listOf(missing(tables, bind, objectName, listOf("locality", "community", "location"), project))
                    QualityCheck.NO_GEOMETRY -> listOf(missing(tables, bind, objectName, WITHOUT_GEOMETRY.getValue(objectName), project))
                }
            }
        return "SELECT * FROM (${parts.joinToString(" UNION ALL ")}) q ORDER BY q.\"object\", q.id, q.field LIMIT ${bind(limit)} OFFSET ${bind(offset)}"
    }

    fun detail(
        check: QualityCheck,
        raw: QualityRaw
    ): String? =
        when (check) {
            QualityCheck.INVALID -> GeoSql.reasonLabel(raw.reason)
            QualityCheck.OUT_OF_ZONE ->
                if (raw.expected != null && raw.zone != null && raw.zone != raw.expected) {
                    "zona ${raw.zone - 32700}S; el proyecto trabaja en ${raw.expected - 32700}S"
                } else {
                    "fuera de la región esperada (envolvente del Perú)"
                }
            QualityCheck.STALE_AREA ->
                if (raw.stored ==
                    null
                ) {
                    "sin área calculada; recalculada ${raw.computed?.toPlainString()} ha"
                } else {
                    "guardada ${raw.stored.toPlainString()} ha; recalculada ${raw.computed?.toPlainString()} ha"
                }
            QualityCheck.NO_TERRITORY -> "sin localidad, comunidad ni ubicación"
            QualityCheck.NO_GEOMETRY -> "sin geometría: no se dibuja ni sirve para ubicar por relación"
        }

    // ---- las partes del UNION ALL: object, id, label, field, reason, zone, expected, stored, computed ----

    private fun head(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        field: String?
    ): String {
        val label = LABEL[objectName]?.let { "CAST(t.${tables.column(objectName, it)} AS text)" } ?: "CAST(NULL AS text)"
        val named = field?.let { "CAST(${bind(it)} AS text)" } ?: "CAST(NULL AS text)"
        return "SELECT CAST(${bind(objectName)} AS text) AS \"object\", t.id AS id, $label AS label, $named AS field"
    }

    private fun projectFilter(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        project: UUID?
    ): String =
        when {
            project == null -> ""
            objectName in BY_PROJECT -> " AND t.${tables.column(objectName, PROJECT)} = ${bind(project)}"
            objectName == PROJECT -> " AND t.id = ${bind(project)}"
            else -> ""
        }

    // el SRID del working_crs («EPSG 32717» -> 32717) del proyecto unido como p, o del proyecto mismo
    private fun expectedZone(
        tables: GateTables,
        objectName: String
    ): Pair<String, String> =
        when (objectName) {
            in BY_PROJECT ->
                "LEFT JOIN ${tables.table(PROJECT)} p ON p.id = t.${tables.column(objectName, PROJECT)}" to
                    "CAST(substring(p.${tables.column(PROJECT, "working_crs")} FROM '[0-9]+') AS int)"
            PROJECT -> "" to "CAST(substring(t.${tables.column(PROJECT, "working_crs")} FROM '[0-9]+') AS int)"
            else -> "" to "CAST(NULL AS int)"
        }

    private fun invalid(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        field: String,
        project: UUID?
    ): String {
        val g = "t.${tables.column(objectName, field)}"
        return "${head(tables, bind, objectName, field)}, ST_IsValidReason($g) AS reason, CAST(NULL AS int) AS zone, CAST(NULL AS int) AS expected, " +
            "CAST(NULL AS numeric) AS stored, CAST(NULL AS numeric) AS computed FROM ${tables.table(objectName)} t " +
            "WHERE $g IS NOT NULL AND NOT ST_IsValid($g)${projectFilter(tables, bind, objectName, project)}"
    }

    private fun outOfZone(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        field: String,
        project: UUID?,
        envelope: ImportProperties
    ): String {
        val g = "t.${tables.column(objectName, field)}"
        val (join, expected) = expectedZone(tables, objectName)
        return "${head(tables, bind, objectName, field)}, CAST(NULL AS text) AS reason, z.zone AS zone, z.expected AS expected, " +
            "CAST(NULL AS numeric) AS stored, CAST(NULL AS numeric) AS computed FROM ${tables.table(objectName)} t $join " +
            "CROSS JOIN LATERAL (SELECT ST_X(ST_Centroid($g)) AS x, ST_Y(ST_Centroid($g)) AS y) c " +
            "CROSS JOIN LATERAL (SELECT CASE WHEN c.y <= 0 THEN 32700 + floor((c.x + 180) / 6)::int + 1 END AS zone, $expected AS expected) z " +
            "WHERE $g IS NOT NULL${projectFilter(
                tables,
                bind,
                objectName,
                project
            )} AND (c.x < ${bind(envelope.minLongitude)} OR c.x > ${bind(envelope.maxLongitude)} " +
            "OR c.y < ${bind(envelope.minLatitude)} OR c.y > ${bind(envelope.maxLatitude)} OR (z.expected IS NOT NULL AND z.zone IS DISTINCT FROM z.expected))"
    }

    // el área como la calcula Areas: el working_crs del proyecto para un área de influencia, si no la zona del centroide
    private fun staleArea(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        project: UUID?
    ): String {
        val field = Areas.GEOMETRY.getValue(objectName)
        val g = "t.${tables.column(objectName, field)}"
        val area = "t.${tables.column(objectName, Areas.FIELD)}"
        val (join, expected) = if (objectName == AREA_OF_INFLUENCE) expectedZone(tables, objectName) else "" to "CAST(NULL AS int)"
        return "${head(tables, bind, objectName, field)}, CAST(NULL AS text) AS reason, a.zone AS zone, CAST(NULL AS int) AS expected, " +
            "$area AS stored, a.computed AS computed FROM ${tables.table(objectName)} t $join " +
            "CROSS JOIN LATERAL (SELECT COALESCE($expected, CASE WHEN ST_Y(ST_Centroid($g)) <= 0 THEN 32700 + " +
            "floor((ST_X(ST_Centroid($g)) + 180) / 6)::int + 1 END) AS zone) zz " +
            "CROSS JOIN LATERAL (SELECT zz.zone AS zone, CASE WHEN zz.zone IN (32717, 32718, 32719) " +
            "THEN round((ST_Area(ST_Transform($g, zz.zone)) / 10000)::numeric, 2) END AS computed) a " +
            "WHERE $g IS NOT NULL${projectFilter(
                tables,
                bind,
                objectName,
                project
            )} AND a.computed IS NOT NULL AND ($area IS NULL OR abs($area - a.computed) > 0.01)"
    }

    private fun missing(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        fields: List<String>,
        project: UUID?
    ): String {
        val empty = fields.joinToString(" AND ") { "t.${tables.column(objectName, it)} IS NULL" }
        return "${head(tables, bind, objectName, null)}, CAST(NULL AS text) AS reason, CAST(NULL AS int) AS zone, CAST(NULL AS int) AS expected, " +
            "CAST(NULL AS numeric) AS stored, CAST(NULL AS numeric) AS computed FROM ${tables.table(objectName)} t " +
            "WHERE $empty${projectFilter(tables, bind, objectName, project)}"
    }
}

// GET /api/sgspe/territory/data-quality: una revisión por página, por SpatialGate, solo con los objetos que la organización
// tiene (TenantModel) y que quien llama lee con todos los campos de la revisión; un objeto pedido que no lee es 403
@Service
class DataQuality(
    private val gate: SpatialGate,
    private val callers: Callers,
    private val metadata: MetadataService,
    private val tenantModel: TenantModel,
    private val properties: ImportProperties
) {
    suspend fun page(
        check: QualityCheck,
        objectName: String?,
        project: UUID?,
        page: Int,
        size: Int
    ): QualityPage {
        val user = callers.require()
        val candidates = DataQualityChecks.objects(check)
        if (objectName != null && objectName !in candidates) {
            throw ValidationException("Objeto inválido", "object", "la revisión ${check.name} cubre ${candidates.joinToString(", ")}")
        }
        val byProject = project != null
        val present = tenantModel.present(user.organizationId, objectName?.let(::listOf) ?: candidates)
        val readability = Readability(metadata)
        val readable = present.filter { o -> DataQualityChecks.fields(check, o, byProject).all { readability.canRead("$o.$it") } }
        if (objectName != null && objectName !in readable) {
            throw ForbiddenException(
                "La revisión ${check.name} de $objectName exige leer ${DataQualityChecks.fields(check, objectName, byProject).joinToString(", ")}"
            )
        }
        if (readable.isEmpty()) return QualityPage(check.name, emptyList(), page, size, false)
        val reads =
            readable.map { GateRead(it, DataQualityChecks.fields(check, it, byProject), principal = true) } +
                listOfNotNull(GateRead("project", setOf("working_crs")).takeIf { DataQualityChecks.needsProject(check, readable) })
        val outcome =
            gate.select(reads, { bind -> DataQualityChecks.sql(this, bind, check, readable, project, properties, size + 1, page * size) }) { row ->
                QualityRaw(
                    objectName = row.get("object", String::class.java)!!,
                    id = row.get("id", UUID::class.java)!!,
                    label = row.get("label", String::class.java),
                    field = row.get("field", String::class.java),
                    reason = row.get("reason", String::class.java),
                    zone = (row.get("zone") as Number?)?.toInt(),
                    expected = (row.get("expected") as Number?)?.toInt(),
                    stored = row.get("stored", BigDecimal::class.java),
                    computed = row.get("computed", BigDecimal::class.java)
                )
            }
        val rows =
            when (outcome) {
                is GateOutcome.Rows -> outcome.rows
                is GateOutcome.Unavailable -> throw ForbiddenException("La revisión ${check.name} exige leer ${outcome.missingFields.joinToString(", ")}")
            }
        return QualityPage(
            check.name,
            rows.take(size).map { QualityRow(it.objectName, it.id, it.label, it.field, DataQualityChecks.detail(check, it)) },
            page,
            size,
            rows.size > size
        )
    }
}

@RestController
@RequestMapping("/api/sgspe/territory/data-quality")
class DataQualityController(
    private val quality: DataQuality
) {
    @GetMapping
    suspend fun list(request: ServerHttpRequest): QualityPage {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("check", "object", "project", "page", "size"))
        val check =
            params.getFirst("check")?.let { raw -> QualityCheck.entries.firstOrNull { it.name == raw } }
                ?: throw ValidationException("Revisión inválida", "check", "es una de ${QualityCheck.entries.joinToString(", ")}")
        val project = params.getFirst("project")?.let { Records.uuid(it) ?: throw ValidationException("Proyecto inválido", "project", "debe ser un UUID") }
        val page = number(params.getFirst("page"), "page", 0, 0, MAX_PAGE)
        val size = number(params.getFirst("size"), "size", DEFAULT_SIZE, 1, MAX_SIZE)
        return quality.page(check, params.getFirst("object"), project, page, size)
    }

    private fun number(
        raw: String?,
        field: String,
        default: Int,
        min: Int,
        max: Int
    ): Int {
        if (raw == null) return default
        val value = raw.toIntOrNull()
        if (value == null || value < min || value > max) {
            throw Finding("OUT_OF_RANGE", "$field va de $min a $max", field, status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        return value
    }

    companion object {
        private const val DEFAULT_SIZE = 50
        private const val MAX_SIZE = 200
        private const val MAX_PAGE = 10_000
    }
}
