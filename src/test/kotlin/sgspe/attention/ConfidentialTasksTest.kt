package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.JobTasks
import sgspe.common.ModelCatalog
import sgspe.deadline.Deadlines
import sgspe.notification.ConfidentialReaders
import java.time.LocalDate
import java.util.UUID

// las tareas automáticas de una QCS confidencial (D-1, security.md §9.6), sin base: la tarea de acuse genérica del alta y
// la reescritura de las abiertas cuando una QCS existente se marca confidencial
class ConfidentialTasksTest {
    private val id = UUID.randomUUID().toString()
    private val received = "2026-01-05T15:00:00Z"
    private val due = LocalDate.parse("2026-01-08")

    private fun rewrite(
        task: Map<String, Any?>,
        state: String = "open",
        objectName: String = "grievance",
        code: String? = "QJ-1"
    ) = ConfidentialTasks.rewrite(objectName, id, code, received, "Proyecto Ejemplo", state, task)

    @Test
    fun `solo cuenta el paso de no confidencial a confidencial`() {
        assertTrue(ConfidentialTasks.becameConfidential(mapOf("confidential" to false), mapOf("confidential" to true)))
        assertTrue(ConfidentialTasks.becameConfidential(emptyMap(), mapOf("confidential" to true)))
        assertFalse(ConfidentialTasks.becameConfidential(mapOf("confidential" to true), mapOf("confidential" to true)))
        assertFalse(ConfidentialTasks.becameConfidential(mapOf("confidential" to true), mapOf("confidential" to false)))
    }

    @Test
    fun `la tarea de acuse de una queja confidencial lleva la huella de su fecha de recepcion en Lima`() {
        // las 03:00 Z del 6 de enero son todavía el 5 en Lima
        val key = ConfidentialTasks.ackKey(id, "2026-01-06T03:00:00Z")
        assertEquals(JobTasks.confidentialAckKey(ConfidentialReaders.fingerprint("grievance", id, LocalDate.parse("2026-01-05"))), key)
        assertFalse(key!!.contains(id))
        assertNull(ConfidentialTasks.ackKey(id, null))
        val task = ConfidentialTasks.ackTask("p-1", "Proyecto Ejemplo")
        assertEquals(
            mapOf(
                "title" to "Acusar recibo de una QCS confidencial en Proyecto Ejemplo",
                "description" to "Una QCS confidencial espera su acuse. Pida el detalle al equipo de quejas.",
                "project" to "p-1"
            ),
            task
        )
    }

    @Test
    fun `una tarea de escalamiento abierta pasa a la generica de su nivel, sin vinculo`() {
        for (level in 1..2) {
            val changes =
                rewrite(
                    mapOf(
                        "generator_key" to "escalation:grievance:$id:$due:$level",
                        "grievance" to id,
                        "title" to "Escalamiento nivel $level: queja QJ-1 vencida"
                    )
                )
            assertEquals(
                mapOf(
                    "generator_key" to JobTasks.confidentialEscalationKey("grievance", ConfidentialReaders.fingerprint("grievance", id, due), due, level),
                    "title" to "QCS confidencial vencida en Proyecto Ejemplo",
                    "description" to "Una QCS confidencial superó su plazo (escalamiento de nivel $level). Pida el detalle al equipo de quejas.",
                    "grievance" to null
                ),
                changes
            )
        }
        val inquiry = rewrite(mapOf("generator_key" to "escalation:inquiry:$id:$due:1", "inquiry" to id), objectName = "inquiry")
        assertEquals(null, inquiry!!["inquiry"])
        assertTrue(JobTasks.isConfidentialTask(inquiry["generator_key"] as String))
    }

    @Test
    fun `la tarea de acuse de AUT-02 abierta pasa a la generica de acuse`() {
        val changes = rewrite(mapOf("title" to "Acusar recibo de la queja QJ-1", "grievance" to id), state = "in_progress")
        assertEquals(
            mapOf(
                "generator_key" to ConfidentialTasks.ackKey(id, received),
                "title" to "Acusar recibo de una QCS confidencial en Proyecto Ejemplo",
                "description" to "Una QCS confidencial espera su acuse. Pida el detalle al equipo de quejas.",
                "grievance" to null
            ),
            changes
        )
    }

    @Test
    fun `no se tocan las cerradas, las manuales ni las de otro registro`() {
        val escalation = mapOf("generator_key" to "escalation:grievance:$id:$due:1", "grievance" to id)
        for (closed in ModelCatalog.fromClasspath().states("task") - Deadlines.TASK_OPEN) assertNull(rewrite(escalation, state = closed), closed)
        assertNull(rewrite(mapOf("generator_key" to "escalation:grievance:${UUID.randomUUID()}:$due:1", "grievance" to id)))
        assertNull(rewrite(mapOf("generator_key" to "escalation:grievance:$id:$due:3", "grievance" to id)))
        assertNull(rewrite(mapOf("title" to "Llamar a la persona", "grievance" to id)))
        // el acuse de AUT-02 con otro código, con clave, de una consulta o sin fecha de recepción
        assertNull(rewrite(mapOf("title" to "Acusar recibo de la queja QJ-2", "grievance" to id)))
        assertNull(rewrite(mapOf("title" to "Acusar recibo de la queja QJ-1", "grievance" to id, "generator_key" to "otra")))
        assertNull(rewrite(mapOf("title" to "Acusar recibo de la queja QJ-1", "inquiry" to id), objectName = "inquiry"))
        assertNull(ConfidentialTasks.rewrite("grievance", id, "QJ-1", null, "Proyecto Ejemplo", "open", mapOf("title" to "Acusar recibo de la queja QJ-1")))
    }

    // las abiertas que se reescriben son Deadlines.TASK_OPEN: todo estado de wf-n-task que es origen de alguna transición
    @Test
    fun `las abiertas son los estados no finales del workflow de la tarea`() {
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(catalog.transitions("task").map { it.from }.toSet(), Deadlines.TASK_OPEN)
        assertEquals(setOf("done", "cancelled"), catalog.states("task").toSet() - Deadlines.TASK_OPEN)
    }
}
