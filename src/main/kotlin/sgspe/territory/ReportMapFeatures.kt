// la Tarea 7 agrega aquí el resto de las clases del archivo; mientras solo hay MapExtent, el nombre no coincide
@file:Suppress("ktlint:standard:filename")

package sgspe.territory

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
