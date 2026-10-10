package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.time.LocalDate

// la situación de plazo de las QCS (workflows.md «Plazos y escalamiento», ASM-100), sin base: la queja mide el acuse en
// `received` y la resolución después; la consulta, la respuesta en `received`; POR VENCER se acota a la mitad del plazo
class DeadlinesQcsTest {
    private val grievance = Deadlines.TRACKED.first { it.objectName == "grievance" }
    private val inquiry = Deadlines.TRACKED.first { it.objectName == "inquiry" }
    private val received = "2026-10-05T15:00:00Z"

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `la queja mide el acuse al recibir y la resolucion despues`() {
        val dates = mapOf("received_at" to received, "ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04")
        assertEquals("VENCIDO", Deadlines.wanted(grievance, "received", dates, date("2026-10-14"), 7))
        assertEquals("AL DIA", Deadlines.wanted(grievance, "acknowledged", dates, date("2026-10-14"), 7))
        assertNull(Deadlines.wanted(grievance, "resolved", dates, date("2026-12-01"), 7))
        assertEquals(date("2026-11-04"), Deadlines.due(grievance, "in_investigation", dates))
    }

    @Test
    fun `el acuse de cinco dias habiles no nace POR VENCER`() {
        // del 5 al 13 hay 8 días: POR VENCER empieza 4 días antes, no 7
        val dates = mapOf("received_at" to received, "ack_due_date" to "2026-10-13")
        assertEquals("AL DIA", Deadlines.wanted(grievance, "received", dates, date("2026-10-05"), 7))
        assertEquals("AL DIA", Deadlines.wanted(grievance, "received", dates, date("2026-10-08"), 7))
        assertEquals("POR VENCER", Deadlines.wanted(grievance, "received", dates, date("2026-10-09"), 7))
        assertEquals(4L, Deadlines.halfCap(7, date("2026-10-05"), date("2026-10-13")))
        assertEquals(7L, Deadlines.halfCap(7, date("2026-10-05"), date("2026-11-04")))
        assertEquals(7L, Deadlines.halfCap(7, null, date("2026-10-13")))
    }

    @Test
    fun `la consulta solo mide su respuesta en received`() {
        val dates = mapOf("received_at" to received, "response_due_date" to "2026-10-20")
        assertEquals("POR VENCER", Deadlines.wanted(inquiry, "received", dates, date("2026-10-14"), 7))
        assertNull(Deadlines.wanted(inquiry, "answered", dates, date("2026-10-30"), 7))
        assertEquals("escalation:inquiry:x:2026-10-20:2", DeadlineJob.escalationKey("inquiry", "x", date("2026-10-20"), 2))
    }

    @Test
    fun `los estados y las fechas de las QCS existen en el modelo, y las solicitudes no se siguen`() {
        val catalog = ModelCatalog.fromClasspath()
        for (tracked in listOf(grievance, inquiry)) {
            assertEquals(emptySet<String>(), tracked.states - catalog.states(tracked.objectName).toSet(), tracked.objectName)
            val dates = (tracked.dateByState.values + tracked.dateField).toSet()
            assertEquals(emptySet<String>(), dates - catalog.derivedFields(tracked.objectName), tracked.objectName)
        }
        assertEquals(false, Deadlines.TRACKED.any { it.objectName == "stakeholder_request" })
    }

    // JOB-01 titula la tarea de escalamiento con DeadlineJob.OVERDUE (getValue): cada objeto que escala necesita su entrada,
    // y una sobrante delata un objeto que dejó de escalar. así un Tracked nuevo (social_risk en el plan 4.2b) falla aquí y no
    // en la integración
    @Test
    fun `cada objeto que escala tiene su titulo de vencido y solo ellos`() {
        assertEquals(
            Deadlines.TRACKED
                .filter { it.escalates }
                .map { it.objectName }
                .toSet(),
            DeadlineJob.OVERDUE.keys
        )
    }
}
