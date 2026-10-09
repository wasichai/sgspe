package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.RuleException

// las hojas de commitment-matrix por nivel de divulgación (deliverables.md §9, §10, RQ-REP-022), sin base
class MatrixSheetsTest {
    private val all = MatrixSheets.REQUIRES.toSet()
    private val line =
        MatrixLine(
            "11111111-2222-3333-4444-555555555555",
            "in_progress",
            mapOf(
                "project" to "p1",
                "code" to "C-1",
                "title" to "Agua",
                "beneficiaries_detail" to "detalle restringido",
                "owner_user_email" to "responsable@sgspe.test",
                "amount" to "100"
            ),
            listOf("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" to "Asociación Ejemplo"),
            listOf("Primer hito (2026-10-01)"),
            listOf("2026-09-01 → 2026-10-01"),
            2
        )

    private fun cells(sheet: XlsxSheet): List<List<Any?>> = sheet.rows.toList()

    @Test
    fun `INTERNO nombra a los solicitantes y trae al responsable y lo restringido que se lee`() {
        val sheet = MatrixSheets.matrix(listOf(line), mapOf("p1" to "PRY-1"), all, Disclosure.INTERNO)
        val row = cells(sheet).single()
        assertTrue("Asociación Ejemplo" in row)
        assertTrue("detalle restringido" in row)
        assertEquals("responsable@sgspe.test", row.last())
        assertTrue("Responsable" in sheet.header)
        assertFalse(sheet.header.any { it.contains("identidad", ignoreCase = true) })
    }

    @Test
    fun `EXTERNO cita por codigo GI y no trae responsable ni lo restringido`() {
        val readable = all - MatrixSheets.RESTRICTED
        val sheet = MatrixSheets.matrix(listOf(line), mapOf("p1" to "PRY-1"), readable, Disclosure.EXTERNO)
        val row = cells(sheet).single()
        assertTrue("GI-AAAAAAAABBBB" in row, row.toString())
        assertFalse("Asociación Ejemplo" in row)
        assertFalse("detalle restringido" in row)
        assertFalse("responsable@sgspe.test" in row)
        assertFalse("Responsable" in sheet.header)
    }

    // un compromiso cumplido o anulado conserva el deadline_status de la última vuelta de JOB-01: la matriz no lo muestra
    @Test
    fun `la situacion de plazo sale solo en un compromiso pendiente`() {
        val overdue = line.attributes + ("deadline_status" to "VENCIDO")

        fun row(state: String) =
            cells(
                MatrixSheets.matrix(
                    listOf(MatrixLine(line.id, state, overdue, line.requesters, line.milestones, line.reschedules, line.actions)),
                    emptyMap(),
                    all,
                    Disclosure.INTERNO
                )
            ).single()
        for (state in listOf("approved", "in_progress", "blocked")) assertTrue("VENCIDO" in row(state), state)
        for (state in listOf("fulfilled", "verified", "closed", "cancelled", "draft")) assertFalse("VENCIDO" in row(state), state)
        val unreadable =
            cells(
                MatrixSheets.matrix(
                    listOf(MatrixLine(line.id, "fulfilled", overdue, line.requesters, line.milestones, line.reschedules, line.actions)),
                    emptyMap(),
                    all - "commitment.deadline_status",
                    Disclosure.INTERNO
                )
            ).single()
        assertTrue(NOT_AVAILABLE in unreadable)
    }

    @Test
    fun `un campo que no se lee sale no disponible`() {
        val sheet = MatrixSheets.matrix(listOf(line), emptyMap(), all - "commitment.amount", Disclosure.INTERNO)
        assertTrue(NOT_AVAILABLE in cells(sheet).single())
    }

    @Test
    fun `las entregas en EXTERNO no traen quienes dieron la conformidad ni los receptores`() {
        val delivery =
            MatrixDelivery(
                "C-1",
                mapOf("conformity_given" to true, "conformity_by" to "Junta", "recipients" to "Padres"),
                listOf("ACTA DE ENTREGA", "FOTO", "FOTO")
            )
        val internal = cells(MatrixSheets.deliveries(listOf(delivery), all, Disclosure.INTERNO)).single()
        assertTrue("Junta" in internal && "Padres" in internal)
        val external = cells(MatrixSheets.deliveries(listOf(delivery), all - MatrixSheets.RESTRICTED, Disclosure.EXTERNO)).single()
        assertFalse("Junta" in external || "Padres" in external)
        assertEquals(listOf(1, 2, 0), external.takeLast(3))
    }

    @Test
    fun `PUBLICO cuenta sobre la grilla completa con la marca reservado y un catalogo vacio es un 409`() {
        val states = listOf("draft", "approved")
        val lines = List(6) { line } + List(2) { MatrixLine("x", "draft", mapOf("commitment_theme" to "AMBIENTAL"), emptyList(), emptyList(), emptyList(), 0) }
        val sheets = MatrixSheets.aggregates(lines, all, states, listOf("SOCIAL", "AMBIENTAL"), listOf("MATERIAL", "NO MATERIAL"))
        val byState = cells(sheets.first())
        // 3 estados de la grilla (con «no disponible») por 4 temas (con SIN TEMA y «no disponible»)
        assertEquals(12, byState.size)
        assertTrue(byState.all { it[2] == SmallCells.HIDDEN || (it[2] as Int) >= SmallCells.MIN })
        assertThrows<RuleException> { MatrixSheets.aggregates(lines, all, states, emptyList(), listOf("MATERIAL")) }
    }

    @Test
    fun `el lugar de la entrega sale literal en INTERNO y en EXTERNO se generaliza a la localidad`() {
        val delivery = MatrixDelivery("C-1", mapOf("place" to "Vivienda de Persona Ejemplo"), emptyList(), "Caserío Ejemplo")
        val internal = cells(MatrixSheets.deliveries(listOf(delivery), all, Disclosure.INTERNO)).single()
        assertTrue("Vivienda de Persona Ejemplo" in internal)
        val readable = all - MatrixSheets.RESTRICTED
        val external = cells(MatrixSheets.deliveries(listOf(delivery), readable, Disclosure.EXTERNO)).single()
        assertFalse("Vivienda de Persona Ejemplo" in external)
        assertTrue("Caserío Ejemplo" in external)
        val noLocality = MatrixDelivery("C-1", mapOf("place" to "Vivienda de Persona Ejemplo"), emptyList(), null)
        val empty = cells(MatrixSheets.deliveries(listOf(noLocality), readable, Disclosure.EXTERNO)).single()
        assertFalse("Vivienda de Persona Ejemplo" in empty)
        assertEquals("", empty[2])
    }

    @Test
    fun `lo que no se lee sale no disponible y nunca vacio ni cero`() {
        val hidden = MatrixLine("x", "draft", mapOf("project" to "p1", "code" to "C-1"), null, null, null, null)
        val row = cells(MatrixSheets.matrix(listOf(hidden), mapOf("p1" to "PRY-1"), all - "project.code", Disclosure.INTERNO)).single()
        assertFalse("PRY-1" in row)
        assertTrue(row.count { it == NOT_AVAILABLE } >= 5, row.toString())
        val noEvidence = MatrixDelivery("C-1", emptyMap(), null)
        val delivery = cells(MatrixSheets.deliveries(listOf(noEvidence), all, Disclosure.INTERNO)).single()
        assertEquals(listOf(NOT_AVAILABLE, NOT_AVAILABLE, NOT_AVAILABLE), delivery.takeLast(3))
    }
}
