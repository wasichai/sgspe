package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// CommitmentWriteGuard, alta y edición (RN-ENG-001, RF-COM-003, RF-COM-004): por la API genérica, como el portal
class CommitmentRecordRulesApiTest : F3DomainApiTest() {
    // los atributos de un compromiso de F3ApiTest (D1 y D2), con un responsable y un verificador nuevos
    private fun attributes(project: String): Map<String, Any?> =
        commitmentAttributes(project, userWithRole("COMMITMENT_MANAGER"), userWithRole("COMMITMENT_MANAGER"))

    @Test
    fun `un compromiso de un taller exige justificacion, y con la politica que bloquea se rechaza`() {
        val project = testProject()
        val workshop = engagement(project, "TALLER")
        val body = attributes(project) + ("origin_engagement" to workshop)
        val missing = exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to body), token, withReason())
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.body)
        assertEquals("JUSTIFICATION_REQUIRED", code(missing))
        val justified = body + ("origin_justification" to "Absuelve una pregunta pendiente") + ("code" to uniqueName("C-"))
        assertEquals(HttpStatus.CREATED, exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to justified), token, withReason()).status)
        val strict = project(mapOf("policy_workshop_commitments_blocked" to true))
        val blocked = attributes(strict) + ("origin_engagement" to engagement(strict, "TALLER")) + ("origin_justification" to "x")
        val refused = exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to blocked), token, withReason())
        assertEquals(HttpStatus.CONFLICT, refused.status)
        assertEquals("WORKSHOP_COMMITMENT_BLOCKED", code(refused))
    }

    @Test
    fun `desde approved la fecha de vencimiento no cambia por la API generica`() {
        val project = testProject()
        val draft = commitment(project)
        assertEquals(HttpStatus.OK, edit("commitment", draft, mapOf("due_date" to "2027-01-10"), headers = withReason()).status)
        val approved = approvedCommitment(project)
        val moved = edit("commitment", approved, mapOf("due_date" to "2027-01-10"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, moved.status, moved.body)
        assertEquals("DUE_DATE_RESCHEDULE_ONLY", code(moved))
    }

    @Test
    fun `una dependencia de si mismo o que cierra un ciclo se rechaza`() {
        val project = testProject()
        val (a, b) = commitment(project) to commitment(project)
        val own =
            exchange(
                "POST",
                "/api/objects/commitment_dependency/records",
                mapOf("attributes" to mapOf("commitment" to a, "depends_on" to a, "blocking" to true)),
                token
            )
        assertEquals(HttpStatus.BAD_REQUEST, own.status, own.body)
        assertEquals("DEPENDENCY_SELF", code(own))
        createRecord("commitment_dependency", mapOf("commitment" to a, "depends_on" to b, "blocking" to true))
        val cycle =
            exchange(
                "POST",
                "/api/objects/commitment_dependency/records",
                mapOf("attributes" to mapOf("commitment" to b, "depends_on" to a, "blocking" to false)),
                token
            )
        assertEquals(HttpStatus.CONFLICT, cycle.status, cycle.body)
        assertEquals("DEPENDENCY_CYCLE", code(cycle))
    }

    @Test
    fun `un cambio de origen que deja vacia la justificacion se rechaza`() {
        val project = testProject()
        val workshop = engagement(project, "TALLER")
        val id = commitment(project, mapOf("origin_engagement" to workshop, "origin_justification" to "Absuelve una pregunta"))
        val blank = edit("commitment", id, mapOf("origin_justification" to null), headers = withReason())
        assertEquals(HttpStatus.BAD_REQUEST, blank.status, blank.body)
        assertEquals("JUSTIFICATION_REQUIRED", code(blank))
    }

    @Test
    fun `editar otro campo de un compromiso aprobado no toca la fecha de vencimiento`() {
        val project = testProject()
        val approved = approvedCommitment(project)
        val renamed = edit("commitment", approved, mapOf("title" to "Otro título"), headers = withReason())
        assertEquals(HttpStatus.OK, renamed.status, renamed.body)
        val patched = patch("/api/objects/commitment/records/$approved", mapOf("attributes" to mapOf("title" to "Otro más")), token, withReason())
        assertEquals(HttpStatus.OK, patched.status, patched.body)
    }

    @Test
    fun `un origen que quien escribe no lee es OBJECT_NOT_READABLE y no pasa en silencio`() {
        val project = testProject()
        val body =
            attributes(project) + (
                "origin_engagement" to
                    java.util.UUID
                        .randomUUID()
                        .toString()
            ) + ("origin_justification" to "x")
        val refused = exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to body), token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("OBJECT_NOT_READABLE", code(refused))
    }
}
