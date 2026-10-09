package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

// SupportPolicyGuard, D7 de WF-D (RN-INV-003, ASM-063): el tope de la campaña bloquea salvo con APPROVE_OVER_CAP, o advierte
// con la política apagada
class SupportPolicyApiTest : F3DomainApiTest() {
    private fun campaign(
        project: String,
        cap: Int
    ): String =
        createRecord("exploration_campaign", mapOf("project" to project, "name" to "Campaña de prueba", "social_support_cap" to cap, "currency" to "PEN"))

    @Test
    fun `con el tope que bloquea, approve se rechaza y approve_senior con APPROVE_OVER_CAP pasa`() {
        val project = testProject()
        val campaign = campaign(project, 1500)
        approvedCommitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 1000), start = false)
        val over = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 600))
        move("commitment", over, "submit", reason = "envío")
        val refused = transition("commitment", over, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        assertEquals(HttpStatus.OK, transition("commitment", over, "approve_senior", member("PROJECT_MANAGER", project).token, "sobre el tope").status)
    }

    @Test
    fun `con la politica apagada el tope superado es una advertencia en la consulta en seco y la aprobacion pasa`() {
        val project = project(mapOf("policy_cap_blocks_approval" to false))
        val campaign = campaign(project, 100)
        val id = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 600))
        move("commitment", id, "submit", reason = "envío")
        val approver = member("APPROVER", project)
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "commitment", "recordId" to id, "transition" to "approve"),
                    HttpStatus.OK,
                    approver.token
                )
            )
        assertTrue(check["warnings"].values().any { it["code"].asString() == "SUPPORT_CAP_EXCEEDED" }, check.toString())
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve", approver.token, "aprobación").status)
    }

    // la entrada del Review Focus 4: dos aprobaciones simultáneas de la misma campaña no pasan el tope juntas, porque el candado
    // de la campaña las pone en fila dentro de la transacción de la transición
    @Test
    fun `dos aprobaciones simultaneas de la misma campana no pasan el tope juntas`() {
        val project = testProject()
        val campaign = campaign(project, 1000)
        val ids = List(2) { commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 600)) }
        ids.forEach { move("commitment", it, "submit", reason = "envío") }
        val approver = member("APPROVER", project)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val statuses =
            try {
                val calls =
                    ids.map { id ->
                        pool.submit<HttpStatus> {
                            start.await()
                            transition("commitment", id, "approve", approver.token, "aprobación").status
                        }
                    }
                start.countDown()
                calls.map { it.get() }
            } finally {
                pool.shutdown()
            }
        assertEquals(listOf(HttpStatus.CONFLICT, HttpStatus.OK), statuses.sortedBy { it.value() }.reversed())
        assertEquals(1, ids.count { record("commitment", it)["state"].asString() == "approved" })
    }
}
