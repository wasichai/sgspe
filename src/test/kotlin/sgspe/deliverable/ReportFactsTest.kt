package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.privacy.FactRow
import java.time.LocalDate

// ADR-0031 y ASM-088: la grilla de un conteo lleva cada valor del catálogo con su cero, el vacío y lo que no está en el catálogo
class ReportFactsTest {
    @Test
    fun `la grilla lleva el catalogo en orden con sus ceros, el vacio y lo desconocido como No disponible`() {
        val rows = ReportFacts.grid(listOf("B", "A", "C"), mapOf("A" to 2, "Sin dato" to 1, "OTRO" to 3, "MAS" to 1), { "rótulo $it" })
        assertEquals(
            listOf(
                FactRow(listOf("rótulo B"), count = 0),
                FactRow(listOf("rótulo A"), count = 2),
                FactRow(listOf("rótulo C"), count = 0),
                FactRow(listOf("Sin dato"), count = 1),
                FactRow(listOf("No disponible"), count = 4)
            ),
            rows
        )
    }

    @Test
    fun `sin vacio propio lo que no esta en el catalogo va a No disponible y un catalogo vacio no arma grilla`() {
        val rows = ReportFacts.grid(listOf("A"), mapOf("A" to 1, "Sin dato" to 2), { it }, empty = null)
        assertEquals(listOf(FactRow(listOf("A"), count = 1), FactRow(listOf("No disponible"), count = 2)), rows)
        assertNull(ReportFacts.grid(emptyList(), mapOf("A" to 1), { it }))
    }

    @Test
    fun `el periodo, el corte y una celda vacia salen como en el informe`() {
        assertEquals("Del 01/09/2026 al 30/09/2026", ReportFacts.period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        assertEquals("Al 30/09/2026", ReportFacts.cutoff(LocalDate.of(2026, 9, 30)))
        assertNull(ReportFacts.value("Sin dato"))
        assertNull(ReportFacts.value(""))
        assertEquals("12.50", ReportFacts.value("12.50"))
    }
}
