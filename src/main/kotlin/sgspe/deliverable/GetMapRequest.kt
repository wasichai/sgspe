package sgspe.deliverable

import sgspe.territory.MapFeature
import java.util.Locale

// una capa ya resuelta para el GetMap: publicada (su nombre <tabla física>__<columna> y las columnas de su etiqueta y su
// filtro) o en línea (sus elementos ya leídos)
sealed interface GetMapLayer {
    val legend: String
    val style: LayerStyle

    data class Named(
        val name: String,
        override val legend: String,
        override val style: LayerStyle,
        val labelColumn: String?,
        val filter: Pair<String, String>?
    ) : GetMapLayer

    data class Inline(
        val name: String,
        override val legend: String,
        override val style: LayerStyle,
        val features: List<MapFeature>
    ) : GetMapLayer
}

// el ogc:GetMap por POST XML (especificación de F8 §2.2.11 y §3.7 paso 3, ASM-134): un StyledLayerDescriptor con las capas
// publicadas por nombre y las demás como UserLayer con InlineFeature (GML 2 con srsName de URL, como exigió el spike de
// ADR-0015), cada una con un UserStyle armado desde la paleta y escalado a 300 dpi (decisión 6 del plan 8.2c); el recuadro en
// el CRS de trabajo, PNG opaco con fondo blanco y el tamaño del marco. todo texto se escapa; nada viene del cliente
object GetMapRequest {
    // GeoServer dibuja con su píxel estándar de 0,28 mm (90,7 dpi): a 300 dpi los grosores, tamaños y textos se multiplican
    const val SCALE = 300.0 / 90.714
    private const val LABEL = "label"
    private const val LABEL_PT = 9.0

    fun srsName(srid: Int): String = "http://www.opengis.net/gml/srs/epsg.xml#$srid"

    fun number(value: Double): String = "%.2f".format(Locale.ROOT, value)

    fun xml(
        frame: MapFrame,
        srid: Int,
        layers: List<GetMapLayer>
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<ogc:GetMap xmlns:ogc="http://www.opengis.net/ows" xmlns:gml="http://www.opengis.net/gml" version="1.2.0" service="WMS">""")
            append("""<StyledLayerDescriptor version="1.0.0">""")
            for (layer in layers) {
                when (layer) {
                    is GetMapLayer.Named -> {
                        append("<NamedLayer><Name>").append(escape(layer.name)).append("</Name>")
                        userStyle(layer.style, layer.labelColumn, layer.filter)
                        append("</NamedLayer>")
                    }
                    is GetMapLayer.Inline -> {
                        append("<UserLayer><Name>").append(escape(layer.name)).append("</Name><InlineFeature><FeatureCollection>")
                        for (feature in layer.features) {
                            append("<featureMember><gml:_Feature><geometryProperty>").append(feature.gml).append("</geometryProperty>")
                            append("<$LABEL>").append(escape(feature.label.orEmpty())).append("</$LABEL>")
                            append("</gml:_Feature></featureMember>")
                        }
                        append("</FeatureCollection></InlineFeature>")
                        userStyle(layer.style, LABEL.takeIf { layer.features.any { !it.label.isNullOrBlank() } }, null)
                        append("</UserLayer>")
                    }
                }
            }
            append("</StyledLayerDescriptor>")
            val b = frame.bbox
            append("""<BoundingBox srsName="${srsName(srid)}">""")
            append("<gml:coord><gml:X>${number(b.minX)}</gml:X><gml:Y>${number(b.minY)}</gml:Y></gml:coord>")
            append("<gml:coord><gml:X>${number(b.maxX)}</gml:X><gml:Y>${number(b.maxY)}</gml:Y></gml:coord>")
            append("</BoundingBox>")
            append("<Output><Format>image/png</Format><Size><Width>${frame.widthPx}</Width><Height>${frame.heightPx}</Height></Size>")
            append("<Transparent>false</Transparent><BGcolor>#FFFFFF</BGcolor></Output>")
            append("<Exceptions>application/vnd.ogc.se_xml</Exceptions>")
            append("</ogc:GetMap>")
        }

    private fun StringBuilder.userStyle(
        style: LayerStyle,
        label: String?,
        filter: Pair<String, String>?
    ) {
        append("<UserStyle><FeatureTypeStyle><Rule>")
        filter?.let { (field, value) ->
            append("""<Filter xmlns="http://www.opengis.net/ogc"><PropertyIsEqualTo><PropertyName>""")
            append(escape(field)).append("</PropertyName><Literal>").append(escape(value)).append("</Literal></PropertyIsEqualTo></Filter>")
        }
        when (style.shape) {
            Shape.POLYGON -> {
                append("<PolygonSymbolizer>")
                fill(style)
                stroke(style)
                append("</PolygonSymbolizer>")
            }
            Shape.LINE -> {
                append("<LineSymbolizer>")
                stroke(style)
                append("</LineSymbolizer>")
            }
            Shape.POINT -> {
                append("<PointSymbolizer><Graphic><Mark><WellKnownName>").append(escape(style.mark)).append("</WellKnownName>")
                fill(style)
                stroke(style)
                append("</Mark><Size>${number(style.size * SCALE)}</Size></Graphic></PointSymbolizer>")
            }
        }
        if (label != null && style.label != null) {
            append("<TextSymbolizer><Label><PropertyName>").append(escape(label)).append("</PropertyName></Label>")
            append("""<Font><CssParameter name="font-family">Liberation Sans</CssParameter>""")
            append("""<CssParameter name="font-size">${number(LABEL_PT * SCALE)}</CssParameter></Font>""")
            style.halo?.let { append("""<Halo><Radius>${number(1.0 * SCALE)}</Radius><Fill><CssParameter name="fill">$it</CssParameter></Fill></Halo>""") }
            append("""<Fill><CssParameter name="fill">${style.label}</CssParameter></Fill></TextSymbolizer>""")
        }
        append("</Rule></FeatureTypeStyle></UserStyle>")
    }

    private fun StringBuilder.fill(style: LayerStyle) {
        val color = style.fill ?: return
        append("""<Fill><CssParameter name="fill">$color</CssParameter>""")
        append("""<CssParameter name="fill-opacity">${number(style.fillOpacity)}</CssParameter></Fill>""")
    }

    private fun StringBuilder.stroke(style: LayerStyle) {
        val color = style.stroke ?: return
        append("""<Stroke><CssParameter name="stroke">$color</CssParameter>""")
        append("""<CssParameter name="stroke-width">${number(style.strokeWidth * SCALE)}</CssParameter>""")
        if (style.dash.isNotEmpty()) {
            append("""<CssParameter name="stroke-dasharray">${style.dash.joinToString(" ") { number(it * SCALE) }}</CssParameter>""")
        }
        append("</Stroke>")
    }

    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
