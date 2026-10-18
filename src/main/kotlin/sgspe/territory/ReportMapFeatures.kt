package sgspe.territory

import io.r2dbc.spi.Readable
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// un rectángulo en metros del CRS de trabajo (gis.md §7.3): la extensión de unas geometrías o el recuadro de un mapa
data class MapExtent(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY

    fun union(other: MapExtent): MapExtent = MapExtent(min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY))
}

// las geometrías de los mapas de informe (especificación de F8 §3.7 pasos 1 y 2; decisión 7 del plan 8.2c), como la
// plataforma de una organización, por SpatialGate.selectAsPlatform (la única puerta de SQL propio; solo fuera de una
// petición, ArchitectureTest): la extensión de unos registros en el CRS de trabajo y los elementos de una capa en línea dentro
// de un recuadro, en GML 2 simplificado a `tolerance` metros, a lo más max + 1 (quien llama decide si se pasó). lo que el
// tenant no tiene sale vacío. también da la microcuenca de las localidades de 4.2 de T1 (#80)
@Component
class ReportMapFeatures(
    private val gate: SpatialGate
) {
    suspend fun extent(
        organizationId: UUID,
        srid: Int,
        refs: List<GeometryRef>
    ): MapExtent? {
        var out: MapExtent? = null
        for (ref in refs.filter { it.ids.isNotEmpty() }) {
            val outcome =
                gate.selectAsPlatform(
                    organizationId,
                    listOf(GateRead(ref.objectName, setOf(ref.geometry))),
                    { bind -> extentSql(this, bind, ref, srid) }
                ) { row ->
                    listOf(double(row, "min_x"), double(row, "min_y"), double(row, "max_x"), double(row, "max_y"))
                }
            val corners = (outcome as? GateOutcome.Rows)?.rows?.singleOrNull() ?: continue
            if (corners.any { it == null }) continue
            val (minX, minY, maxX, maxY) = corners.map { it!! }
            val found = MapExtent(minX, minY, maxX, maxY)
            out = out?.union(found) ?: found
        }
        return out
    }

    suspend fun features(
        organizationId: UUID,
        srid: Int,
        query: MapLayerQuery,
        bbox: MapExtent,
        tolerance: Double,
        max: Int
    ): List<MapFeature> {
        val project = "project".takeIf { query.project != null }
        val current = "superseded".takeIf { query.currentOnly }
        val fields = setOf(query.geometry) + query.equals.keys + listOfNotNull(query.label, project, current)
        val outcome =
            gate.selectAsPlatform(
                organizationId,
                listOf(GateRead(query.objectName, fields)),
                { bind -> featuresSql(this, bind, query, srid, bbox, tolerance, max) }
            ) { row ->
                MapFeature(srsName(row.get("gml", String::class.java).orEmpty(), srid), row.get("label", String::class.java))
            }
        return (outcome as? GateOutcome.Rows)?.rows.orEmpty()
    }

    // la microcuenca de cada localidad dada (4.2 de T1, #80): el nombre de la environmental_feature MICROCUENCA cuyo boundary
    // cubre su location. una localidad sin microcuenca, sin location o que el tenant no tiene queda fuera; si cae en dos (en
    // el borde común), la primera por nombre. la llama solo ReportSources (JOB-05, ArchitectureTest)
    suspend fun microbasins(
        organizationId: UUID,
        localities: List<UUID>
    ): Map<UUID, String> {
        if (localities.isEmpty()) return emptyMap()
        val outcome =
            gate.selectAsPlatform(
                organizationId,
                listOf(GateRead(LOCALITY, setOf("location")), GateRead(FEATURE, setOf("name", "feature_type", "boundary"))),
                { bind -> microbasinsSql(this, bind, localities) }
            ) { row -> row.get("locality", UUID::class.java)!! to row.get("name", String::class.java).orEmpty() }
        val rows = (outcome as? GateOutcome.Rows)?.rows.orEmpty()
        return rows.filter { it.second.isNotBlank() }.groupBy({ it.first }, { it.second }).mapValues { (_, names) -> names.first() }
    }

    companion object {
        private const val LOCALITY = "locality"
        private const val FEATURE = "environmental_feature"
        const val MICROBASIN = "MICROCUENCA"

        internal fun microbasinsSql(
            t: GateTables,
            bind: (Any) -> String,
            localities: List<UUID>
        ): String {
            val location = "l.${t.column(LOCALITY, "location")}"
            val boundary = "f.${t.column(FEATURE, "boundary")}"
            val name = "f.${t.column(FEATURE, "name")}::text"
            return "SELECT l.${t.column(LOCALITY, SpatialGate.ID)} AS locality, $name AS name " +
                "FROM ${t.table(LOCALITY)} l JOIN ${t.table(FEATURE)} f " +
                "ON ST_Covers($boundary, ST_Transform($location, ${t.srid(FEATURE, "boundary")})) " +
                "WHERE l.${t.column(LOCALITY, SpatialGate.ID)} = ANY(${bind(localities.toTypedArray())}) " +
                "AND f.${t.column(FEATURE, "feature_type")}::text = ${bind(MICROBASIN)} " +
                "AND $location IS NOT NULL AND $boundary IS NOT NULL ORDER BY 1, 2"
        }

        private fun double(
            row: Readable,
            name: String
        ): Double? = (row.get(name) as? Number)?.toDouble()

        // ST_AsGML escribe srsName="EPSG:<srid>"; GeoServer pide la URL de epsg.xml (spike de ADR-0015)
        fun srsName(
            gml: String,
            srid: Int
        ): String = gml.replace("srsName=\"EPSG:$srid\"", "srsName=\"http://www.opengis.net/gml/srs/epsg.xml#$srid\"")

        internal fun extentSql(
            t: GateTables,
            bind: (Any) -> String,
            ref: GeometryRef,
            srid: Int
        ): String {
            val o = ref.objectName
            val column = "x.${t.column(o, ref.geometry)}"
            return "SELECT ST_XMin(e) AS min_x, ST_YMin(e) AS min_y, ST_XMax(e) AS max_x, ST_YMax(e) AS max_y FROM (" +
                "SELECT ST_Extent(ST_Transform($column, $srid)) AS e FROM ${t.table(o)} x " +
                "WHERE x.${t.column(o, SpatialGate.ID)} = ANY(${bind(ref.ids.toTypedArray())}) AND $column IS NOT NULL) s"
        }

        internal fun featuresSql(
            t: GateTables,
            bind: (Any) -> String,
            q: MapLayerQuery,
            srid: Int,
            bbox: MapExtent,
            tolerance: Double,
            limit: Int
        ): String {
            val o = q.objectName
            val column = "x.${t.column(o, q.geometry)}"
            val own = t.srid(o, q.geometry)
            val envelope = "ST_Transform(ST_MakeEnvelope(${bind(bbox.minX)}, ${bind(bbox.minY)}, ${bind(bbox.maxX)}, ${bind(bbox.maxY)}, $srid), $own)"
            val terms = mutableListOf("$column IS NOT NULL", "ST_Intersects($column, $envelope)")
            q.project?.let { terms += "x.${t.column(o, "project")} = ${bind(it)}" }
            q.ids?.let { terms += "x.${t.column(o, SpatialGate.ID)} = ANY(${bind(it.toTypedArray())})" }
            for ((field, values) in q.equals.toSortedMap()) terms += "x.${t.column(o, field)}::text = ANY(${bind(values.sorted().toTypedArray())})"
            if (q.currentOnly) terms += "x.${t.column(o, "superseded")} IS NOT TRUE"
            val label = q.label?.let { "x.${t.column(o, it)}::text" } ?: "NULL::text"
            return "SELECT ST_AsGML(2, ST_SimplifyPreserveTopology(ST_Transform($column, $srid), ${bind(tolerance)}), 1, 0) AS gml, $label AS label " +
                "FROM ${t.table(o)} x WHERE ${terms.joinToString(" AND ")} ORDER BY x.${t.column(o, SpatialGate.ID)} LIMIT ${bind(limit + 1)}"
        }
    }
}

