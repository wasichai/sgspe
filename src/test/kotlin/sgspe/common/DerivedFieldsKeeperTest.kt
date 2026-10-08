package sgspe.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory

// la decisión de DerivedFieldsKeeper sin base: solo se devuelve lo que sigue vacío en lo guardado
class DerivedFieldsKeeperTest {
    private val log = LoggerFactory.getLogger(DerivedFieldsKeeperTest::class.java)
    private val before = mapOf<String, Any?>("area_ha" to 12.5, "crs" to "EPSG:32718")

    private fun restore(
        stored: suspend () -> Map<String, Any?>,
        patched: MutableList<Map<String, Any?>>
    ) = runBlocking {
        DerivedFieldsKeeper.restore(log, "area_of_influence x", listOf("area_ha", "crs"), before, stored) { patched += it }
    }

    @Test
    fun `devuelve los derivados que siguen vacios`() {
        val patched = mutableListOf<Map<String, Any?>>()
        restore({ mapOf("area_ha" to null, "crs" to null) }, patched)
        assertEquals(listOf(mapOf<String, Any?>("area_ha" to 12.5, "crs" to "EPSG:32718")), patched)
    }

    @Test
    fun `no pisa un derivado al que otra escritura ya dio valor`() {
        val patched = mutableListOf<Map<String, Any?>>()
        restore({ mapOf("area_ha" to 99.0, "crs" to null) }, patched)
        assertEquals(listOf(mapOf<String, Any?>("crs" to "EPSG:32718")), patched)
    }

    @Test
    fun `si todos ya tienen valor no escribe nada`() {
        val patched = mutableListOf<Map<String, Any?>>()
        restore({ mapOf("area_ha" to 99.0, "crs" to "EPSG:32719") }, patched)
        assertTrue(patched.isEmpty())
    }

    @Test
    fun `un fallo se registra y no se propaga`() {
        val patched = mutableListOf<Map<String, Any?>>()
        restore({ error("la base no responde") }, patched)
        assertTrue(patched.isEmpty())
    }

    @Test
    fun `la cancelacion no se traga`() {
        assertThrows<CancellationException> { restore({ throw CancellationException("cancelada") }, mutableListOf()) }
    }
}
