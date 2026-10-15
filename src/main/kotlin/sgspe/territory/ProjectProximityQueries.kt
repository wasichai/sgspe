package sgspe.territory

import io.r2dbc.spi.Readable
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Finding
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.util.UUID

// algo cercano a un proyecto: un incidente, una señal de alerta o un conflicto local, a la distancia de su punto (o el de su
// localidad) a la referencia del proyecto, en metros en el working_crs
data class Nearby(
    val objectName: String,
    val id: UUID,
    val label: String?,
    val distanceM: BigDecimal
)

// las consultas de cercanía de un proyecto, por la compuerta (patrón b de gis.md §5.2):
// - concession-territories (caso 12): comunidades, sectores comunales y localidades sobre las concesiones del proyecto
//   (project_concession, leída por Records), con el área del cruce en la zona UTM de su centroide (ASM-064)
// - sensitive-distances (caso 13): los componentes vigentes a menos de distanceKm de un elemento ambiental sensible
// - nearby-conflicts (caso 7): incidentes y señales de alerta (su ubicación o la de su localidad) y los conflictos locales
//   ACTIVO (por sus localidades, que se leen por Records.related) a menos de distanceKm del área de interés o de los
//   componentes del proyecto, del más cercano al más lejano
// cada parte que quien llama no puede leer sale en missingFields y la lista trae las demás
@Service
class ProjectProximityQueries(
    private val gate: SpatialGate,
    private val frames: ProjectFrames,
    private val records: Records,
    private val metadata: MetadataService,
    private val limits: TerritoryQueryProperties
) {
    suspend fun concessionTerritories(
        projectId: UUID,
        limit: Int
    ): Map<String, Any?> {
        records.find(TerritoryNames.PROJECT, projectId)
            ?: throw Finding("PROJECT_NOT_FOUND", "El proyecto no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()
        val concessions =
            records.related(TerritoryNames.PROJECT, projectId, "project_concession", max = limits.maxRelated, field = "id").map { UUID.fromString(it.id) }
        val parts = SpatialParts<Map<String, Any?>>()
        if (concessions.isEmpty()) return parts.response("rows", emptyList(), truncated = false)
        for (target in TERRITORIES) {
            val reads =
                listOf(
                    GateRead(TerritoryNames.CONCESSION, setOf("code", "name", "boundary"), principal = true),
                    GateRead(target.objectName, target.geometries.toSet() + "name" + listOfNotNull(target.kind))
                )
            parts.add(gate.select(reads, { bind -> concessionSql(this, bind, concessions, target, limit) }) { row -> territoryRow(row, target) })
        }
        val sorted =
            parts.rows.sortedWith(
                compareBy(
                    { (it["concession"] as Map<*, *>)["code"]?.toString() },
                    { it["object"].toString() },
                    { it["name"]?.toString() },
                    { it["id"].toString() }
                )
            )
        return parts.response("rows", sorted.take(limit), truncated = sorted.size > limit)
    }

    // la misma consulta como la plataforma de `organizationId`, con las concesiones ya leídas: la usa solo el ensamblado de
    // T2 en JOB-05 (ReportSources, fuera de una petición; ArchitectureTest). lo que el tenant no tiene sale vacío
    suspend fun concessionTerritoriesAsPlatform(
        organizationId: UUID,
        concessions: List<UUID>
    ): List<Map<String, Any?>> {
        if (concessions.isEmpty()) return emptyList()
        val rows = mutableListOf<Map<String, Any?>>()
        for (target in TERRITORIES) {
            val reads =
                listOf(
                    GateRead(TerritoryNames.CONCESSION, setOf("code", "name", "boundary"), principal = true),
                    GateRead(target.objectName, target.geometries.toSet() + "name" + listOfNotNull(target.kind))
                )
            val outcome =
                gate.selectAsPlatform(organizationId, reads, { bind -> concessionSql(this, bind, concessions, target, limits.maxRows) }) { row ->
                    territoryRow(row, target)
                }
            if (outcome is GateOutcome.Rows) {
                if (outcome.rows.size > limits.maxRows) throw Records.tooMany(TerritoryNames.CONCESSION, limits.maxRows)
                rows += outcome.rows
            }
        }
        return rows.sortedWith(compareBy({ (it["concession"] as Map<*, *>)["code"]?.toString() }, { it["object"].toString() }, { it["name"]?.toString() }))
    }

    suspend fun sensitiveDistances(
        projectId: UUID,
        meters: BigDecimal,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<Map<String, Any?>>()
        val reads =
            listOf(
                GateRead(
                    TerritoryNames.COMPONENT,
                    setOf("code", "component_type", "project", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES,
                    principal = true
                ),
                GateRead(TerritoryNames.FEATURE, setOf("name", "feature_type") + FEATURE_GEOMETRIES, principal = true)
            )
        parts.add(
            gate.select(reads, { bind -> sensitiveSql(this, bind, frame, meters, limit) }) { row ->
                linkedMapOf(
                    "component" to
                        mapOf(
                            "id" to row.get("component", UUID::class.java).toString(),
                            "code" to row.get("component_code", String::class.java),
                            "componentType" to row.get("component_type", String::class.java)
                        ),
                    "feature" to
                        mapOf(
                            "id" to row.get("feature", UUID::class.java).toString(),
                            "name" to row.get("feature_name", String::class.java),
                            "featureType" to row.get("feature_type", String::class.java)
                        ),
                    "distanceM" to row.get("distance_m", BigDecimal::class.java)
                )
            }
        )
        return parts.response("rows", parts.rows.take(limit), truncated = parts.rows.size > limit)
    }

    suspend fun nearbyConflicts(
        projectId: UUID,
        meters: BigDecimal,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<Nearby>()
        for ((objectName, label) in EVENTS) {
            val reads = REFERENCE_READS + PLACE_READ + GateRead(objectName, setOf(label, "location", "locality"))
            parts.add(gate.select(reads, { bind -> eventSql(this, bind, frame, meters, limit, objectName, label) }, ::nearby))
        }
        val distances =
            when (
                val outcome =
                    gate.select(REFERENCE_READS + PLACE_READ, { bind -> localitySql(this, bind, frame, meters, limits.maxRows) }) { row ->
                        row.get("id", UUID::class.java)!! to row.get("distance_m", BigDecimal::class.java)!!
                    }
            ) {
                is GateOutcome.Rows -> {
                    if (outcome.rows.size > limits.maxRows) throw Records.tooMany(TerritoryNames.LOCALITY, limits.maxRows, "distanceKm")
                    outcome.rows.toMap()
                }
                is GateOutcome.Unavailable -> {
                    parts.missing(*outcome.missingFields.toTypedArray())
                    null
                }
            }
        if (distances != null) parts.rows += conflicts(distances, parts)
        val sorted = parts.rows.sortedWith(compareBy({ it.distanceM }, { it.objectName }, { it.id }))
        val items = sorted.take(limit).map { mapOf("object" to it.objectName, "id" to it.id.toString(), "label" to it.label, "distanceM" to it.distanceM) }
        return parts.response("items", items, truncated = sorted.size > limit)
    }

    // los conflictos ACTIVO a la distancia de su localidad más cercana; uno sin localidad cercana no sale
    private suspend fun conflicts(
        distances: Map<UUID, BigDecimal>,
        parts: SpatialParts<Nearby>
    ): List<Nearby> {
        if (distances.isEmpty()) return emptyList()
        val readability = Readability(metadata)
        // si no se lee el objeto, se nombra solo el objeto, no además su título
        if (readability.objectMissing("$CONFLICT.title")) {
            parts.missing(CONFLICT)
            return emptyList()
        }
        val unreadable = readability.missing(listOf("$CONFLICT.title"))
        if (unreadable.isNotEmpty()) parts.missing(*unreadable.toTypedArray())
        val active =
            try {
                records.all(CONFLICT, filters = mapOf("conflict_status" to "ACTIVO"), max = limits.maxRelated, field = "distanceKm")
            } catch (_: ForbiddenException) {
                parts.missing(CONFLICT)
                return emptyList()
            }
        return active.mapNotNull { conflict ->
            val id = UUID.fromString(conflict.id)
            records
                .related(CONFLICT, id, "local_conflict_locality", max = limits.maxRelated, field = "distanceKm")
                .mapNotNull { distances[UUID.fromString(it.id)] }
                .minOrNull()
                ?.let { Nearby(CONFLICT, id, Values.text(conflict.attributes["title"]), it) }
        }
    }

    private fun nearby(row: Readable): Nearby =
        Nearby(
            row.get("object", String::class.java)!!,
            row.get("id", UUID::class.java)!!,
            row.get("label", String::class.java),
            row.get("distance_m", BigDecimal::class.java)!!
        )

    private fun territoryRow(
        row: Readable,
        target: Territory
    ): Map<String, Any?> =
        linkedMapOf(
            "concession" to
                mapOf(
                    "id" to row.get("concession", UUID::class.java).toString(),
                    "code" to row.get("concession_code", String::class.java),
                    "name" to row.get("concession_name", String::class.java)
                ),
            "object" to target.objectName,
            "id" to row.get("id", UUID::class.java).toString(),
            "name" to row.get("name", String::class.java),
            "localityType" to row.get("kind", String::class.java),
            "areaHa" to row.get("area_ha", BigDecimal::class.java)
        )

    // un objeto del tenant que puede estar sobre una concesión, con sus geometrías y la columna de su tipo
    class Territory(
        val objectName: String,
        val geometries: List<String>,
        val kind: String?
    )

    companion object {
        const val CONFLICT = "local_conflict"
        private val FEATURE_GEOMETRIES = listOf("boundary", "trace", "location")

        // los elementos ambientales sensibles del caso 13 (DOC-01 §7.1.2, Tabla 8.1); MICROCUENCA y CERRO no lo son
        val SENSITIVE = listOf("ZONA DE PROTECCION", "FUENTE DE AGUA", "SITIO SAGRADO", "RESTO ARQUEOLOGICO")

        // los eventos de nearby-conflicts y la columna que los nombra
        private val EVENTS = listOf("incident" to "code", "early_warning_signal" to "source_type")

        val TERRITORIES =
            listOf(
                Territory(TerritoryNames.COMMUNITY, listOf("territory"), null),
                Territory(TerritoryNames.SECTOR, listOf("boundary"), null),
                Territory(TerritoryNames.LOCALITY, listOf("boundary", "location"), "locality_type")
            )

        val REFERENCE_READS =
            listOf(
                GateRead(TerritoryNames.PROJECT, setOf("interest_area")),
                GateRead(TerritoryNames.COMPONENT, setOf("project", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES)
            )
        private val PLACE_READ = GateRead(TerritoryNames.LOCALITY, setOf("location", "boundary"))

        // la referencia del proyecto: su área de interés y las versiones vigentes de sus componentes, juntas en 4326; NULL si
        // no tiene ninguna, y entonces nada está cerca
        internal fun reference(
            t: GateTables,
            bind: (Any) -> String,
            project: UUID
        ): String {
            val p = TerritoryNames.PROJECT
            val c = TerritoryNames.COMPONENT
            val component = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, 4326)
            return "WITH ref AS (SELECT ST_Collect(s.g) AS g FROM (" +
                "SELECT ST_Transform(p.${t.column(
                    p,
                    "interest_area"
                )}, 4326) AS g FROM ${t.table(p)} p WHERE p.${t.column(p, SpatialGate.ID)} = ${bind(project)} " +
                "UNION ALL SELECT $component AS g FROM ${t.table(c)} c " +
                "WHERE c.${t.column(c, "project")} = ${bind(project)} AND c.${t.column(c, "superseded")} IS NOT TRUE" +
                ") s WHERE s.g IS NOT NULL) "
        }

        // el punto de un registro de la localidad: su ubicación o un punto sobre su límite, en 4326
        private fun localityPoint(
            t: GateTables,
            alias: String
        ): String {
            val l = TerritoryNames.LOCALITY
            return "ST_Transform($alias.${t.column(l, "location")}, 4326), ST_PointOnSurface(ST_Transform($alias.${t.column(l, "boundary")}, 4326))"
        }

        // que la columna desnuda `column` (de SRID `columnSrid`) caiga en la envolvente de la referencia ampliada en grados: así
        // el índice GIST de la columna sirve al prefiltro
        private fun envelope(
            column: String,
            columnSrid: Int,
            bind: (Any) -> String,
            meters: BigDecimal
        ): String = "$column && ST_Transform(ST_Expand(ref.g, ${bind(SpatialSql.degrees(meters))}), $columnSrid)"

        private fun within(
            place: String,
            bind: (Any) -> String,
            srid: String,
            meters: BigDecimal
        ): String = "ST_DWithin(ST_Transform($place, $srid), ST_Transform(ref.g, $srid), ${bind(meters.toDouble())})"

        // los eventos con ubicación propia, y los que no la tienen por la ubicación (o un punto del límite) de su localidad
        internal fun eventSql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            meters: BigDecimal,
            limit: Int,
            objectName: String,
            label: String
        ): String {
            val l = TerritoryNames.LOCALITY
            val srid = bind(frame.srid)
            val own = "ST_Transform(e.${t.column(objectName, "location")}, 4326)"
            val viaLocality = "COALESCE(${localityPoint(t, "l")})"
            val head = "e.${t.column(objectName, SpatialGate.ID)} AS id, e.${t.column(objectName, label)}::text AS label"
            return reference(t, bind, frame.id) +
                "SELECT object, id, label, distance_m FROM (" +
                "SELECT CAST(${bind(objectName)} AS text) AS object, $head, ${SpatialSql.distanceM(own, "ref.g", srid)} AS distance_m " +
                "FROM ${t.table(objectName)} e CROSS JOIN ref WHERE " +
                "${envelope("e.${t.column(objectName, "location")}", t.srid(objectName, "location"), bind, meters)} AND ${within(own, bind, srid, meters)} " +
                "UNION ALL " +
                "SELECT CAST(${bind(objectName)} AS text) AS object, $head, ${SpatialSql.distanceM(viaLocality, "ref.g", srid)} AS distance_m " +
                "FROM ${t.table(
                    objectName
                )} e JOIN ${t.table(l)} l ON l.${t.column(l, SpatialGate.ID)} = e.${t.column(objectName, "locality")} CROSS JOIN ref " +
                "WHERE e.${t.column(objectName, "location")} IS NULL AND " +
                "(${envelope("l.${t.column(l, "location")}", t.srid(l, "location"), bind, meters)} OR " +
                "${envelope("l.${t.column(l, "boundary")}", t.srid(l, "boundary"), bind, meters)}) AND ${within(viaLocality, bind, srid, meters)}" +
                ") u ORDER BY distance_m, id LIMIT ${bind(limit + 1)}"
        }

        internal fun localitySql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            meters: BigDecimal,
            limit: Int
        ): String {
            val l = TerritoryNames.LOCALITY
            val srid = bind(frame.srid)
            val place = "COALESCE(${localityPoint(t, "l")})"
            return reference(t, bind, frame.id) +
                "SELECT l.${t.column(l, SpatialGate.ID)} AS id, ${SpatialSql.distanceM(place, "ref.g", srid)} AS distance_m " +
                "FROM ${t.table(l)} l CROSS JOIN ref WHERE " +
                "(${envelope("l.${t.column(l, "location")}", t.srid(l, "location"), bind, meters)} OR " +
                "${envelope("l.${t.column(l, "boundary")}", t.srid(l, "boundary"), bind, meters)}) AND ${within(place, bind, srid, meters)} " +
                "ORDER BY distance_m, id LIMIT ${bind(limit + 1)}"
        }

        internal fun sensitiveSql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            meters: BigDecimal,
            limit: Int
        ): String {
            val c = TerritoryNames.COMPONENT
            val f = TerritoryNames.FEATURE
            val srid = bind(frame.srid)
            val component = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, 4326)
            val feature = SpatialSql.firstOf(FEATURE_GEOMETRIES.map { "f.${t.column(f, it)}" }, 4326)
            val degrees = bind(SpatialSql.degrees(meters))
            val bare =
                FEATURE_GEOMETRIES.joinToString(" OR ") { g ->
                    "f.${t.column(f, g)} && ST_Transform(ST_Expand($component, $degrees), ${t.srid(f, g)})"
                }
            return "SELECT c.${t.column(c, SpatialGate.ID)} AS component, c.${t.column(c, "code")}::text AS component_code, " +
                "c.${t.column(c, "component_type")}::text AS component_type, f.${t.column(f, SpatialGate.ID)} AS feature, " +
                "f.${t.column(f, "name")}::text AS feature_name, f.${t.column(f, "feature_type")}::text AS feature_type, " +
                "${SpatialSql.distanceM(component, feature, srid)} AS distance_m " +
                "FROM ${t.table(c)} c JOIN ${t.table(f)} f ON f.${t.column(f, "feature_type")} = ANY(${bind(SENSITIVE.toTypedArray())}) " +
                "AND ($bare) " +
                "AND ST_DWithin(ST_Transform($component, $srid), ST_Transform($feature, $srid), ${bind(meters.toDouble())}) " +
                "WHERE c.${t.column(c, "project")} = ${bind(frame.id)} AND c.${t.column(c, "superseded")} IS NOT TRUE " +
                "ORDER BY distance_m, 2, 5, 1, 4 LIMIT ${bind(limit + 1)}"
        }

        internal fun concessionSql(
            t: GateTables,
            bind: (Any) -> String,
            concessions: List<UUID>,
            target: Territory,
            limit: Int
        ): String {
            val k = TerritoryNames.CONCESSION
            val x = target.objectName
            val boundary = "k.${t.column(k, "boundary")}"
            val crossing =
                target.geometries.joinToString(
                    " OR "
                ) { g ->
                    "(ST_Intersects(ST_Transform($boundary, ${t.srid(
                        x,
                        g
                    )}), x.${t.column(x, g)}) AND NOT ST_Touches(ST_Transform($boundary, ${t.srid(x, g)}), x.${t.column(x, g)}))"
                }
            val shape = SpatialSql.firstOf(target.geometries.map { "x.${t.column(x, it)}" }, 4326)
            val piece = "ST_Intersection(ST_Transform($boundary, 4326), $shape)"
            val kind = target.kind?.let { "x.${t.column(x, it)}::text" } ?: "NULL::text"
            return "SELECT k.${t.column(k, SpatialGate.ID)} AS concession, k.${t.column(k, "code")}::text AS concession_code, " +
                "k.${t.column(k, "name")}::text AS concession_name, x.${t.column(x, SpatialGate.ID)} AS id, x.${t.column(x, "name")}::text AS name, " +
                "$kind AS kind, ${SpatialSql.zoneAreaHa(piece)} AS area_ha " +
                "FROM ${t.table(k)} k JOIN ${t.table(x)} x ON ($crossing) " +
                "WHERE k.${t.column(k, SpatialGate.ID)} = ANY(${bind(concessions.toTypedArray())}) ORDER BY 2, 3, 1, 5, 4 LIMIT ${bind(limit + 1)}"
        }
    }
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory/projects")
class ProjectProximityQueriesController(
    private val queries: ProjectProximityQueries,
    private val limits: TerritoryQueryProperties
) {
    @GetMapping("/{id}/concession-territories")
    suspend fun concessionTerritories(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("limit"))
        return queries.concessionTerritories(id, QueryParams.limit(request.queryParams.getFirst("limit"), limits))
    }

    @GetMapping("/{id}/sensitive-distances")
    suspend fun sensitiveDistances(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("distanceKm", "limit"))
        return queries.sensitiveDistances(id, QueryParams.distanceM(params.getFirst("distanceKm"), limits), QueryParams.limit(params.getFirst("limit"), limits))
    }

    @GetMapping("/{id}/nearby-conflicts")
    suspend fun nearbyConflicts(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("distanceKm", "limit"))
        return queries.nearbyConflicts(id, QueryParams.distanceM(params.getFirst("distanceKm"), limits), QueryParams.limit(params.getFirst("limit"), limits))
    }
}
