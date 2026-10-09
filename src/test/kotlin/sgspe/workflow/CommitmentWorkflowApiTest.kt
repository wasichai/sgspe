package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// WF-D (workflows.md): el ciclo con aprobación interna, la aprobación de nivel superior, la devolución, las cinco
// cancelaciones, el motivo que exige `requiresReason` y lo que el listado dice a cada rol. cada compromiso trae lo que
// piden D1, D2 y D11, y aprueba alguien que no lo registró (D4), para que la clase siga en verde con las guardas del plan
// 3.2. los casos de CommitmentWriteGuard y SupportPolicyGuard (D1 a D11: campos mínimos, responsable, doble llave,
// segregación, nivel superior, empleo, tope, `approve_direct` con la política, entrega con conformidad y evidencia,
// dependencias, verificación), los derivados de CommitmentDerivationsListener y `register_fulfillment`, `reopen_execution`,
// `verify` y `close`, que necesitan la entrega con evidencia del plan 3.2, los prueba ese plan en
// CommitmentWorkflowGuardsApiTest
class CommitmentWorkflowApiTest : F3ApiTest() {
    private val manager by lazy { member("COMMITMENT_MANAGER") }
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val social by lazy { member("SOCIAL_MANAGER") }
    private val approver by lazy { member("APPROVER") }
    private val senior by lazy { member("PROJECT_MANAGER") }

    // un compromiso de COMMITMENT_MANAGER con COMMUNITY_RELATIONS_OFFICER de responsable y SOCIAL_MANAGER de verificador
    private fun draft(): String = commitment(testProject(), owner = officer, verifier = social, creator = manager)

    @Test
    fun `un compromiso se envia, se aprueba, se ejecuta, se bloquea y se desbloquea, siempre con motivo`() {
        val id = draft()
        assertEquals("draft", state("commitment", id))
        move("commitment", id, "submit", manager.token, "matriz completa")
        move("commitment", id, "approve", approver.token, "dentro del presupuesto")
        assertEquals("approved", state("commitment", id))
        move("commitment", id, "start", officer.token, "inicio de obra")
        move("commitment", id, "block", manager.token, "lluvias en la zona")
        assertEquals("blocked", state("commitment", id))
        move("commitment", id, "unblock", manager.token, "pasaron las lluvias")
        assertEquals("in_progress", state("commitment", id))

        val reasons = tree(send("GET", "/api/objects/commitment/records/$id/history", null, HttpStatus.OK)).values().map { it["reason"]?.asString() }
        assertTrue(reasons.containsAll(listOf("matriz completa", "dentro del presupuesto", "lluvias en la zona")), reasons.toString())
    }

    @Test
    fun `PROJECT_MANAGER devuelve un compromiso y lo aprueba en nivel superior`() {
        val id = draft()
        move("commitment", id, "submit", officer.token, "pedido de la junta")
        move("commitment", id, "return_to_draft", senior.token, "falta la unidad")
        assertEquals("draft", state("commitment", id))
        move("commitment", id, "submit", officer.token, "unidad corregida")
        move("commitment", id, "approve_senior", senior.token, "mesa de diálogo")
        assertEquals("approved", state("commitment", id))
    }

