package sgspe.deliverable

import sgspe.territory.WmsLayersCatalog
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

enum class Shape { POLYGON, LINE, POINT }

// cómo se dibuja una capa en un mapa de informe, en las unidades de la paleta (píxeles a 90,7 dpi, como los SLD): GetMapRequest
// los escala a 300 dpi y MapComposer los usa en la leyenda
data class LayerStyle(
    val shape: Shape,
    val stroke: String?,
    val strokeWidth: Double = 1.0,
    val dash: List<Double> = emptyList(),
    val fill: String? = null,
    val fillOpacity: Double = 0.0,
    val mark: String = "circle",
    val size: Double = 7.0,
    val label: String? = null,
    val halo: String? = null
)

// los estilos de los mapas de informe (gis.md §7.2; decisión 6 del plan 8.2c): los de las capas publicadas salen de
// model/styles/palette.json (el build la copia al jar), los mismos colores de los SLD de GeoServer y del portal, con la forma
// de la geometría de su capa en layers.json; los de las capas en línea los fija SGSPE, porque esas capas no se publican
object ReportLayerStyles {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    private const val SURFACE = "#ffffff"
    private const val MAP_GROUND = "#eef0ea"
    private const val LYR_BOUNDARY = "#66727f"
    private const val LYR_WATER = "#3d7fbf"
    private const val LYR_WATER_LABEL = "#2f6aa3"
    private const val LYR_PROJECT = "#1d4f91"
    private const val LYR_ACCESS = "#0b1d3a"
    private const val LYR_COMMUNITY_LABEL = "#56621a"
    private const val LYR_CONCESSION = "#8a5a2b"

    val PUBLISHED: Map<String, LayerStyle> by lazy {
        val text =
            ReportLayerStyles::class.java.getResourceAsStream("/model/styles/palette.json")?.use { String(it.readAllBytes()) }
                ?: error("falta model/styles/palette.json en el jar")
        parse(text)
    }

    // los colores son tokens del tema claro de la paleta cartográfica (design-system.md §3.3; ReportLayerStylesTest): el agua en
    // lyr-water, el proyecto en lyr-project, sus componentes en lyr-access, la tenencia en oliva y siena, con el halo map-ground
    val INLINE: Map<String, LayerStyle> =
        mapOf(
            "LYR-06" to
                LayerStyle(
                    Shape.POLYGON,
                    LYR_COMMUNITY_LABEL,
                    1.0,
                    listOf(4.0, 2.0),
                    LYR_COMMUNITY_LABEL,
                    0.05,
                    label = LYR_COMMUNITY_LABEL,
                    halo = MAP_GROUND
                ),
            "LYR-08" to LayerStyle(Shape.LINE, LYR_WATER, 2.0, label = LYR_WATER_LABEL, halo = MAP_GROUND),
            "LYR-09-location" to LayerStyle(Shape.POINT, SURFACE, 1.0, fill = LYR_PROJECT, mark = "star", size = 12.0),
            "LYR-09-area" to LayerStyle(Shape.POLYGON, LYR_PROJECT, 1.5, listOf(3.0, 3.0)),
            "LYR-10" to LayerStyle(Shape.POINT, SURFACE, 1.0, fill = LYR_ACCESS, mark = "square", size = 10.0, label = LYR_ACCESS, halo = MAP_GROUND),
            "LYR-11" to LayerStyle(Shape.POLYGON, LYR_ACCESS, 1.0, fill = LYR_ACCESS, fillOpacity = 0.25),
            "LYR-11-point" to LayerStyle(Shape.POINT, SURFACE, 1.0, fill = LYR_ACCESS, mark = "triangle", size = 8.0),
            "LYR-13-AIDS" to LayerStyle(Shape.POLYGON, LYR_PROJECT, 2.5, fill = LYR_PROJECT, fillOpacity = 0.12),
            "LYR-13-AIIS" to LayerStyle(Shape.POLYGON, LYR_PROJECT, 2.0, listOf(8.0, 5.0)),
            "LYR-14" to LayerStyle(Shape.POLYGON, LYR_CONCESSION, 0.8, fill = LYR_CONCESSION, fillOpacity = 0.12),
            "LYR-15" to LayerStyle(Shape.POLYGON, LYR_BOUNDARY, 1.0, fill = LYR_BOUNDARY, fillOpacity = 0.20),
            "PROJECT-CONCESSION" to LayerStyle(Shape.POLYGON, LYR_CONCESSION, 2.5, label = LYR_CONCESSION, halo = MAP_GROUND)
        )

    fun parse(json: String): Map<String, LayerStyle> {
        val geometries = WmsLayersCatalog.ALL.associate { it.style to it.geometry }
        return JSON
            .readTree(json)
            .path("styles")
            .properties()
            .associate { (name, node) ->
                val shape =
                    when (geometries[name]) {
                        "location" -> Shape.POINT
                        "trace" -> Shape.LINE
                        else -> Shape.POLYGON
                    }
                name to
                    LayerStyle(
                        shape = shape,
                        stroke = color(node, "stroke"),
                        strokeWidth = node.path("strokeWidth").asDouble(1.0),
                        dash = node.path("strokeDasharray").values().map { it.asDouble() },
                        fill = color(node, "fill"),
                        fillOpacity = node.path("fillOpacity").asDouble(if (shape == Shape.POINT) 1.0 else 0.0),
                        mark = text(node, "mark") ?: "circle",
                        size = node.path("size").asDouble(7.0),
                        label = color(node, "label"),
                        halo = color(node, "halo")
                    )
            }
    }

    private fun text(
        node: JsonNode,
        field: String
    ): String? = node.path(field).takeIf { !it.isNull && !it.isMissingNode }?.asString()

    private val COLOR = Regex("#[0-9a-fA-F]{6}")

    // los colores van sin escapar al SLD: solo #rrggbb, o el estilo no carga
    private fun color(
        node: JsonNode,
        field: String
    ): String? =
        text(node, field)?.also {
            require(COLOR.matches(it)) { "El color '$it' del campo $field de la paleta no es #rrggbb" }
        }
}
