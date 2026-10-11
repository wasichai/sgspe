package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import tools.jackson.databind.json.JsonMapper
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// las agregaciones territoriales (patrón b de gis.md §5.2):
// - concentration (casos 4 y 6): eventos por localidad o, sin ella, por comunidad, para el mapa de calor; nunca por punto. la
//   celda trae el punto de su localidad (su ubicación o un punto sobre su límite) o de su comunidad. las QCS confidenciales
//   que quien llama no ve no cuentan: la compuerta aplica ConfidentialAttentionScope como Records
// - summary (caso 15, RQ-DSH-029): la superficie oficial del área, sus localidades miembro por tipo y los componentes del
//   proyecto sobre tierra comunal o privada (la consulta de KPI-TER-04, ComponentLand). una cifra que quien llama no puede
//   leer sale null con missingFields, nunca cero
@Service
class TerritoryAggregates(
    private val gate: SpatialGate,
    private val records: Records,
    private val metadata: MetadataService,
    private val componentLand: ComponentLand,
    private val limits: TerritoryQueryProperties
) {
    suspend fun concentration(
        of: String?,
        projects: List<UUID>,
        period: Period,
        limit: Int
    ): Map<String, Any?> {
        val dateField =
            of?.let { EVENTS[it] }
                ?: throw Finding("INVALID_OF", "of debe ser uno de ${EVENTS.keys.sorted().joinToString(", ")}", "of", status = HttpStatus.BAD_REQUEST)
                    .toCodedException()
        val reads =
            listOf(
                GateRead(of, setOf("project", TerritoryNames.LOCALITY, TerritoryNames.COMMUNITY, dateField), principal = true),
                GateRead(TerritoryNames.LOCALITY, setOf("name", "location", "boundary")),
                GateRead(TerritoryNames.COMMUNITY, setOf("name", "territory"))
            )
        val parts = SpatialParts<Map<String, Any?>>()
        parts.add(
            gate.select(reads, { bind -> concentrationSql(this, bind, of, dateField, projects, period, limit) }) { row ->
                linkedMapOf(
                    "object" to row.get("kind", String::class.java),
                    "id" to row.get("zone", UUID::class.java).toString(),
                    "name" to row.get("name", String::class.java),
                    "point" to row.get("point", String::class.java)?.let { JSON.readValue(it, Map::class.java) },
                    "count" to row.get("n", java.lang.Long::class.java)?.toLong()
                )
            }
        )
        return linkedMapOf<String, Any?>("of" to of, "periodStart" to period.start.toString(), "periodEnd" to period.end.toString()) +
            parts.response("cells", parts.rows.take(limit), truncated = parts.rows.size > limit)
    }

    suspend fun summary(areaId: UUID): Map<String, Any?> {
        val area =
            records.find(TerritoryNames.AREA, areaId)
                ?: throw Finding("AREA_NOT_FOUND", "El área de influencia no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND)
                    .toCodedException()
        val readable = Readability(metadata)
        val missing = linkedSetOf<String>()
        val areaHa: BigDecimal? =
            if (readable.canRead("${TerritoryNames.AREA}.calculated_area_ha")) {
                Values.decimal(area.attributes["calculated_area_ha"])
            } else {
                missing += "${TerritoryNames.AREA}.calculated_area_ha"
                null
            }
        val byType = localitiesByType(areaId, readable, missing)
        var communalLand: Int? = null
        var privateLand: Int? = null
        when (val outcome = componentLand.rows(listOfNotNull(Records.uuid(area.attributes["project"])))) {
            is GateOutcome.Rows -> {
                communalLand =
                    outcome.rows
                        .filter { it.land == ComponentLand.COMMUNAL || it.land == COMMUNAL_PARCEL }
                        .map { it.component }
                        .distinct()
                        .size
                privateLand =
                    outcome.rows
                        .filter { it.land in PRIVATE }
                        .map { it.component }
                        .distinct()
                        .size
            }
            is GateOutcome.Unavailable -> missing += outcome.missingFields
        }
        val out = linkedMapOf<String, Any?>("available" to missing.isEmpty())
        if (missing.isNotEmpty()) out["missingFields"] = missing.toList()
        out["areaHa"] = areaHa
        out["localitiesByType"] = byType
        out["componentsOnCommunalLand"] = communalLand
        out["componentsOnPrivateLand"] = privateLand
        return out
    }

    // las localidades miembro de esta versión, por locality_type; null si quien llama no puede leer lo que hace falta
    private suspend fun localitiesByType(
        areaId: UUID,
        readable: Readability,
        missing: MutableSet<String>
    ): List<Map<String, Any?>>? {
        val absent =
            readable.missing(
                listOf("${TerritoryNames.MEMBER}.area_of_influence", "${TerritoryNames.MEMBER}.locality", "${TerritoryNames.LOCALITY}.locality_type")
            )
        if (absent.isNotEmpty()) {
            missing += absent
            return null
        }
        val localities =
            records
                .all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(TerritoryNames.AREA, listOf(areaId))), max = limits.maxRows)
                .mapNotNull { Records.uuid(it.attributes[TerritoryNames.LOCALITY]) }
                .distinct()
        if (localities.isEmpty()) return emptyList()
        return records
            .all(TerritoryNames.LOCALITY, ids = localities, max = limits.maxRows)
            .groupingBy { Values.text(it.attributes["locality_type"]) ?: NONE }
            .eachCount()
            .toSortedMap()
            .map { (type, count) -> mapOf("localityType" to type, "count" to count) }
    }

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()
        private const val NONE = "SIN DATO"
        private const val COMMUNAL_PARCEL = "COMUNAL"

        // las tenencias privadas: piden permiso de terceros. PROPIEDAD DE LA EMPRESA es de la empresa
        val PRIVATE = setOf("PROPIEDAD", "POSESION")

        // los eventos de concentration y su fecha (DATETIME)
        val EVENTS: Map<String, String> =
            mapOf(
                "engagement" to "held_at",
                "grievance" to "received_at",
                "inquiry" to "received_at",
                "stakeholder_request" to "received_at",
                "incident" to "occurred_at",
                "early_warning_signal" to "received_at"
            )

        internal fun concentrationSql(
            t: GateTables,
            bind: (Any) -> String,
            of: String,
            dateField: String,
            projects: List<UUID>,
            period: Period,
            limit: Int
        ): String {
            val l = TerritoryNames.LOCALITY
            val m = TerritoryNames.COMMUNITY
            val locality = "e.${t.column(of, l)}"
            val community = "e.${t.column(of, m)}"
            val date = "e.${t.column(of, dateField)}"
            val from = bind(OffsetDateTime.ofInstant(period.from(), ZoneOffset.UTC))
            val until = bind(OffsetDateTime.ofInstant(period.until(), ZoneOffset.UTC))
            val project = if (projects.isEmpty()) "" else " AND e.${t.column(of, "project")} = ANY(${bind(projects.toTypedArray())})"
            val point =
                "COALESCE(ST_Transform(l.${t.column(l, "location")}, 4326), ST_PointOnSurface(ST_Transform(l.${t.column(l, "boundary")}, 4326)), " +
                    "ST_PointOnSurface(ST_Transform(m.${t.column(m, "territory")}, 4326)))"
            return "SELECT k.kind AS kind, k.zone AS zone, k.n AS n, COALESCE(l.${t.column(l, "name")}, m.${t.column(m, "name")})::text AS name, " +
                "ST_AsGeoJSON($point, 9) AS point FROM (" +
                "SELECT CASE WHEN $locality IS NOT NULL THEN 'locality' ELSE 'community' END AS kind, COALESCE($locality, $community) AS zone, count(*) AS n " +
                "FROM ${t.table(of)} e WHERE $date >= $from AND $date < $until$project " +
                "AND ($locality IS NOT NULL OR $community IS NOT NULL) GROUP BY 1, 2) k " +
                "LEFT JOIN ${t.table(l)} l ON k.kind = 'locality' AND l.${t.column(l, SpatialGate.ID)} = k.zone " +
                "LEFT JOIN ${t.table(m)} m ON k.kind = 'community' AND m.${t.column(m, SpatialGate.ID)} = k.zone " +
                "ORDER BY k.n DESC, k.zone LIMIT ${bind(limit + 1)}"
        }
    }
}

// api.md §4 (F6)
@RestController
class TerritoryAggregatesController(
    private val aggregates: TerritoryAggregates,
    private val limits: TerritoryQueryProperties,
    private val clock: Clock
) {
    @GetMapping("/api/sgspe/territory/concentration")
    suspend fun concentration(request: ServerHttpRequest): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("of", "project", "periodStart", "periodEnd", "limit"))
        val period = QueryParams.period(params.getFirst("periodStart"), params.getFirst("periodEnd"), LocalDate.now(clock.withZone(LIMA)), limits)
        return aggregates.concentration(
            params.getFirst("of"),
            QueryParams.uuids("project", params["project"]),
            period,
            QueryParams.limit(params.getFirst("limit"), limits)
        )
    }

    @GetMapping("/api/sgspe/areas-of-influence/{id}/summary")
    suspend fun summary(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, emptySet())
        return aggregates.summary(id)
    }
}
