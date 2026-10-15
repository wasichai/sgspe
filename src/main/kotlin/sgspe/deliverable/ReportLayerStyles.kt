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

    val PUBLISHED: Map<String, LayerStyle> by lazy {
        val text =
            ReportLayerStyles::class.java.getResourceAsStream("/model/styles/palette.json")?.use { String(it.readAllBytes()) }
                ?: error("falta model/styles/palette.json en el jar")
        parse(text)
    }

    val INLINE: Map<String, LayerStyle> =
        mapOf(
            "LYR-06" to LayerStyle(Shape.POLYGON, "#6b8e23", 1.0, listOf(4.0, 2.0), "#6b8e23", 0.06, label = "#3d5214", halo = "#ffffff"),
            "LYR-08" to LayerStyle(Shape.LINE, "#2f6fb0", 1.2, label = "#1d4470", halo = "#ffffff"),
            "LYR-09-location" to LayerStyle(Shape.POINT, "#ffffff", 1.0, fill = "#1d3557", mark = "star", size = 12.0),
            "LYR-09-area" to LayerStyle(Shape.POLYGON, "#1d3557", 1.6, listOf(8.0, 4.0)),
            "LYR-10" to LayerStyle(Shape.POINT, "#ffffff", 0.8, fill = "#d62828", mark = "circle", size = 6.0, label = "#7a1010", halo = "#ffffff"),
            "LYR-11" to LayerStyle(Shape.POLYGON, "#7f3c8d", 1.0, fill = "#7f3c8d", fillOpacity = 0.25),
            "LYR-11-point" to LayerStyle(Shape.POINT, "#ffffff", 0.8, fill = "#7f3c8d", mark = "triangle", size = 8.0),
            "LYR-13-AIDS" to LayerStyle(Shape.POLYGON, "#e76f51", 2.0, fill = "#e76f51", fillOpacity = 0.10),
            "LYR-13-AIIS" to LayerStyle(Shape.POLYGON, "#f4a261", 1.6, listOf(6.0, 3.0), "#f4a261", 0.06),
            "LYR-14" to LayerStyle(Shape.POLYGON, "#8d6e63", 0.8, fill = "#bcaaa4", fillOpacity = 0.25),
            "LYR-15" to LayerStyle(Shape.POLYGON, "#5d4037", 1.0, fill = "#5d4037", fillOpacity = 0.30),
            "PROJECT-CONCESSION" to LayerStyle(Shape.POLYGON, "#b8860b", 2.2, label = "#5b3f00", halo = "#ffffff")
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
                        stroke = text(node, "stroke"),
                        strokeWidth = node.path("strokeWidth").asDouble(1.0),
                        dash = node.path("strokeDasharray").values().map { it.asDouble() },
                        fill = text(node, "fill"),
                        fillOpacity = node.path("fillOpacity").asDouble(if (shape == Shape.POINT) 1.0 else 0.0),
                        mark = text(node, "mark") ?: "circle",
                        size = node.path("size").asDouble(7.0),
                        label = text(node, "label"),
                        halo = text(node, "halo")
                    )
            }
    }

    private fun text(
        node: JsonNode,
        field: String
    ): String? = node.path(field).takeIf { !it.isNull && !it.isMissingNode }?.asString()
}
