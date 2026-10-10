package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el RecordReadScope de las QCS confidenciales en las rutas de wasichai (security.md §9.6, riesgo 1 de la especificación de
// F4): un relacionista que no es responsable ni la recibió no ve una QCS confidencial en lista, detalle, historial,
// relacionados ni transiciones, y no la cambia ni la transiciona; su responsable, quien la recibió, el gestor de quejas y el
// ADMIN sí; el aprobador solo la queja grave. el alta de una confidencial devuelve el registro a quien la recibió. los
// tableros, el reporte, las exportaciones y los avisos tienen sus propias pruebas
class ConfidentialAttentionApiTest : F4ApiTest() {
    private val receiver by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val colleague by lazy { member("COMMUNITY_RELATIONS_OFFICER") }

    private fun confidential(
        severity: String = "BAJA",
        attributes: Map<String, Any?> = emptyMap()
    ): String = grievance(testProject(), receiver, mapOf("confidential" to true, "severity" to severity) + attributes)

    private fun listed(
        objectName: String,
        account: Account
    ): Set<String> =
        tree(send("GET", "/api/objects/$objectName/records?project=${testProject()}&size=200", null, HttpStatus.OK, account.token))["content"]
            .values()
            .map { it["id"].asString() }
            .toSet()

    @Test
    fun `quien la recibio la ve desde el alta, y otro relacionista no la ve por ninguna ruta`() {
        val id = confidential()
        val open = grievance(testProject(), receiver)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/grievance/records/$id", null, receiver.token).status)
        assertTrue(id in listed("grievance", receiver))
        assertEquals(setOf(open), listed("grievance", colleague) intersect setOf(id, open))
        for (path in listOf(
            "/api/objects/grievance/records/$id",
            "/api/objects/grievance/records/$id/history",
            "/api/objects/grievance/records/$id/transitions"
        )) {
            assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, colleague.token).status, path)
        }
        val related =
            tree(send("GET", "/api/objects/project/records/${testProject()}/related/grievance_project?size=200", null, HttpStatus.OK, colleague.token))
        assertFalse(related["content"].values().any { it["id"].asString() == id })
    }

    @Test
    fun `quien no la ve no la cambia ni la transiciona`() {
        val id = confidential()
        assertEquals(HttpStatus.NOT_FOUND, transitionWith("grievance", id, "acknowledge", colleague.token, reason = "acuse").status)
        val put = exchange("PUT", "/api/objects/grievance/records/$id", mapOf("attributes" to mapOf("priority" to "ALTA")), colleague.token, withReason())
        assertEquals(HttpStatus.NOT_FOUND, put.status, put.body)
        assertEquals("received", state("grievance", id))
    }

    @Test
    fun `su responsable, el gestor de quejas y el ADMIN la ven`() {
        val owner = member("COMMUNITY_RELATIONS_OFFICER")
        val id = confidential(attributes = mapOf("owner_user_id" to owner.id, "owner_user_email" to owner.email))
        assertTrue(id in listed("grievance", owner))
        assertTrue(id in listed("grievance", member("GRIEVANCE_MANAGER")))
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/grievance/records/$id", null, token).status)
    }

    @Test
    fun `el aprobador solo ve la queja confidencial grave`() {
        val low = confidential("MEDIA")
        val high = confidential("ALTA")
        val approver = member("APPROVER")
        val seen = listed("grievance", approver)
        assertTrue(high in seen)
        assertFalse(low in seen)
        // una consulta confidencial no tiene severidad: el aprobador no la ve
        val inquiry = inquiry(testProject(), receiver, mapOf("confidential" to true))
        assertFalse(inquiry in listed("inquiry", approver))
    }
}
