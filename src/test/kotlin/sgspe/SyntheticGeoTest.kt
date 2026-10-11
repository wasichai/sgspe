package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.cos

// las geometrías sintéticas de F6ApiTest (RQ-GIS-021): metros a grados cerca de ORIGIN, anillos cerrados en sentido
// antihorario (RFC 7946) y un MultiPolygon de una parte. es unitaria: no levanta la app
class SyntheticGeoTest {
    private val lon = SyntheticGeo.ORIGIN.first
    private val lat = SyntheticGeo.ORIGIN.second

    @Suppress("UNCHECKED_CAST")
    private fun ringOf(polygon: Map<String, Any>): List<List<Double>> = (polygon["coordinates"] as List<List<List<Double>>>).single()

    @Test
    fun `ORIGIN cae en la zona UTM 17 del hemisferio sur`() {
        assertEquals(17, ((lon + 180) / 6).toInt() + 1)
        assertTrue(lat < 0)
    }

    @Test
    fun `offset mueve metros al norte y al este`() {
        val (sameLon, north) = SyntheticGeo.offset(lon, lat, 0.0, 1000.0)
        assertEquals(lon, sameLon, 0.0)
        assertEquals(1000.0 / 111_320.0, north - lat, 1e-12)
        val (east, sameLat) = SyntheticGeo.offset(lon, lat, 1000.0, 0.0)
        assertEquals(1000.0 / (111_320.0 * cos(Math.toRadians(lat))), east - lon, 1e-12)
        assertEquals(lat, sameLat, 0.0)
    }

    @Test
    fun `un cuadrado es un anillo cerrado antihorario centrado en el punto`() {
        val ring = ringOf(SyntheticGeo.square(lon, lat, 100.0))
        assertEquals(5, ring.size)
        assertEquals(ring.first(), ring.last())
        // área con signo (fórmula del lazo): positiva si el anillo va en sentido antihorario
        val signed = ring.zipWithNext { a, b -> a[0] * b[1] - b[0] * a[1] }.sum() / 2
        assertTrue(signed > 0, "$signed")
        assertEquals(lon, ring.dropLast(1).map { it[0] }.average(), 1e-12)
        assertEquals(lat, ring.dropLast(1).map { it[1] }.average(), 1e-12)
        assertEquals(SyntheticGeo.offset(lon, lat, 50.0, 0.0).first, ring.maxOf { it[0] }, 0.0)
    }

    @Test
    fun `multiSquare es el mismo cuadrado como MultiPolygon de una parte`() {
        val multi = SyntheticGeo.multiSquare(lon, lat, 100.0)
        assertEquals("MultiPolygon", multi["type"])
        assertEquals(listOf(SyntheticGeo.square(lon, lat, 100.0)["coordinates"]), multi["coordinates"])
    }

    @Test
    fun `un punto y una linea en GeoJSON, una linea necesita dos puntos y un cuadrado un lado positivo`() {
        assertEquals(mapOf("type" to "Point", "coordinates" to listOf(lon, lat)), SyntheticGeo.point(lon, lat))
        val line = SyntheticGeo.line(lon to lat, SyntheticGeo.offset(lon, lat, 100.0, 0.0))
        assertEquals("LineString", line["type"])
        assertEquals(2, (line["coordinates"] as List<*>).size)
        assertThrows<IllegalArgumentException> { SyntheticGeo.line(lon to lat) }
        assertThrows<IllegalArgumentException> { SyntheticGeo.square(lon, lat, 0.0) }
    }
}
