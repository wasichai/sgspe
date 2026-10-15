// la Tarea 7 agrega aquí el resto de las clases del archivo; mientras solo hay MapExtent, el nombre no coincide
@file:Suppress("ktlint:standard:filename")

package sgspe.territory

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
