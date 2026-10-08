package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

// medir antes de escribir (P20): una geometría que PostGIS no puede medir no es un error del comando ni deja nada escrito;
// cualquier otro fallo sí sale. el resultado distingue «sin zona o sin geometría» (área vacía, se escribe) de «no medible» (null,
// no se escribe)
class AreasTest {
    @Test
    fun `una geometria no medible da null y no una excepcion`() {
        assertNull(runBlocking { Areas.measuring("area_of_influence", "id") { throw InvalidGeometryException("fuera del dominio") } })
    }

    @Test
    fun `un area vacia por falta de zona o de geometria es una medida valida`() {
        val empty = runBlocking { Areas.measuring("area_of_influence", "id") { null } }
        assertNotNull(empty)
        assertNull(empty!!.value)
    }

    @Test
    fun `una medida lleva su valor`() {
        assertEquals(BigDecimal("12.5"), runBlocking { Areas.measuring("area_of_influence", "id") { BigDecimal("12.5") } }!!.value)
    }

    @Test
    fun `cualquier otro fallo sale`() {
        assertThrows<IllegalStateException> { runBlocking { Areas.measuring("area_of_influence", "id") { error("falla") } } }
    }
}
