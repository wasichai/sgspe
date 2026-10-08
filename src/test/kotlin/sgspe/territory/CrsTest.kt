package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// gis.md §3: los valores del catálogo y sus SRID, y la zona del centroide (ASM-064)
class CrsTest {
    @Test
    fun `los valores del catalogo y su srid`() {
        assertEquals(32717, Crs.srid("EPSG 32717"))
        assertEquals(24879, Crs.srid("EPSG 24879"))
        assertEquals(4326, Crs.srid("EPSG 4326"))
        assertNull(Crs.srid("EPSG 3857"))
        assertNull(Crs.srid(null))
        assertEquals("EPSG 32718", Crs.value(32718))
    }

    @Test
    fun `la zona de un CRS UTM, WGS 84 o PSAD56`() {
        assertEquals(17, Crs.zone(32717))
        assertEquals(19, Crs.zone(24879))
        assertNull(Crs.zone(4326))
    }

    @Test
    fun `zona del centroide en el Peru, y nada fuera de 17S a 19S o al norte del ecuador`() {
        assertEquals(32717, GeoSql.zoneOf(-78.5, -7.9))
        assertEquals(32718, GeoSql.zoneOf(-75.0, -12.0))
        assertEquals(32719, GeoSql.zoneOf(-70.0, -15.5))
        assertNull(GeoSql.zoneOf(-60.0, -15.0))
        assertNull(GeoSql.zoneOf(-78.5, 1.0))
    }
}
