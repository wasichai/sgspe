package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-J con WarningWriteGuard (workflows.md, especificación de F4 §3.7, RN-EWS-001): los casos propios de la alerta que la matriz
// de workflows no cubre. confirma y descarta una persona con CONFIRM_ALERT; una cuenta de servicio con el rol no; quien no
// tiene la acción tampoco (en la consulta en seco: los roles de confirm la tienen todos); descartar exige motivo
class EarlyWarningWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    // una cuenta de servicio con `role`, asignada al proyecto: su token y su usuario de respaldo (<id>@service-accounts.invalid)
    private fun serviceAccount(role: String): Account {
        val created = tree(send("POST", "/api/service-accounts", mapOf("name" to uniqueName("cuenta-"), "roles" to listOf(role)), HttpStatus.CREATED))
        val issued =
            tree(
                send(
                    "POST",
                    "/api/auth/token",
                    mapOf("clientId" to created["clientId"].asString(), "clientSecret" to created["clientSecret"].asString()),
                    HttpStatus.OK,
                    token = null
                )
            )
        val bearer = "Bearer ${issued["token"].asString()}"
        val me = me(bearer)
        val account = Account(bearer, me["email"].asString(), me["userId"].asString())
        assign(account, testProject(), role)
        return account
    }

    private fun inTriage(): String {
        val id = earlyWarning(testProject(), manager)
        move("early_warning", id, "start_triage", manager.token)
        return id
    }

    @Test
    fun `una persona con CONFIRM_ALERT confirma y descarta con motivo`() {
        val confirmed = inTriage()
        move("early_warning", confirmed, "confirm", manager.token)
        assertEquals("confirmed", state("early_warning", confirmed))
        val dismissed = inTriage()
        val bare = transition("early_warning", dismissed, "dismiss", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, bare.status, bare.body)
        assertEquals("REASON_REQUIRED", code(bare))
        move("early_warning", dismissed, "dismiss", manager.token, "falsa alarma")
        assertEquals("dismissed", state("early_warning", dismissed))
    }

    @Test
    fun `una cuenta de servicio con el rol no confirma ni descarta`() {
        val service = serviceAccount("SOCIAL_RISK_MANAGER")
        val id = inTriage()
        for (name in listOf("confirm", "dismiss")) {
            val response = transition("early_warning", id, name, service.token, "decisión automática")
            assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
            assertEquals("HUMAN_DECISION_REQUIRED", code(response))
        }
        assertEquals("in_triage", state("early_warning", id))
    }

    @Test
    fun `quien no tiene CONFIRM_ALERT no confirma, y la consulta en seco lo dice`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = inTriage()
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "early_warning", "recordId" to id, "transition" to "confirm"),
                    HttpStatus.OK,
                    officer.token
                )
            )
        assertEquals(listOf("ACTION_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        assertEquals(HttpStatus.FORBIDDEN, transition("early_warning", id, "confirm", officer.token).status)
    }
}
