package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

// la geometría de una zona que llega a ST_GeomFromGeoJSON y los campos que el filtro de una zona necesita leer
class ZoneRulesTest {
    private val ring = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0), listOf(0.0, 0.0))

    @Test
    fun `una geometria mal formada o vacia no llega al SQL`() {
        assertNull(ZoneRules.usable(null))
        assertNull(ZoneRules.usable(emptyMap()))
        assertNull(ZoneRules.usable(mapOf("type" to "Polygon")))
        assertNull(ZoneRules.usable(mapOf("type" to "Polygon", "coordinates" to emptyList<Any>())))
        assertNull(ZoneRules.usable(mapOf("type" to "Polygon", "coordinates" to "x")))
        assertNull(ZoneRules.usable(mapOf("type" to 5, "coordinates" to listOf(ring))))
        assertNull(ZoneRules.usable(mapOf("type" to "", "coordinates" to listOf(ring))))
        assertNull(ZoneRules.usable(mapOf("type" to "GeometryCollection", "geometries" to emptyList<Any>())))
    }

    @Test
    fun `una geometria con tipo y coordenadas pasa tal cual`() {
        val polygon = mapOf("type" to "Polygon", "coordinates" to listOf(ring))
        assertEquals(polygon, ZoneRules.usable(polygon))
        assertNotNull(ZoneRules.usable(mapOf("type" to "MultiPolygon", "coordinates" to listOf(listOf(ring)))))
    }

    @Test
    fun `se necesita leer la relacion con ids y la geometria solo si la zona la tiene`() {
        val target = ZoneTargets.ALL.getValue("commitment")
        val zone = Zone(mapOf("locality" to setOf(UUID.randomUUID())), mapOf("type" to "Polygon", "coordinates" to listOf(ring)))
        assertEquals(listOf("commitment.locality", "commitment.location"), ZoneRules.required("commitment", target, zone))
        val bare = Zone(mapOf("locality" to setOf(UUID.randomUUID()), "community" to emptySet()), null)
        assertEquals(listOf("commitment.locality"), ZoneRules.required("commitment", target, bare))
    }

    @Test
    fun `el periodo suma el campo de fecha a lo que se necesita leer`() {
        val target = ZoneTargets.ALL.getValue("commitment")
        val zone = Zone(emptyMap(), null)
        assertEquals(listOf("commitment.agreed_date"), ZoneRules.required("commitment", target, zone, periodGiven = true))
    }
}
