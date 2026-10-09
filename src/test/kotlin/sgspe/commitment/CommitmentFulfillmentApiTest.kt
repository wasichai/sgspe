package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// D9, D10 y D11 de WF-D (RN-COM-001, ASM-009, RF-COM-004, RN-COM-005)
class CommitmentFulfillmentApiTest : F3DomainApiTest() {
    private fun delivery(
        commitment: String,
        conformityBy: String? = "Junta directiva",
        evidence: List<String> = emptyList()
    ): String {
        val response =
            exchange(
                "POST",
                "/api/sgspe/commitments/$commitment/deliveries",
                mapOf(
                    "attributes" to
                        mapOf("delivery_date" to "2026-10-01", "conformity_given" to true) + listOfNotNull(conformityBy?.let { "conformity_by" to it }),
                    "evidence" to evidence
                ),
                token,
                withReason()
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["delivery"]["id"].asString()
    }

    @Test
    fun `MATERIAL sin foto se rechaza, con acta y foto pasa y fecha fulfilled_date, y reabrir la vacia`() {
        val project = testProject()
        val id = approvedCommitment(project)
        delivery(id, evidence = listOf(evidence(project, "ACTA DE ENTREGA")))
        val refused = transition("commitment", id, "register_fulfillment", reason = "entregado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("EVIDENCE_REQUIRED", code(refused))
        delivery(id, evidence = listOf(evidence(project, "ACTA DE ENTREGA"), evidence(project, "FOTO", "PUBLICA")))
        move("commitment", id, "register_fulfillment", reason = "entregado")
        assertTrue(record("commitment", id)["attributes"]["fulfilled_date"].asString().isNotBlank())
        move("commitment", id, "reopen_execution", reason = "falta un tramo")
        assertTrue(record("commitment", id)["attributes"]["fulfilled_date"].let { it == null || it.isNull })
    }

    @Test
    fun `NO MATERIAL exige quienes dieron la conformidad y una carta, un acta u otro`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        delivery(id, conformityBy = null, evidence = listOf(evidence(project, "CARTA")))
        assertEquals("DELIVERY_REQUIRED", code(transition("commitment", id, "register_fulfillment", reason = "entregado")))
        delivery(id, evidence = listOf(evidence(project, "CARTA")))
        assertEquals(HttpStatus.OK, transition("commitment", id, "register_fulfillment", reason = "entregado").status)
    }

    // la evidencia de otro proyecto vinculada a la entrega no sostiene el cumplimiento (P54)
    @Test
    fun `la evidencia de otro proyecto no cuenta para registrar el cumplimiento`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        delivery(id, evidence = listOf(evidence(project(), "CARTA")))
        val refused = transition("commitment", id, "register_fulfillment", reason = "entregado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("EVIDENCE_REQUIRED", code(refused))
        assertEquals("in_progress", state("commitment", id))
    }

    @Test
    fun `una dependencia bloqueante abierta impide registrar el cumplimiento`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        val other = commitment(project)
        createRecord("commitment_dependency", mapOf("commitment" to id, "depends_on" to other, "blocking" to true))
        delivery(id, evidence = listOf(evidence(project, "OTRO")))
        val refused = transition("commitment", id, "register_fulfillment", reason = "entregado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("BLOCKING_DEPENDENCY", code(refused))
        // cancelado, el compromiso del que depende deja de bloquear (D10)
        move("commitment", other, "cancel_draft", reason = "ya no aplica")
        move("commitment", id, "register_fulfillment", reason = "entregado")
    }

    // D10 con un compromiso de otro proyecto: hoy la lectura es de todo el tenant (CON-041, opción A, hasta UPS-01), así que
    // quien registra sin asignación a ese proyecto lo lee y, abierto, bloquea como cualquier otro. que un compromiso que no se
    // lee falle cerrado (OBJECT_NOT_READABLE) lo prueba BlockingDependencyRuleTest: ningún rol que registra deja de leerlo
    @Test
    fun `una dependencia bloqueante hacia un compromiso de otro proyecto tambien impide registrar el cumplimiento`() {
        val project = testProject()
        val manager = member("COMMITMENT_MANAGER", project)
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        createRecord("commitment_dependency", mapOf("commitment" to id, "depends_on" to commitment(project()), "blocking" to true))
        delivery(id, evidence = listOf(evidence(project, "OTRO")))
        val refused = transition("commitment", id, "register_fulfillment", manager.token, "entregado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("BLOCKING_DEPENDENCY", code(refused))
        assertEquals("commitment_dependency", tree(refused.body)["errors"][0]["field"].asString())
        assertEquals("in_progress", state("commitment", id))
    }

    // sin tipo de recurso, D9 no supone NO MATERIAL: falla cerrada
    @Test
    fun `sin tipo de recurso no se registra el cumplimiento`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        delivery(id, evidence = listOf(evidence(project, "CARTA")))
        assertEquals(HttpStatus.OK, edit("commitment", id, mapOf("resource_type" to null), headers = withReason()).status)
        val refused = transition("commitment", id, "register_fulfillment", reason = "entregado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("FIELD_REQUIRED", code(refused))
        assertEquals("resource_type", tree(refused.body)["errors"][0]["field"].asString())
    }

    @Test
    fun `el responsable no verifica su compromiso, otro gestor si, y cerrar fecha closed_date`() {
        val project = testProject()
        val owner = member("COMMITMENT_MANAGER", project)
        val id = approvedCommitment(project, owner, mapOf("resource_type" to "NO MATERIAL"))
        delivery(id, evidence = listOf(evidence(project, "ACTA")))
        move("commitment", id, "register_fulfillment", reason = "entregado")
        val own = transition("commitment", id, "verify", owner.token, "verificado")
        assertEquals(HttpStatus.FORBIDDEN, own.status, own.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(own))
        val verifier = member("COMMITMENT_MANAGER", project)
        assertEquals(HttpStatus.OK, transition("commitment", id, "verify", verifier.token, "verificado").status)
        move("commitment", id, "close", verifier.token, "cerrado")
        assertTrue(record("commitment", id)["attributes"]["closed_date"].asString().isNotBlank())
    }

    // P55: cancelado es un estado final, como cumplido: sus entregas ya no se corrigen
    @Test
    fun `las entregas de un compromiso cancelado quedan congeladas`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        val delivery = delivery(id)
        move("commitment", id, "cancel_in_progress", member("APPROVER", project).token, "se cancela")
        val frozen =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("delivered_amount" to 130)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.CONFLICT, frozen.status, frozen.body)
        assertEquals("DELIVERY_FROZEN", code(frozen))
    }
}