// las geometrías de unos registros que encuadran un mapa
data class GeometryRef(
    val objectName: String,
    val geometry: String,
    val ids: List<UUID>
)

// una capa en línea de un mapa de informe: un objeto y su geometría, igualdades por campo (como texto), ids, el proyecto, solo
// los componentes vigentes (superseded no verdadero) y el campo de su etiqueta (nunca un dato personal: name o code)
data class MapLayerQuery(
    val objectName: String,
    val geometry: String,
    val equals: Map<String, Set<String>> = emptyMap(),
    val ids: List<UUID>? = null,
    val project: UUID? = null,
    val label: String? = null,
    val currentOnly: Boolean = false
)

// un elemento en línea: su GML 2 en el CRS de trabajo, con el srsName de URL que pide GeoServer, y su etiqueta. `gml` se inserta
// SIN escapar en el GetMap: debe venir solo de ST_AsGML de PostGIS (Tarea 7), nunca de texto de una persona
data class MapFeature(
    val gml: String,
    val label: String?
) {
    init {
        require(gml.startsWith("<gml:")) { "El GML de un elemento debe empezar por <gml:" }
        require(FORBIDDEN.none { it in gml }) { "El GML de un elemento no puede cerrar ni abrir otros elementos del GetMap" }
    }

    private companion object {
        // lo que sacaría al GML de su geometryProperty o metería otro elemento al SLD
        val FORBIDDEN =
            listOf(
                "<!",
                "<?",
                "</geometryProperty",
                "</gml:_Feature",
                "</featureMember",
                "</FeatureCollection",
                "</InlineFeature",
                "</UserLayer",
                "<label",
                "<UserStyle",
                "<sld:"
            )
    }
}
