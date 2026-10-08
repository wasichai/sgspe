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
            // 448.6 m con PROJ 9.8 (postgis 18-3.6); el rango de gis.md §9 es de cientos de metros, aquí se acota para notar un cambio de transformación
            assertTrue(shift > BigDecimal(400) && shift < BigDecimal(500), "desplazamiento $shift m")
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
            assertEquals("autointersección", validity.reason)
            assertTrue(validity.detail!!.contains("Self-intersection"), validity.detail)
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
            // el tipo destino no distingue mayúsculas
            assertEquals("Polygon", geo.asType(single, "POLYGON")!!["type"])
            assertEquals("MultiPolygon", geo.asType(square(-75.0, -12.0, 0.01), "MULTIPOLYGON")!!["type"])
        }
    }

    @Test
    fun `un MultiPolygon con hueco mide el area neta`() {
        runBlocking {
            val outer = listOf(listOf(500000, 8800000), listOf(501000, 8800000), listOf(501000, 8801000), listOf(500000, 8801000), listOf(500000, 8800000))
            val hole = listOf(listOf(500300, 8800300), listOf(500300, 8800700), listOf(500700, 8800700), listOf(500700, 8800300), listOf(500300, 8800300))
            val shape = geo.toWgs84(mapOf("type" to "MultiPolygon", "coordinates" to listOf(listOf(outer, hole))), 32718)
            // 100 ha menos el hueco de 400 m por 400 m (16 ha)
            assertEquals(BigDecimal("84.00"), geo.areaHa(shape, 32718))
        }
    }

    @Test
    fun `medir exige una zona de trabajo y transformar uno del catalogo`() {
        runBlocking {
            val shape = square(-75.0, -12.0, 0.01)
            assertTrue(runCatching { geo.areaHa(shape, 4326) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.areaHa(shape, 3857) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.distanceM(shape, shape, 4326) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.utm(shape, 4326) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.toWgs84(shape, 3857) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.pointToWgs84(BigDecimal(500000), BigDecimal(8800000), 32716) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `un poligono vacio es invalido y no tiene centroide ni UTM`() {
        runBlocking {
            val empty = mapOf("type" to "Polygon", "coordinates" to emptyList<Any>())
            val validity = geo.validity(empty)
            assertFalse(validity.valid)
            assertEquals("geometría vacía", validity.reason)
            assertNull(geo.centroid(empty))
            assertNull(geo.centroidZone(empty))
            assertNull(geo.utm(empty, 32718))
        }
    }

    @Test
    fun `coordenadas absurdas son una InvalidGeometryException y no un error de R2DBC`() {
        runBlocking {
            val far = mapOf("type" to "Point", "coordinates" to listOf(1.0e12, 8800000.0))
            assertTrue(runCatching { geo.toWgs84(far, 32718) }.exceptionOrNull() is InvalidGeometryException)
            assertTrue(runCatching { geo.pointToWgs84(BigDecimal("1e12"), BigDecimal(8800000), 32718) }.exceptionOrNull() is InvalidGeometryException)
            val tall = mapOf("type" to "Point", "coordinates" to listOf(-75.0, 200.0))
            assertTrue(runCatching { geo.utm(tall, 32718) }.exceptionOrNull() is InvalidGeometryException)
            assertTrue(runCatching { geo.areaHa(square(-75.0, 200.0, 0.01), 32718) }.exceptionOrNull() is InvalidGeometryException)
        }
    }
}
