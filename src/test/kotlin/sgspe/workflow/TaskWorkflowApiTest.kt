package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// WF-N (workflows.md): la tarea se inicia, se bloquea, se desbloquea y se completa, también desde `open`, y se cancela
// desde cada estado abierto, por los «Roles operativos» que tienen permisos en F3. cada tarea lleva un único origen
// (ExactlyOneGuard) y cada cancelación su motivo (TransitionReasonGuard, ya activo para task); `deadline_status`, las
// tareas de escalamiento y las de JOB-03, que no se duplican por `generator_key`, los prueba el plan 3.2
class TaskWorkflowApiTest : F3ApiTest() {
    @Test
    fun `FIELD_AGENT inicia, bloquea, desbloquea y completa una tarea`() {
        val agent = member("FIELD_AGENT")
        val id = task(testProject(), agent)
        assertEquals("open", state("task", id))
        for (step in listOf("start", "block", "unblock", "complete")) move("task", id, step, agent.token)
        assertEquals("done", state("task", id))
    }

    @Test
    fun `una tarea se completa sin iniciarla y se cancela desde cada estado abierto con motivo`() {
        val gis = member("GIS_ANALYST")
        val approver = member("APPROVER")
        val quick = task(testProject(), gis)
        move("task", quick, "complete_open", approver.token)
        val open = task(testProject(), gis)
        assertEquals(HttpStatus.BAD_REQUEST, transition("task", open, "cancel_open", gis.token).status)
        assertEquals("open", state("task", open))
        move("task", open, "cancel_open", gis.token, "ya no hace falta")
        val running = task(testProject(), gis).also { move("task", it, "start", gis.token) }
        move("task", running, "cancel_in_progress", approver.token, "la reunión se suspendió")
        val blocked =
            task(testProject(), gis).also {
                move("task", it, "start", gis.token)
                move("task", it, "block", gis.token)
            }
        move("task", blocked, "cancel_blocked", gis.token, "sin acceso a la zona")
        assertEquals(listOf("done", "cancelled", "cancelled", "cancelled"), listOf(quick, open, running, blocked).map { state("task", it) })
    }

    @Test
    fun `los roles de solo lectura no mueven tareas y desde un estado final responde 409`() {
        val id = task(testProject())
        // AUDITOR no tiene permisos sobre task: wasichai exige UPDATE antes de mirar el registro, así que responde 403
        for (role in listOf("ANALYST", "READ_ONLY", "AUDITOR")) {
            assertEquals(HttpStatus.FORBIDDEN, transition("task", id, "start", member(role).token).status, role)
        }
        val agent = member("FIELD_AGENT")
        assertEquals(HttpStatus.CONFLICT, transition("task", id, "unblock", agent.token).status)
        move("task", id, "start", agent.token)
        assertEquals(HttpStatus.CONFLICT, transition("task", id, "complete_open", agent.token).status)
        move("task", id, "complete", agent.token)
        assertEquals(HttpStatus.CONFLICT, transition("task", id, "start", agent.token).status)
        assertTrue(available("task", id, agent.token).isEmpty(), "una tarea hecha no ofrece transiciones")
    }

    @Test
    fun `PROJECT_ADMIN trabaja tareas pero no las crea`() {
        // security.md §7.5 (plan 3.1): PROJECT_ADMIN no lee ningún origen de una tarea, así que tiene RU y no CRU
        val admin = member("PROJECT_ADMIN")
        val create = exchange("POST", "/api/objects/task/records", mapOf("attributes" to mapOf("project" to testProject(), "title" to "x")), admin.token)
        assertEquals(HttpStatus.FORBIDDEN, create.status, create.body)
        val id = task(testProject())
        move("task", id, "start", admin.token)
    }

    @Test
    fun `la clave de un generador es unica`() {
        val key = uniqueName("escalation:commitment:")
        task(testProject(), attributes = mapOf("generator_key" to key))
        val repeated =
            exchange(
                "POST",
                "/api/objects/task/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Otra", "engagement" to engagement(testProject()), "generator_key" to key))
            )
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
    }
}
