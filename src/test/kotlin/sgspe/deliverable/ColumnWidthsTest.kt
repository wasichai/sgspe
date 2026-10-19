package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Cell
import sgspe.privacy.ColumnSpec

// ASM-141, deliverables.md §7.5: el ancho de cada columna según su contenido, el mismo para el DOCX y el PDF
class ColumnWidthsTest {
    private fun rows(vararg row: List<String>): List<List<Cell>> = row.map { r -> r.map { Cell(it) } }

    private fun assertSumsToOne(columns: List<ColumnWidths.Column>) = assertEquals(1.0, columns.sumOf { it.fraction }, 1e-9)

    @Test
    fun `una columna de texto largo se lleva el ancho y las cifras van angostas y a la derecha`() {
        val columns =
            ColumnWidths.of(
                listOf("Localidad", "Justificación", "Población"),
                rows(
                    listOf("CC San Juan", "Colinda con el tajo y usa el camino de acceso para trasladar su ganado a la feria semanal", "1 250"),
                    listOf("Anexo Pucará", "Se ubica aguas abajo del depósito de relaves", "96")
                )
            )
        assertSumsToOne(columns)
        assertEquals(listOf(ColumnWidths.Align.LEFT, ColumnWidths.Align.LEFT, ColumnWidths.Align.RIGHT), columns.map { it.align })
        assertTrue(columns[1].fraction > columns[0].fraction, columns.toString())
        assertTrue(columns[2].fraction < columns[0].fraction, columns.toString())
    }

    @Test
    fun `si todo cabe en una linea el ancho es proporcional al texto mas largo`() {
        val columns = ColumnWidths.of(listOf("Código", "Nombre"), rows(listOf("GI-01", "Junta de usuarios")), capacity = 100)
        assertSumsToOne(columns)
        // «Junta de usuarios» (17) frente a «Código» (6)
        assertEquals(17.0 / 23.0, columns[1].fraction, 1e-9)
    }

    @Test
    fun `ninguna columna queda mas angosta que su palabra mas larga cuando hay sitio`() {
        val long = "palabra ".repeat(60).trim()
        val columns = ColumnWidths.of(listOf("Organización", "Texto"), rows(listOf("Municipalidad", long)), capacity = 60)
        assertSumsToOne(columns)
        assertTrue(columns[0].fraction * 60 >= "Organización".length - 1e-9, columns.toString())
    }

    @Test
    fun `porcentajes, montos y fechas cuentan como cifras y una celda vacia no lo impide`() {
        val columns =
            ColumnWidths.of(
                listOf("Avance", "Monto", "Fecha", "Estado"),
                rows(listOf("45 %", "S/ 12 500,00", "15/09/2026", "En curso"), listOf("", "US$ 300", "2026-09-30", "Cerrado"))
            )
        assertEquals(
            listOf(ColumnWidths.Align.RIGHT, ColumnWidths.Align.RIGHT, ColumnWidths.Align.RIGHT, ColumnWidths.Align.LEFT),
            columns.map { it.align }
        )
    }

    @Test
    fun `una celda combinada no se mide y las demas siguen en su columna`() {
        val columns =
            ColumnWidths.of(
                listOf("A", "B", "C"),
                listOf(
                    listOf(Cell("un texto combinado muy largo que no debe ensanchar ninguna columna", colSpan = 3)),
                    listOf(Cell("x"), Cell("y"), Cell("12"))
                )
            )
        assertSumsToOne(columns)
        assertEquals(1.0 / 3.0, columns[0].fraction, 1e-9)
        assertEquals(ColumnWidths.Align.RIGHT, columns[2].align)
    }

    @Test
    fun `una celda que ocupa filas hacia abajo deja libre su columna en las filas siguientes`() {
        val columns =
            ColumnWidths.of(
                listOf("Grupo", "Detalle"),
                listOf(listOf(Cell("Grupo A", rowSpan = 2), Cell("uno")), listOf(Cell("un detalle largo")))
            )
        // «un detalle largo» (16) se mide en la columna 1, no en la 0 (7)
        assertEquals(7.0 / 23.0, columns[0].fraction, 1e-9)
    }

    @Test
    fun `los pesos y alineaciones de la tabla mandan sobre el calculo`() {
        val columns =
            ColumnWidths.of(
                listOf("A", "B"),
                rows(listOf("1", "texto")),
                specs = listOf(ColumnSpec(weight = "1"), ColumnSpec(weight = "3", align = "CENTER"))
            )
        assertEquals(listOf(0.25, 0.75), columns.map { it.fraction })
        assertEquals(listOf(ColumnWidths.Align.RIGHT, ColumnWidths.Align.CENTER), columns.map { it.align })
    }

    @Test
    fun `una tabla sin filas reparte por el encabezado y una sin columnas da una sola`() {
        assertEquals(1, ColumnWidths.of(emptyList(), emptyList()).size)
        val columns = ColumnWidths.of(listOf("Uno", "Tres"), emptyList())
        assertSumsToOne(columns)
        assertEquals(listOf(ColumnWidths.Align.LEFT, ColumnWidths.Align.LEFT), columns.map { it.align })
    }
}