    @Test
    fun `cada estado abierto se cancela con su transicion y su rol`() {
        val cancelled = mutableListOf<String>()
        val inDraft = draft()
        move("commitment", inDraft, "cancel_draft", manager.token, "registro duplicado")
        cancelled += inDraft
        val pending = draft().also { move("commitment", it, "submit", manager.token, "matriz completa") }
        move("commitment", pending, "cancel_pending_approval", approver.token, "fuera de las políticas")
        cancelled += pending
        val approved =
            draft().also {
                move("commitment", it, "submit", manager.token, "matriz completa")
                move("commitment", it, "approve", approver.token, "aprobado")
            }
        move("commitment", approved, "cancel_approved", senior.token, "la comunidad desistió")
        cancelled += approved
        val running =
            draft().also {
                move("commitment", it, "submit", manager.token, "matriz completa")
                move("commitment", it, "approve", approver.token, "aprobado")
                move("commitment", it, "start", manager.token, "inicio")
            }
        move("commitment", running, "cancel_in_progress", approver.token, "reemplazado por otro compromiso")
        cancelled += running
        val blocked =
            draft().also {
                move("commitment", it, "submit", manager.token, "matriz completa")
                move("commitment", it, "approve", approver.token, "aprobado")
                move("commitment", it, "start", manager.token, "inicio")
                move("commitment", it, "block", manager.token, "sin acceso")
            }
        move("commitment", blocked, "cancel_blocked", senior.token, "el acceso no se recuperó")
        cancelled += blocked
        assertEquals(List(5) { "cancelled" }, cancelled.map { state("commitment", it) })
    }

    @Test
    fun `sin motivo una transicion responde 400 en reason y el compromiso no cambia`() {
        // commitment es requiresReason (ASM-058): lo exige wasichai antes que cualquier guarda
        val id = draft()
        val response = transition("commitment", id, "submit", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("reason"), problemFields(response.body))
        assertEquals("draft", state("commitment", id))
    }

    @Test
    fun `quien no actualiza recibe 403, un rol fuera de la transicion tambien, y desde otro estado responde 409`() {
        val id = draft()
        // FIELD_AGENT no tiene permisos sobre commitment; ANALYST solo lo lee
        assertEquals(HttpStatus.FORBIDDEN, transition("commitment", id, "submit", member("FIELD_AGENT").token, "x").status)
        assertEquals(HttpStatus.FORBIDDEN, transition("commitment", id, "submit", member("ANALYST").token, "x").status)
        // APPROVER tiene `u` y aprueba, pero no envía
        assertEquals(HttpStatus.FORBIDDEN, transition("commitment", id, "submit", approver.token, "x").status)
        // approve sale de pending_approval
        assertEquals(HttpStatus.CONFLICT, transition("commitment", id, "approve", approver.token, "x").status)
        assertEquals(HttpStatus.NOT_FOUND, transition("commitment", id, "fulfil", manager.token, "x").status)
        assertEquals("draft", state("commitment", id))
    }

    @Test
    fun `el listado explica a cada rol que puede y que no`() {
        val id = draft()
        val forManager = available("commitment", id, manager.token)
        assertEquals(setOf("submit", "approve_direct", "cancel_draft"), forManager.keys)
        assertTrue(forManager.values.all { it == (true to null) }, forManager.toString())
        val forApprover = available("commitment", id, approver.token)
        assertEquals(false to "requires role COMMITMENT_MANAGER or COMMUNITY_RELATIONS_OFFICER or SOCIAL_MANAGER", forApprover["submit"])
        val forAnalyst = available("commitment", id, member("ANALYST").token)
        assertTrue(forAnalyst.values.all { it == (false to "requires permission UPDATE") }, forAnalyst.toString())
    }

    @Test
    fun `el codigo es unico en el proyecto y el monto es obligatorio desde el alta`() {
        // uniqueConstraints (project, code) y `amount` requerido (RN-COM-004): metadata, sin guardas
        val attributes = commitmentAttributes(testProject(), officer, social)
        createRecord("commitment", attributes, mapOf(REASON to reason("alta de prueba")))
        val repeated = exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to attributes), headers = mapOf(REASON to reason("otra vez")))
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
        val withoutAmount =
            exchange(
                "POST",
                "/api/objects/commitment/records",
                mapOf("attributes" to attributes - "amount" + ("code" to uniqueName("COM-"))),
                headers = mapOf(REASON to reason("sin monto"))
            )
        assertEquals(HttpStatus.BAD_REQUEST, withoutAmount.status, withoutAmount.body)
        assertEquals(listOf("amount"), problemFields(withoutAmount.body))
    }
}
