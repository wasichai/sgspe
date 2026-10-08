package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.DomainApiTest
import java.math.BigDecimal

// gis.md §9 contra PostGIS real, con geometrías sintéticas (RQ-GIS-021): ida y vuelta UTM ↔ 4326, área de un cuadrado,
// zona del centroide, PSAD56 y validez
class GeoSqlApiTest : DomainApiTest() {
    @Autowired
    private lateinit var geo: GeoSql

    @Test
    fun `ida y vuelta UTM y 4326 en 17S, 18S y 19S con error de 1 cm o menos`() {
        runBlocking {
            for ((srid, easting, northing) in listOf(
                Triple(32717, 812345.678, 9123456.789),
                Triple(32718, 500000.0, 8800000.0),
                Triple(32719, 300000.5, 8300000.25)
            )) {
                val wgs84 = geo.pointToWgs84(BigDecimal(easting), BigDecimal(northing), srid)
                val back = geo.utm(wgs84, srid)!!
                assertTrue((back.easting - BigDecimal(easting)).abs() <= BigDecimal("0.01"), "$srid este ${back.easting}")
                assertTrue((back.northing - BigDecimal(northing)).abs() <= BigDecimal("0.01"), "$srid norte ${back.northing}")
            }
        }
    }

    @Test
    fun `un cuadrado de 1 km por lado en UTM da 100 ha de area plana`() {
        runBlocking {
            val ring = listOf(listOf(500000, 8800000), listOf(501000, 8800000), listOf(501000, 8801000), listOf(500000, 8801000), listOf(500000, 8800000))
            val square = geo.toWgs84(mapOf("type" to "Polygon", "coordinates" to listOf(ring)), 32718)
            assertEquals(BigDecimal("100.00"), geo.areaHa(square, 32718))
            assertEquals(32718, geo.centroidZone(square))
            assertNull(geo.areaHa(point(-75.0, -12.0), 32718))
        }
    }

    @Test
    fun `el mismo numero leido como PSAD56 cae a cientos de metros que como WGS 84`() {
        runBlocking {
            val psad = geo.pointToWgs84(BigDecimal(812345), BigDecimal(9123456), 24877)
            val wgs = geo.pointToWgs84(BigDecimal(812345), BigDecimal(9123456), 32717)
            val shift = geo.distanceM(psad, wgs, 32717)
            assertTrue(shift > BigDecimal(100) && shift < BigDecimal(1000), "desplazamiento $shift m")
        }
    }

    @Test
    fun `un lazo es invalido con su motivo, y los tipos se normalizan sin perder partes`() {
        runBlocking {
            val bowtie =
                mapOf(
                    "type" to "Polygon",
                    "coordinates" to
                        listOf(listOf(listOf(-75.0, -12.0), listOf(-74.99, -11.99), listOf(-74.99, -12.0), listOf(-75.0, -11.99), listOf(-75.0, -12.0)))
                )
            val validity = geo.validity(bowtie)
            assertFalse(validity.valid)
            assertTrue(validity.reason!!.contains("Self-intersection"), validity.reason)
            val single = square(-75.0, -12.0, 0.01, "MultiPolygon")
            assertEquals("Polygon", geo.asType(single, "Polygon")!!["type"])
            assertEquals("MultiPolygon", geo.asType(square(-75.0, -12.0, 0.01), "MultiPolygon")!!["type"])
            val two =
                mapOf(
                    "type" to "MultiPolygon",
                    "coordinates" to
                        (
                            square(
                                -75.0,
                                -12.0,
                                0.01,
                                "MultiPolygon"
                            )["coordinates"] as List<*> + square(-74.9, -12.0, 0.01, "MultiPolygon")["coordinates"] as List<*>
                        )
                )
            assertNull(geo.asType(two, "Polygon"))
        }
    }
}
