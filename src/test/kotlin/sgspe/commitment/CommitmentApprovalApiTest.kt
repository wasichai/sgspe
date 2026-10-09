package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import tools.jackson.databind.JsonNode

// CommitmentWriteGuard en la aprobación (WF-D D1 a D5 y D8) y la consulta en seco: los casos que CommitmentWorkflowApiTest (3.1)
// deja a 3.2 (workflows.md, «Pruebas», WF-D)
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
    fun `submit sin responsable es FIELD_REQUIRED y el responsable no queda a medias (D2)`() {
        val project = testProject()
        // UserReferenceGuard exige el par entero: el correo solo no se puede vaciar, así que D2 se ve con el par vacío
        val id = commitment(project, attributes = mapOf("owner_user_id" to null, "owner_user_email" to null))
        val refused = transition("commitment", id, "submit", reason = "envío")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("FIELD_REQUIRED", code(refused))
        assertEquals("owner_user_id", problemFields(refused.body).single())
        val half = edit("commitment", commitment(project), mapOf("owner_user_email" to null), headers = withReason("sin correo"))
        assertEquals(HttpStatus.BAD_REQUEST, half.status, half.body)
        assertEquals("owner_user_email", problemFields(half.body).single())
    }

    @Test
    fun `vaciar el responsable o due_date en pending_approval hace que approve se rechace (D1, D2)`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val cases =
            listOf(
                mapOf("owner_user_id" to null, "owner_user_email" to null) to "owner_user_id",
                mapOf("due_date" to null) to "due_date"
            )
        for ((changes, field) in cases) {
            val id = commitment(project)
            move("commitment", id, "submit", reason = "envío")
            // pending_approval admite cambios: por eso D1 y D2 se vuelven a comprobar al aprobar
            val cleared = edit("commitment", id, changes, headers = withReason("corrección en revisión"))
            assertEquals(HttpStatus.OK, cleared.status, cleared.body)
            val refused = transition("commitment", id, "approve", approver.token, "aprobación")
            assertEquals(HttpStatus.CONFLICT, refused.status, "$field: ${refused.body}")
            assertEquals("FIELD_REQUIRED", code(refused), field)
            assertEquals(field, problemFields(refused.body).single())
            assertEquals("pending_approval", state("commitment", id))
        }
    }

    @Test
    fun `quien registro no aprueba, y sin la accion APPROVE tampoco`() {
        val project = testProject()
        // APPROVER no tiene CREATE sobre commitment: quien registra y aprueba a la vez tiene los dos roles (security.md §5.4)
        val approver = userWithRoles(listOf("COMMITMENT_MANAGER", "APPROVER")).also { assign(it, project, "APPROVER") }
        val own = commitment(project, creator = approver)
        move("commitment", own, "submit", reason = "envío")
        // la consulta en seco del propio registrador dice lo mismo que la escritura, con el mismo campo
        assertEquals(listOf("SEGREGATION_OF_DUTIES" to "record"), blocking(check(own, "approve", approver.token)))
        val segregation = transition("commitment", own, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.FORBIDDEN, segregation.status, segregation.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(segregation))
        assertEquals("record", problemFields(segregation.body).single())
        assertEquals("pending_approval", state("commitment", own))
        // la consulta en seco de quien no tiene la acción APPROVE (los roles los explica la lista de transiciones)
        val manager = member("COMMITMENT_MANAGER", project)
        assertEquals(listOf("ACTION_REQUIRED" to "record"), blocking(check(own, "approve", manager.token)))
        // otra persona con APPROVE sí aprueba
        assertEquals(HttpStatus.OK, transition("commitment", own, "approve", member("APPROVER", project).token, "aprobación").status)
        // y la misma cuenta de dos roles aprueba un compromiso ajeno: el 403 venía de la identidad, no de la cuenta
        val other = commitment(project)
        move("commitment", other, "submit", reason = "envío")
        assertEquals(HttpStatus.OK, transition("commitment", other, "approve", approver.token, "aprobación").status)
    }

    @Test
    fun `quien registro tampoco aplica approve_senior (D4)`() {
        val project = testProject()
        val manager = userWithRoles(listOf("COMMITMENT_MANAGER", "PROJECT_MANAGER")).also { assign(it, project, "PROJECT_MANAGER") }
        val id = commitment(project, creator = manager)
        move("commitment", id, "submit", reason = "envío")
        val refused = transition("commitment", id, "approve_senior", manager.token, "aprobación superior")
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(refused))
        assertEquals("pending_approval", state("commitment", id))
    }

    @Test
    fun `un compromiso de una mesa de dialogo solo se aprueba en nivel superior`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        move("commitment", id, "submit", reason = "envío")
        val approve = transition("commitment", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, approve.status, approve.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(approve))
        assertEquals("origin_engagement", problemFields(approve.body).single())
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve_senior", member("PROJECT_MANAGER", project).token, "aprobación superior").status)
    }

    @Test
    fun `un solicitante POLITICO tambien exige nivel superior`() {
        val project = testProject()
        val id = commitment(project)
        // influence_group es derivado: lo escribe sgspe (la evaluación), aquí la plataforma
        val political =
            platformCreate(
                "stakeholder",
                mapOf("project" to project, "stakeholder_scope" to "AIDS", "organization" to organization(), "influence_group" to "POLITICO")
            )
        val linked = linkWithReason("commitment", id, Commitments.STAKEHOLDERS, political)
        assertEquals(HttpStatus.NO_CONTENT, linked.status, linked.body)
        move("commitment", id, "submit", reason = "envío")
        val approve = transition("commitment", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, approve.status, approve.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(approve))
        assertEquals(Commitments.STAKEHOLDERS, problemFields(approve.body).single())
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve_senior", member("PROJECT_MANAGER", project).token, "aprobación superior").status)
    }

    @Test
    fun `approve_direct tampoco aprueba lo que exige nivel superior, y con la politica apagada no se exige (D5)`() {
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val dialogue = commitment(relaxed, attributes = mapOf("origin_engagement" to engagement(relaxed, "MESA DE DIALOGO")))
        val direct = transition("commitment", dialogue, "approve_direct", member("COMMITMENT_MANAGER", relaxed).token, "directa")
        assertEquals(HttpStatus.CONFLICT, direct.status, direct.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(direct))
        assertEquals("draft", state("commitment", dialogue))
        val off = project(mapOf("policy_senior_approval_for_dialogue" to false))
        val id = commitment(off, attributes = mapOf("origin_engagement" to engagement(off, "MESA DE DIALOGO")))
        move("commitment", id, "submit", reason = "envío")
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve", member("APPROVER", off).token, "aprobación").status)
    }

    @Test
    fun `approve_direct solo con la aprobacion interna desactivada, y D8 se informa primero`() {
        val active = testProject()
        val manager = member("COMMITMENT_MANAGER", active)
        // incompleto y de una mesa de diálogo: aun así, lo primero que se dice es que el proyecto no admite la vía directa (P53)
        val id = commitment(active, attributes = mapOf("unit" to null, "origin_engagement" to engagement(active, "MESA DE DIALOGO")))
        val refused = transition("commitment", id, "approve_direct", manager.token, "directa")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        assertEquals("project", problemFields(refused.body).single())
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val direct = commitment(relaxed)
        assertEquals(HttpStatus.OK, transition("commitment", direct, "approve_direct", member("COMMITMENT_MANAGER", relaxed).token, "directa").status)
    }

    @Test
    fun `la consulta en seco y la escritura dan el mismo codigo y el mismo campo`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("unit" to null))
        val check = check(id, "submit", token)
        assertEquals(false, check["allowed"].asBoolean())
        assertEquals(listOf("FIELD_REQUIRED" to "unit"), blocking(check))
        val written = transition("commitment", id, "submit", reason = "envío")
        assertEquals(HttpStatus.CONFLICT, written.status, written.body)
        assertEquals(blocking(check).single(), code(written) to problemFields(written.body).single())
        assertEquals("draft", state("commitment", id))
    }

    // POST /api/sgspe/transition-checks de `transition` sobre el compromiso `id`, como `token`
    private fun check(
        id: String,
        transition: String,
        token: String
    ): JsonNode =
        tree(
            send(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "commitment", "recordId" to id, "transition" to transition),
                HttpStatus.OK,
                token
            )
        )

    // los bloqueos de una consulta en seco: código y campo
    private fun blocking(check: JsonNode): List<Pair<String, String?>> =
        check["blocking"].values().map { it["code"].asString() to it["field"]?.takeUnless { f -> f.isNull }?.asString() }
}
