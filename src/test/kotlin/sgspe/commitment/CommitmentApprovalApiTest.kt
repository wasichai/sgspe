package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// CommitmentWriteGuard en la aprobación (WF-D D1 a D5 y D8) y la consulta en seco: los casos que CommitmentWorkflowApiTest (3.1)
// deja a 3.2
class CommitmentApprovalApiTest : F3DomainApiTest() {
    @Test
    fun `submit exige cada campo de la matriz, el responsable y un solicitante, y execution_time no`() {
        val project = testProject()
        val noObjective = commitment(project, attributes = mapOf("objective" to null))
        val refused = transition("commitment", noObjective, "submit", reason = "envío")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("FIELD_REQUIRED", code(refused))
        assertEquals("objective", problemFields(refused.body).single())
        // el alta directa, sin el solicitante que vincula commitment()
        val attributes = commitmentAttributes(project, userWithRole("COMMITMENT_MANAGER"), userWithRole("COMMITMENT_MANAGER"))
        val alone = createRecord("commitment", attributes, withReason("alta de prueba"))
        assertEquals("STAKEHOLDER_REQUIRED", code(transition("commitment", alone, "submit", reason = "envío")))
        val noTime = commitment(project, attributes = mapOf("execution_time" to null))
        assertEquals(HttpStatus.OK, transition("commitment", noTime, "submit", reason = "envío").status)
    }

    @Test
    fun `quien registro no aprueba, y sin la accion APPROVE tampoco`() {
        val project = testProject()
        // APPROVER no tiene CREATE sobre commitment: quien registra y aprueba a la vez tiene los dos roles (security.md §5.4)
        val approver = userWithRoles(listOf("COMMITMENT_MANAGER", "APPROVER")).also { assign(it, project, "APPROVER") }
        val own = commitment(project, creator = approver)
        move("commitment", own, "submit", reason = "envío")
        val segregation = transition("commitment", own, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.FORBIDDEN, segregation.status, segregation.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(segregation))
        assertEquals("pending_approval", state("commitment", own))
        // la consulta en seco de quien no tiene la acción APPROVE (los roles los explica la lista de transiciones)
        val manager = member("COMMITMENT_MANAGER", project)
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "commitment", "recordId" to own, "transition" to "approve"),
                    HttpStatus.OK,
                    manager.token
                )
            )
        assertEquals(listOf("ACTION_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        // otra persona con APPROVE sí aprueba
        assertEquals(HttpStatus.OK, transition("commitment", own, "approve", member("APPROVER", project).token, "aprobación").status)
    }

    // un usuario con varios roles a la vez, con su id para asignarlo (security.md §5.4)
    private fun userWithRoles(roles: List<String>): Account {
        val email = "${uniqueName("dos-roles")}@sgspe.test"
        val created =
            tree(
                send(
                    "POST",
                    "/api/users",
                    mapOf("email" to email, "displayName" to "Usuario de prueba", "password" to PASSWORD, "roles" to roles),
                    HttpStatus.CREATED
                )
            )
        return Account(bearer(email, PASSWORD), email, created["id"].asString())
    }

    @Test
    fun `un compromiso de una mesa de dialogo solo se aprueba en nivel superior`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        move("commitment", id, "submit", reason = "envío")
        val approve = transition("commitment", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, approve.status, approve.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(approve))
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve_senior", member("PROJECT_MANAGER", project).token, "aprobación superior").status)
    }

    @Test
    fun `approve_direct solo con la aprobacion interna desactivada`() {
        val active = testProject()
        val id = commitment(active)
        val manager = member("COMMITMENT_MANAGER", active)
        val refused = transition("commitment", id, "approve_direct", manager.token, "directa")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val direct = commitment(relaxed)
        assertEquals(HttpStatus.OK, transition("commitment", direct, "approve_direct", member("COMMITMENT_MANAGER", relaxed).token, "directa").status)
    }

    @Test
    fun `la consulta en seco dice lo mismo que la escritura real`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("unit" to null))
        val check =
            tree(send("POST", "/api/sgspe/transition-checks", mapOf("object" to "commitment", "recordId" to id, "transition" to "submit"), HttpStatus.OK))
        assertEquals(false, check["allowed"].asBoolean())
        assertEquals(listOf("FIELD_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        assertEquals("unit", check["blocking"][0]["field"].asString())
    }
}
