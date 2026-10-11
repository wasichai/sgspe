package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.gis.GeometryType

// las reglas puras de la validación previa (gis.md §4.3 y §8, especificación de F6 §3.6)
class GeometryCheckRulesTest {
    private val properties = ImportProperties()

    @Test
    fun `el tipo GeoJSON y la dimension que se extrae de la reparacion`() {
        assertEquals("MultiPolygon", GeometryCheckRules.geoJsonType(GeometryType.MULTIPOLYGON))
        assertEquals("LineString", GeometryCheckRules.geoJsonType(GeometryType.LINESTRING))
        assertEquals(3, GeometryCheckRules.extractDimension(GeometryType.POLYGON))
        assertEquals(2, GeometryCheckRules.extractDimension(GeometryType.MULTILINESTRING))
        assertEquals(1, GeometryCheckRules.extractDimension(GeometryType.POINT))
    }

    @Test
    fun `fuera de la envolvente del Peru y fuera de la zona del proyecto`() {
        assertFalse(GeometryCheckRules.outOfRegion(-78.50 to -7.90, properties))
        assertTrue(GeometryCheckRules.outOfRegion(10.0 to 10.0, properties))
        assertTrue(GeometryCheckRules.outOfRegion(null, properties))
        assertFalse(GeometryCheckRules.outOfZone(-78.50 to -7.90, 32717))
        assertTrue(GeometryCheckRules.outOfZone(-75.50 to -7.90, 32717))
        assertFalse(GeometryCheckRules.outOfZone(-75.50 to -7.90, null))
    }

    @Test
    fun `una geometria absurda se rechaza antes de llegar a PostGIS`() {
        val ring = List(GeometryCheckRules.MAX_VERTICES + 1) { listOf(-78.5, -7.9) }
        assertTrue(GeometryCheckRules.tooLarge(mapOf("type" to "LineString", "coordinates" to ring)))
        val small = mapOf("type" to "LineString", "coordinates" to listOf(listOf(-78.5, -7.9), listOf(-78.4, -7.9)))
        assertFalse(GeometryCheckRules.tooLarge(small))
        var deep: Any = listOf(1.0, 2.0)
        repeat(20) { deep = listOf(deep) }
        assertTrue(GeometryCheckRules.tooLarge(mapOf("type" to "Polygon", "coordinates" to deep)))
    }
}
