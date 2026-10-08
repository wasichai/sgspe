package sgspe.territory

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.R2dbcNonTransientException
import io.r2dbc.spi.R2dbcNonTransientResourceException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.UncategorizedR2dbcException
import org.springframework.r2dbc.core.DatabaseClient

// las guardas puras de GeoSql, sin base (gis.md §3.1: nunca se mide sobre grados ni con un SRID fuera del catálogo)
class GeoSqlTest {
    private fun failure(
        message: String,
        state: String?
    ): R2dbcNonTransientException = object : R2dbcNonTransientException(message, state) {}

    @Test
    fun `medir exige una zona de trabajo y transformar uno del catalogo`() {
        GeoSql.requireWorkingZone(32717)
        GeoSql.requireWorkingZone(32719)
        GeoSql.requireCatalogued(4326)
        GeoSql.requireCatalogued(24877)
        GeoSql.requireCatalogued(32719)
        for (srid in listOf(4326, 3857, 32716, 32720, 24877, 24878, 24879, 0)) {
            assertThrows(IllegalArgumentException::class.java) { GeoSql.requireWorkingZone(srid) }
        }
        for (srid in listOf(3857, 32716, 32720, 0)) {
            assertThrows(IllegalArgumentException::class.java) { GeoSql.requireCatalogued(srid) }
        }
    }

    @Test
    fun `areaHa, distanceM y utm rechazan PSAD56 antes de tocar la base`() {
        val geo = GeoSql(DatabaseClient.create(ConnectionFactories.get("r2dbc:postgresql://x:y@localhost:1/z")))
        val shape = mapOf("type" to "Point", "coordinates" to listOf(-75.0, -12.0))
        runBlocking {
            assertTrue(runCatching { geo.areaHa(shape, 24878) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.distanceM(shape, shape, 24877) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { geo.utm(shape, 24879) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `un error de PostGIS por la geometria se reconoce, uno de conexion o de SQL no`() {
        val projection = failure("transform: Point outside of projection domain (2050)", "XX000")
        assertEquals(projection, GeoSql.geometryFailure(projection))
        assertEquals(projection, GeoSql.geometryFailure(UncategorizedR2dbcException("bind", "SELECT 1", projection)))
        assertNotNull(GeoSql.geometryFailure(failure("dato", "22023")))
        assertNull(GeoSql.geometryFailure(R2dbcNonTransientResourceException("conexión", "08006")))
        assertNull(GeoSql.geometryFailure(failure("sintaxis", "42601")))
        assertNull(GeoSql.geometryFailure(failure("sin estado", null)))
        assertNull(GeoSql.geometryFailure(IllegalStateException("otra cosa")))
    }

    @Test
    fun `los motivos de ST_IsValidReason salen en espanol y sin coordenadas`() {
        assertEquals("autointersección", GeoSql.reasonLabel("Self-intersection[-74.995 -11.995]"))
        assertEquals("anillo autointersecado", GeoSql.reasonLabel("Ring Self-intersection[1 2]"))
        assertEquals("pocos puntos", GeoSql.reasonLabel("Too few points in geometry component[0 0]"))
        assertEquals("anillo sin cerrar", GeoSql.reasonLabel("IllegalArgumentException: Points of LinearRing do not form a closed linestring"))
        assertEquals("hueco fuera del contorno", GeoSql.reasonLabel("Hole lies outside shell[1 1]"))
        assertEquals("no cumple las reglas de validez OGC", GeoSql.reasonLabel("algo nuevo"))
        assertEquals("no cumple las reglas de validez OGC", GeoSql.reasonLabel(null))
    }
}
