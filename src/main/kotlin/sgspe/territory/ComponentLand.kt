package sgspe.territory

import io.r2dbc.spi.Readable
import org.springframework.stereotype.Service
import sgspe.common.Records
import java.util.UUID

// un componente vigente que cruza tierra de terceros: TERRITORIO COMUNAL (community.territory) o la tenencia del predio
// (land_parcel.tenure_type)
data class ComponentLandRow(
    val component: UUID,
    val project: UUID,
    val componentType: String?,
    val land: String,
    val landId: UUID
)

// la consulta de KPI-TER-04 y de summary (especificación de F6 §3.2, caso 15 de gis.md §5.3): los project_component vigentes
// (superseded no verdadero) de los proyectos que intersecan community.territory o land_parcel.boundary, por la compuerta.
// ningún objeto es principal: un tablero o un resumen sin permiso sale no disponible, nunca 403. no se recorta: más de
// max-rows cruces es 400 TOO_MANY_RECORDS (la cifra no puede salir parcial)
@Service
class ComponentLand(
    private val gate: SpatialGate,
    private val limits: TerritoryQueryProperties
) {
    // `platform`: la organización cuando lee el lector de la plataforma de los KPI (DashboardData); null, la persona
    suspend fun rows(
        projects: Collection<UUID>,
        platform: UUID? = null
    ): GateOutcome<ComponentLandRow> {
        if (projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val query: GateTables.((Any) -> String) -> String = { bind -> sql(this, bind, projects, limits.maxRows) }
        val map: (Readable) -> ComponentLandRow = { row ->
            ComponentLandRow(
                row.get("component", UUID::class.java)!!,
                row.get("project", UUID::class.java)!!,
                row.get("component_type", String::class.java),
                tenureOf(row.get("land", String::class.java)),
                row.get("land_id", UUID::class.java)!!
            )
        }
        val outcome = if (platform == null) gate.select(READS, query, map) else gate.selectAsPlatform(platform, READS, query, map)
        if (outcome is GateOutcome.Rows && outcome.rows.size > limits.maxRows) throw Records.tooMany(TerritoryNames.COMPONENT, limits.maxRows)
        return outcome
    }

    companion object {
        const val COMMUNAL = "TERRITORIO COMUNAL"
        const val NO_TENURE = "SIN TENENCIA"

        // tenure_type no es requerido: un predio sin tenencia se cuenta aparte, no revienta (ni es comunal ni privado)
        internal fun tenureOf(raw: String?): String = raw ?: NO_TENURE

        private val COMPONENT_FIELDS = setOf("project", "component_type", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES

        val READS: List<GateRead> =
            listOf(
                GateRead(TerritoryNames.COMPONENT, COMPONENT_FIELDS),
                GateRead(TerritoryNames.COMMUNITY, setOf("territory")),
                GateRead(TerritoryNames.PARCEL, setOf("tenure_type", "boundary"))
            )

        internal fun sql(
            t: GateTables,
            bind: (Any) -> String,
            projects: Collection<UUID>,
            limit: Int
        ): String {
            val c = TerritoryNames.COMPONENT

            fun shape(srid: Int) = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, srid)

            fun vigentes() = "c.${t.column(c, "project")} = ANY(${bind(projects.toTypedArray())}) AND c.${t.column(c, "superseded")} IS NOT TRUE"
            val head = "SELECT c.${t.column(
                c,
                SpatialGate.ID
            )} AS component, c.${t.column(c, "project")} AS project, c.${t.column(c, "component_type")}::text AS component_type"
            val territory = "m.${t.column(TerritoryNames.COMMUNITY, "territory")}"
            val boundary = "p.${t.column(TerritoryNames.PARCEL, "boundary")}"
            return "$head, CAST(${bind(COMMUNAL)} AS text) AS land, m.${t.column(TerritoryNames.COMMUNITY, SpatialGate.ID)} AS land_id " +
                "FROM ${t.table(c)} c JOIN ${t.table(TerritoryNames.COMMUNITY)} m " +
                "ON (ST_Intersects(${shape(
                    t.srid(TerritoryNames.COMMUNITY, "territory")
                )}, $territory) AND NOT ST_Touches(${shape(t.srid(TerritoryNames.COMMUNITY, "territory"))}, $territory)) WHERE ${vigentes()} " +
                "UNION ALL $head, p.${t.column(
                    TerritoryNames.PARCEL,
                    "tenure_type"
                )}::text AS land, p.${t.column(TerritoryNames.PARCEL, SpatialGate.ID)} AS land_id " +
                "FROM ${t.table(c)} c JOIN ${t.table(TerritoryNames.PARCEL)} p " +
                "ON (ST_Intersects(${shape(
                    t.srid(TerritoryNames.PARCEL, "boundary")
                )}, $boundary) AND NOT ST_Touches(${shape(t.srid(TerritoryNames.PARCEL, "boundary"))}, $boundary)) WHERE ${vigentes()} " +
                "LIMIT ${bind(limit + 1)}"
        }
    }
}
