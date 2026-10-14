package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.time.LocalDate
import java.util.UUID

// una cifra oficial es completa o no existe (especificación de F8 §2.2.15, ASM-136): las razones antes y después de calcular,
// el desglose principal como tabla Markdown y el cuerpo del cierre
class ClosabilityTest {
    private val today = LocalDate.parse("2026-10-13")
    private val september = ClosingPeriod(null, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))
    private val july = ClosingPeriod(null, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"))

    @Test
    fun `antes de calcular, la postura, lo que no tiene escalar y lo que Kpis no calcula`() {
        for (code in listOf("KPI-STK-02", "KPI-STK-05", "KPI-STK-06")) assertEquals(Closability.STANCE, Closability.before(code, september, today), code)
        for (code in listOf("KPI-TER-01", "KPI-EXE-03")) assertEquals(Closability.NO_SCALAR_VALUE, Closability.before(code, september, today), code)
        assertEquals(Closability.UNAVAILABLE, Closability.before("KPI-XYZ-01", september, today))
        assertNull(Closability.before("KPI-COM-08", july, today))
        // la lista de postura es exactamente la de los KPI que leen stance (dashboards.md §8)
        assertEquals(
            Closability.STANCE_KPIS,
            Kpis.ALL
                .filter { kpi -> kpi.requires.any { it.endsWith(".stance") } }
                .map { it.code }
                .toSet()
        )
    }

    @Test
    fun `el mensaje de NOT_PERIOD_BOUND dice que vale el mes pasado`() {
        val message = Closability.message(Closability.NOT_PERIOD_BOUND, "KPI-COM-02")
        assertTrue(message.startsWith("KPI-COM-02 no se cierra (NOT_PERIOD_BOUND)"), message)
        assertTrue("el pasado en Lima" in message, message)
        assertEquals(
            "KPI-STK-02 no se cierra (STANCE): una cifra oficial es completa o no existe (ASM-136)",
            Closability.message(Closability.STANCE, "KPI-STK-02")
        )
    }

    // decisión 27 (R7): la foto de hoy solo vale para el periodo vigente: un mes calendario entero, el pasado o el actual
    @Test
    fun `un KPI sin periodo solo se cierra para el periodo vigente`() {
        fun before(
            start: String,
            end: String,
            on: LocalDate = today
        ) = Closability.before("KPI-COM-02", ClosingPeriod(null, LocalDate.parse(start), LocalDate.parse(end)), on)
        assertNull(Closability.before("KPI-COM-02", september, today))
        assertEquals(Closability.NOT_PERIOD_BOUND, Closability.before("KPI-COM-02", july, today))
        assertEquals(Closability.NOT_PERIOD_BOUND, before("2026-08-01", "2026-08-31"))
        // el mes actual entero es vigente (que no haya terminado lo dice PERIOD_NOT_ENDED); una parte de él, no
        assertNull(before("2026-10-01", "2026-10-31"))
        assertEquals(Closability.NOT_PERIOD_BOUND, before("2026-10-01", "2026-10-12"))
        // el cambio de año: el 10 de enero, diciembre es el mes pasado
        assertNull(before("2025-12-01", "2025-12-31", LocalDate.parse("2026-01-10")))
        assertEquals(Closability.NOT_PERIOD_BOUND, before("2025-11-01", "2025-11-30", LocalDate.parse("2026-01-10")))
        // un tramo de 21 meses que termina en el mes pasado, un inicio a mitad de mes, un fin antes del último día
        assertEquals(Closability.NOT_PERIOD_BOUND, before("2025-01-01", "2026-09-30"))
        assertEquals(Closability.NOT_PERIOD_BOUND, before("2026-09-15", "2026-09-30"))
        assertEquals(Closability.NOT_PERIOD_BOUND, before("2026-09-01", "2026-09-29"))
        // un KPI del periodo se cierra para cualquier periodo terminado
        assertNull(Closability.before("KPI-COM-08", ClosingPeriod(null, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-09-30")), today))
        // los que declaran el periodo como ignorado son de esta lista; los de periodo, no
        assertTrue(Kpis.ALL.filter { "periodStart" in it.ignores }.all { it.code in Closability.PERIODLESS || it.code in Closability.NO_SCALAR })
        for (code in listOf("KPI-ENG-01", "KPI-COM-05", "KPI-COM-08", "KPI-GRV-01", "KPI-STK-08")) assertFalse(code in Closability.PERIODLESS, code)
        assertTrue(Closability.PERIODLESS.all { it in Kpis.BY_CODE })
    }

    // decisión 25 (R4): la marca confidentialHidden ya no impide cerrar: el cierre calcula sin las confidenciales
    @Test
    fun `despues de calcular, no disponible y sin valor`() {
        assertEquals(Closability.UNAVAILABLE, Closability.after(IndicatorValue("KPI-COM-10", "x", false, unavailableReason = "FIELD_NOT_READABLE")))
        assertNull(Closability.after(IndicatorValue("KPI-GRV-01", "x", true, 3, confidentialHidden = true)))
        assertEquals(Closability.NO_SCALAR_VALUE, Closability.after(IndicatorValue("KPI-STK-07", "x", true, null)))
        assertNull(Closability.after(IndicatorValue("KPI-COM-01", "x", true, 0)))
    }

    @Test
    fun `el desglose principal es una tabla Markdown sin romperse con barras ni saltos`() {
        val value =
            IndicatorValue(
                "KPI-COM-01",
                "x",
                true,
                3,
                breakdowns =
                    listOf(
                        Breakdown(
                            "workflow_state",
                            listOf(
                                mapOf("key" to "draft", "value" to 2),
                                mapOf("key" to "a|b\nc", "label" to "Etiqueta", "value" to 1),
                                mapOf("key" to "x", "label" to "C:\\ruta|a\r\nb\rc", "value" to 4)
                            )
                        ),
                        Breakdown("otro", listOf(mapOf("key" to "z", "value" to 9)))
                    )
            )
        assertEquals(
            "| workflow_state | Valor |\n|---|---|\n| draft | 2 |\n| Etiqueta | 1 |\n| C:\\\\ruta\\|a b c | 4 |",
            Closability.breakdown(value)
        )
        assertNull(Closability.breakdown(IndicatorValue("KPI-COM-02", "x", true, 3)))
    }

    // decisión 26 (R5): un desglose por registro (grupos de interés, interacciones, responsables, riesgos) no se congela
    @Test
    fun `un desglose por registro no se congela`() {
        for (by in Closability.RECORD_LEVEL) {
            val value =
                IndicatorValue(
                    "KPI-STK-03",
                    "x",
                    true,
                    2,
                    breakdowns =
                        listOf(
                            Breakdown(
                                by,
                                listOf(
                                    mapOf(
                                        "key" to "id-1",
                                        "label" to "Persona Ejemplo 1",
                                        "value" to 1
                                    )
                                )
                            )
                        )
                )
            assertNull(Closability.breakdown(value), by)
        }
        assertEquals(setOf("stakeholder", "engagement", "overdue", "owner", "risk"), Closability.RECORD_LEVEL)
        // la lista es de permitidos: un desglose que no está en AGGREGATE tampoco se congela
        val unknown = IndicatorValue("KPI-COM-01", "x", true, 1, breakdowns = listOf(Breakdown("nuevo", listOf(mapOf("key" to "a", "value" to 1)))))
        assertNull(Closability.breakdown(unknown))
    }

    @Test
    fun `el cuerpo del cierre trae el periodo y, si es de un proyecto, su id`() {
        val project = UUID.randomUUID()
        val body = mapOf("project" to project.toString(), "attributes" to mapOf("period_start_date" to "2026-09-01", "period_end_date" to "2026-09-30"))
        assertEquals(ClosingPeriod(project, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")), ClosingPeriod.parse(body))
        assertNull(ClosingPeriod.parse(body - "project").project)

        fun code(body: Map<String, Any?>) = assertThrows<WasichaiException> { ClosingPeriod.parse(body) }.violations.single().let { it.field to it.message }
        assertEquals("attributes.period_end_date" to "FIELD_REQUIRED", code(mapOf("attributes" to mapOf("period_start_date" to "2026-09-01"))))
        assertEquals(
            "attributes.period_start_date" to "INVALID_VALUE",
            code(
                mapOf("attributes" to mapOf("period_start_date" to "1/9/2026", "period_end_date" to "2026-09-30"))
            )
        )
        assertEquals(
            "attributes.period_start_date" to "OUT_OF_RANGE",
            code(
                mapOf("attributes" to mapOf("period_start_date" to "2026-10-01", "period_end_date" to "2026-09-30"))
            )
        )
        assertThrows<WasichaiException> { ClosingPeriod.parse(body + ("note" to "x")) }
    }
}
