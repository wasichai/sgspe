package sgspe.common

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las guardas transversales que no leen la base, sin servidor: motivo en las transiciones, marca de los comandos y
// «exactamente uno de»
class CrossGuardsTest {
    private fun transition(
        objectName: String,
        name: String,
        reason: String? = null
    ) = RecordWrite(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        objectName,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        transition = name,
        reason = reason
    )

    @Test
    fun `retirar un grupo de interes sin motivo es un 400 sobre reason, y con motivo pasa`() {
        runBlocking {
            val guard = TransitionReasonGuard()
            val error = assertThrows<RuleException> { guard.beforeWrite(transition("stakeholder", "retire_profiled")) }
            assertEquals(400, error.status.value())
            // el campo y el código viajan en errors[0] (P15)
            assertEquals("reason", error.violations.single().field)
            assertEquals("REASON_REQUIRED", error.violations.single().message)
            guard.beforeWrite(transition("stakeholder", "retire_profiled", "ya no es actor del proyecto"))
            guard.beforeWrite(transition("stakeholder", "profile"))
        }
    }

    @Test
    fun `en seco, el motivo faltante es una advertencia y no un bloqueo`() {
        runBlocking {
            val finding = TransitionReasonGuard().findings(transition("field_visit", "cancel"), dryRun = true).single()
            assertFalse(finding.blocking)
            assertEquals("REASON_REQUIRED", finding.code)
        }
    }

    @Test
    fun `la tabla de motivos es la de workflows md para los objetos de F2`() {
        assertEquals(setOf("return_to_draft", "suspend", "cancel_draft", "cancel_in_review"), TransitionReasonGuard.REQUIRED["project"])
        assertEquals(
            setOf("postpone", "return_minutes", "cancel_planned", "cancel_scheduled", "cancel_postponed"),
            TransitionReasonGuard.REQUIRED["engagement"]
        )
        assertEquals(setOf("cancel"), TransitionReasonGuard.REQUIRED["field_visit"])
    }

    @Test
    fun `una transicion de comando sin la marca es un 403, con la marca pasa`() {
        runBlocking {
            val guard = CommandTransitionGuard()
            val refused = assertThrows<RuleException> { guard.beforeWrite(transition("stakeholder", "assess")) }
            assertEquals(403, refused.status.value())
            assertEquals("COMMAND_ONLY", refused.violations.single().message)
            assertEquals(403, assertThrows<RuleException> { guard.beforeWrite(transition("area_of_influence", "supersede")) }.status.value())
            withContext(SgspeWrite) { guard.beforeWrite(transition("stakeholder", "assess")) }
            guard.beforeWrite(transition("stakeholder", "profile"))
            assertTrue(guard.findings(transition("area_of_influence", "approve"), dryRun = true).single().blocking)
        }
    }

    // Review Focus 1 del plan 7.1: apiOnly no rechaza transiciones, así que accept de WF-K de una sugerencia por la API
    // genérica la llevaría a accepted sin ejecutar ningún comando. desde que existe el workflow, solo con la marca
    @Test
    fun `aceptar una sugerencia de IA solo llega por su comando`() {
        runBlocking {
            val guard = CommandTransitionGuard()
            val refused = assertThrows<RuleException> { guard.beforeWrite(transition("ai_suggestion", "accept")) }
            assertEquals(403, refused.status.value())
            assertEquals("COMMAND_ONLY", refused.violations.single().message)
            withContext(SgspeWrite) { guard.beforeWrite(transition("ai_suggestion", "accept")) }
            guard.beforeWrite(transition("ai_suggestion", "reject"))
        }
    }

    @Test
    fun `exactamente uno de`() {
        val group = listOf("person", "organization")
        assertNull(ExactlyOneGuard.violation(group, mapOf("person" to "p-1")))
        val none = ExactlyOneGuard.violation(group, mapOf("person" to null, "organization" to " "))!!
        assertEquals(HttpStatus.BAD_REQUEST, none.status)
        assertEquals("Informe exactamente uno de person, organization", none.message)
        val both = ExactlyOneGuard.violation(group, mapOf("person" to "p-1", "organization" to "o-1"))!!
        assertEquals("person", both.field)
        assertEquals(listOf(listOf("from_person", "from_organization"), listOf("to_person", "to_organization")), ExactlyOneGuard.GROUPS["actor_relationship"])
    }

    @Test
    fun `una tarea sin origen solo pasa con la clave de una tarea confidencial`() {
        val group = ExactlyOneGuard.GROUPS.getValue("task").single()
        val key = "escalation:grievance:0123456789abcdef:2026-10-13:2"
        assertNull(ExactlyOneGuard.violation("task", group, mapOf("project" to "p-1", "generator_key" to key)))
        // también el nivel 1 y la tarea de acuse de una queja confidencial (D-1)
        assertNull(ExactlyOneGuard.violation("task", group, mapOf("project" to "p-1", "generator_key" to key.removeSuffix(":2") + ":1")))
        assertNull(ExactlyOneGuard.violation("task", group, mapOf("project" to "p-1", "generator_key" to "ack:grievance:0123456789abcdef")))
        assertEquals("EXACTLY_ONE", ExactlyOneGuard.violation("task", group, mapOf("generator_key" to "escalation:grievance:g-1:2026-10-13:2"))?.code)
        assertEquals("EXACTLY_ONE", ExactlyOneGuard.violation("task", group, mapOf("generator_key" to "escalation:grievance:g-1:2026-10-13:1"))?.code)
        assertEquals("EXACTLY_ONE", ExactlyOneGuard.violation("task", group, mapOf("generator_key" to "risk_review:r-1:2026-10-13"))?.code)
        assertEquals("EXACTLY_ONE", ExactlyOneGuard.violation("task", group, mapOf("project" to "p-1"))?.code)
        assertEquals("EXACTLY_ONE", ExactlyOneGuard.violation("task", group, mapOf("generator_key" to key, "grievance" to "g-1", "inquiry" to "c-1"))?.code)
        assertNull(ExactlyOneGuard.violation("task", group, mapOf("generator_key" to key, "grievance" to "g-1")))
        // la excepción es solo de task
        assertEquals("EXACTLY_ONE", ExactlyOneGuard.violation("delivery", listOf("commitment"), mapOf("generator_key" to key))?.code)
    }

    @Test
    fun `un hallazgo se vuelve la excepcion de su estado con el codigo en errors 0`() {
        val bad = Finding("X", "m", "f", status = HttpStatus.BAD_REQUEST).toCodedException()
        assertEquals(400, bad.status.value())
        assertEquals("f", bad.violations.single().field)
        assertEquals("X", bad.violations.single().message)
        // un 403 sin campo lleva el código igual, sobre `record`
        val forbidden = Finding("X", "m", status = HttpStatus.FORBIDDEN).toCodedException()
        assertEquals(403, forbidden.status.value())
        assertEquals("X", forbidden.violations.single().message)
        val conflict = Finding("X", "m", "working_crs").toCodedException()
        assertEquals(409, conflict.status.value())
        assertEquals("working_crs", conflict.violations.single().field)
    }
}
