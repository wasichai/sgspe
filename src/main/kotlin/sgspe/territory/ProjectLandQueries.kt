package sgspe.territory

import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// un componente que cruza un predio o un territorio comunal, con la longitud de su trazo o el área de su huella dentro de él,
// en el working_crs del proyecto
data class LandCrossing(
    val component: UUID,
    val componentCode: String?,
    val componentType: String?,
    val land: UUID,
    val landCode: String?,
    val landKind: String?,
    val lengthM: BigDecimal?,
    val areaHa: BigDecimal?
)

// componentes × predios (caso 10 de gis.md §5.3: quién debe dar el permiso de uso) y × territorio comunal (caso 11: qué
// comunidad debe autorizar en asamblea), por la compuerta. los titulares de land_right se leen después por Records con los
// permisos de campo de quien llama: sin land_right.holder_person, holders es null y missingFields lo dice
@Service
class ProjectLandQueries(
    private val gate: SpatialGate,
    private val frames: ProjectFrames,
    private val records: Records,
    private val metadata: MetadataService,
    private val limits: TerritoryQueryProperties,
    private val clock: Clock
) {
    suspend fun parcels(
        projectId: UUID,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<LandCrossing>()
        parts.add(
            gate.select(PARCEL_READS, { bind -> crossingSql(this, bind, frame, TerritoryNames.PARCEL, "boundary", "code", "tenure_type", limit) }, ::crossing)
        )
        val crossings = parts.rows.take(limit)
        val holders = holders(crossings.map { it.land }.distinct(), parts)
        val rows =
            crossings.map { c ->
                linkedMapOf(
                    "component" to component(c),
                    "parcel" to mapOf("id" to c.land.toString(), "code" to c.landCode, "tenureType" to c.landKind),
                    "lengthM" to c.lengthM,
                    "areaHa" to c.areaHa,
                    "holders" to holders?.let { it[c.land].orEmpty() }
                )
            }
        return parts.response("rows", rows, truncated = parts.rows.size > limit)
    }

    suspend fun communalLand(
        projectId: UUID,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<LandCrossing>()
        parts.add(
            gate.select(COMMUNAL_READS, { bind -> crossingSql(this, bind, frame, TerritoryNames.COMMUNITY, "territory", "name", null, limit) }, ::crossing)
        )
        val rows =
            parts.rows.take(limit).map { c ->
                linkedMapOf(
                    "component" to component(c),
                    "community" to mapOf("id" to c.land.toString(), "name" to c.landCode),
                    "lengthM" to c.lengthM,
                    "areaHa" to c.areaHa
                )
            }
        return parts.response("rows", rows, truncated = parts.rows.size > limit)
    }

    // los titulares de cada predio: personas y organizaciones de sus land_right, con su nombre si quien llama puede leerlo
    private suspend fun holders(
        parcels: List<UUID>,
        parts: SpatialParts<*>
    ): Map<UUID, List<Map<String, Any?>>>? {
        val readable = Readability(metadata)
        val missing = readable.missing(HOLDER_FIELDS)
        if (missing.isNotEmpty()) {
            parts.missing(*missing.toTypedArray())
            return null
        }
        if (parcels.isEmpty()) return emptyMap()
        val rights = records.all(TerritoryNames.RIGHT, criteria = listOf(Criteria.anyOf("land_parcel", parcels)), max = limits.maxRows, field = "limit")
        val people = names("person", rights.mapNotNull { Values.text(it.attributes["holder_person"]) }, readable, listOf("given_names", "surnames"))
        val organizations = names("organization", rights.mapNotNull { Values.text(it.attributes["holder_organization"]) }, readable, listOf("name"))
        return rights
            .groupBy { UUID.fromString(Values.text(it.attributes["land_parcel"])) }
            .mapValues { (_, list) ->
                currentHolders(list, today(clock)).map { (kind, id) ->
                    mapOf(
                        "object" to kind,
                        "id" to id,
                        "name" to (if (kind == "person") people else organizations)[id]
                    )
                }
            }
    }

    // el nombre de cada registro, de los campos que quien llama puede leer; ninguno si no lee el objeto o esos campos
    private suspend fun names(
        objectName: String,
        ids: List<String>,
        readable: Readability,
        fields: List<String>
    ): Map<String, String> {
        if (ids.isEmpty() || !fields.all { readable.canRead("$objectName.$it") }) return emptyMap()
        return records
            .all(objectName, ids = ids.distinct().map(UUID::fromString), max = limits.maxRows, field = "limit")
            .associate { record -> record.id to fields.mapNotNull { Values.text(record.attributes[it]) }.joinToString(" ") }
            .filterValues { it.isNotBlank() }
    }

    private fun component(c: LandCrossing): Map<String, Any?> =
        mapOf(
            "id" to c.component.toString(),
            "code" to c.componentCode,
            "componentType" to c.componentType
        )

    private fun crossing(row: io.r2dbc.spi.Readable): LandCrossing =
        LandCrossing(
            row.get("component", UUID::class.java)!!,
            row.get("component_code", String::class.java),
            row.get("component_type", String::class.java),
            row.get("land", UUID::class.java)!!,
            row.get("land_code", String::class.java),
            row.get("land_kind", String::class.java),
            row.get("length_m", BigDecimal::class.java),
            row.get("area_ha", BigDecimal::class.java)
        )

    companion object {
        private val COMPONENT_READ =
            GateRead(TerritoryNames.COMPONENT, setOf("code", "component_type", "project", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES, principal = true)
        val PARCEL_READS = listOf(COMPONENT_READ, GateRead(TerritoryNames.PARCEL, setOf("code", "tenure_type", "boundary"), principal = true))
        val COMMUNAL_READS = listOf(COMPONENT_READ, GateRead(TerritoryNames.COMMUNITY, setOf("name", "territory"), principal = true))
        private val HOLDER_FIELDS = listOf("land_right.land_parcel", "land_right.holder_person", "land_right.holder_organization", "land_right.end_date")

        // los titulares vigentes de un predio: los derechos sin end_date o con end_date de hoy en adelante (hoy en Lima), cada
        // persona u organización una sola vez. devuelve (objeto, id)
        // hoy en Lima, como en las demás consultas: a las 20:00 de Lima ya es mañana en UTC
        internal fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(LIMA))

        internal fun currentHolders(
            rights: List<wasichai.core.data.RecordResponse>,
            today: LocalDate
        ): List<Pair<String, String>> =
            rights
                .filter { right -> Values.text(right.attributes["end_date"])?.let { LocalDate.parse(it) >= today } ?: true }
                .flatMap { right ->
                    listOfNotNull(
                        Values.text(right.attributes["holder_person"])?.let { "person" to it },
                        Values.text(right.attributes["holder_organization"])?.let { "organization" to it }
                    )
                }.distinct()

        // los componentes vigentes del proyecto que cruzan `geometry` de `land`, con la longitud del trazo y el área de la huella
        // dentro de ella en el working_crs; `kind` es la columna que se devuelve como land_kind (la tenencia), o ninguna
        internal fun crossingSql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            land: String,
            geometry: String,
            code: String,
            kind: String?,
            limit: Int
        ): String {
            val c = TerritoryNames.COMPONENT
            val srid = bind(frame.srid)
            val target = "x.${t.column(land, geometry)}"
            val shape = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, t.srid(land, geometry))
            val trace = "c.${t.column(c, "trace")}"
            val footprint = "c.${t.column(c, "footprint")}"
            val kindColumn = kind?.let { "x.${t.column(land, it)}::text" } ?: "NULL::text"
            return "SELECT c.${t.column(c, SpatialGate.ID)} AS component, c.${t.column(c, "code")} AS component_code, " +
                "c.${t.column(c, "component_type")}::text AS component_type, x.${t.column(land, SpatialGate.ID)} AS land, " +
                "x.${t.column(land, code)}::text AS land_code, $kindColumn AS land_kind, " +
                "CASE WHEN $trace IS NOT NULL THEN ${SpatialSql.lengthM(trace, target, srid)} END AS length_m, " +
                "CASE WHEN $footprint IS NOT NULL THEN ${SpatialSql.areaHa(footprint, target, srid)} END AS area_ha " +
                "FROM ${t.table(c)} c JOIN ${t.table(land)} x ON (ST_Intersects($shape, $target) AND NOT ST_Touches($shape, $target)) " +
                "WHERE c.${t.column(c, "project")} = ${bind(frame.id)} AND c.${t.column(c, "superseded")} IS NOT TRUE " +
                "ORDER BY 2, 5, 1, 4 LIMIT ${bind(limit + 1)}"
        }
    }
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory/projects")
class ProjectLandQueriesController(
    private val queries: ProjectLandQueries,
    private val limits: TerritoryQueryProperties
) {
    @GetMapping("/{id}/component-parcels")
    suspend fun parcels(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("limit"))
        return queries.parcels(id, QueryParams.limit(request.queryParams.getFirst("limit"), limits))
    }

    @GetMapping("/{id}/component-communal-land")
    suspend fun communalLand(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("limit"))
        return queries.communalLand(id, QueryParams.limit(request.queryParams.getFirst("limit"), limits))
    }
}
