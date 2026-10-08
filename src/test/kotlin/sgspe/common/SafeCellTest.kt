package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SafeCellTest {
    @Test
    fun `el texto que una hoja tomaria por formula lleva un apostrofo delante`() {
        listOf("=1+1", "+1", "-1", "@SUMA(A1)", "\tx", "\rx").forEach { assertEquals("'$it", safeCell(it)) }
    }

    @Test
    fun `el texto normal y el vacio no cambian`() {
        listOf("TST-001", "Cajamarca", "", "a=b", "1+1").forEach { assertEquals(it, safeCell(it)) }
    }
}
