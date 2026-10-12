package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// los fragmentos de SQL de las medidas (gis.md §3.3 y §3.4): el prefiltro en grados nunca achica la envolvente; áreas y
// longitudes van en un CRS UTM; la zona del centroide solo vale en el hemisferio sur y en 17S a 19S
class SpatialSqlTest {
    @Test
    fun `el margen del prefiltro cuenta 100 km por grado`() {
        assertEquals(0.01, SpatialSql.degrees(1000))
        assertEquals(0.5, SpatialSql.degrees(50_000))
    }

    @Test
    fun `las medidas transforman las dos geometrias al CRS pedido`() {
        assertEquals(
            "round(ST_Length(ST_Intersection(ST_Transform(c.t, :s), ST_Transform(p.b, :s)))::numeric, 2)",
            SpatialSql.lengthM("c.t", "p.b", ":s")
        )
        assertEquals(
            "round((ST_Area(ST_Intersection(ST_Transform(c.f, :s), ST_Transform(p.b, :s))) / 10000)::numeric, 4)",
            SpatialSql.areaHa("c.f", "p.b", ":s")
        )
        assertEquals("round(ST_Distance(ST_Transform(a, :s), ST_Transform(b, :s))::numeric, 2)", SpatialSql.distanceM("a", "b", ":s"))
        assertEquals("COALESCE(ST_Transform(c.f, 4326), ST_Transform(c.l, 4326))", SpatialSql.firstOf(listOf("c.f", "c.l"), 4326))
    }

    @Test
    fun `el area del tenant va en la zona UTM de su centroide y solo en 17S a 19S`() {
        val zone = SpatialSql.centroidZone("g")
        assertTrue(zone.contains("WHEN ST_Y(ST_Centroid(ST_Transform(g, 4326))) < 0 THEN 32700 + floor("), zone)
        val area = SpatialSql.zoneAreaHa("g")
        assertTrue(area.contains("ST_Dimension(g) = 2 AND $zone IN (32717, 32718, 32719)"), area)
        assertTrue(area.contains("ST_Area(ST_Transform(g, $zone))"), area)
    }

    @Test
    fun `las partes juntan filas y lo que falta, y la respuesta lo dice sin cero`() {
        val parts = SpatialParts<String>()
        parts.add(GateOutcome.Rows(listOf("a")))
        parts.add(GateOutcome.Unavailable(listOf("land_right.holder_person")))
        parts.missing("locality")
        assertEquals(listOf("a"), parts.rows)
        assertEquals(
            mapOf(
                "available" to false,
                "missingFields" to listOf("land_right.holder_person", "locality"),
                "truncated" to false,
                "rows" to listOf("A")
            ),
            parts.response("rows", listOf("A"), truncated = false)
        )
        assertEquals(mapOf("available" to true, "rows" to emptyList<String>()), SpatialParts<String>().response("rows", emptyList()))
    }
}
