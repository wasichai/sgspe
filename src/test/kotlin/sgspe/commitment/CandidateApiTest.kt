package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.IdempotencyKey
import java.util.UUID

// los candidatos a compromiso registrados por personas (RF-COM-010, WF-K, api.md §4.5): los casos que
// CommitmentCandidateWorkflowApiTest (3.1) deja a 3.2
class CandidateApiTest : F3DomainApiTest() {
    private fun create(
        attributes: Map<String, Any?>,
        headers: Map<String, String> = emptyMap()
    ): Response = exchange("POST", "/api/sgspe/commitment-candidates", mapOf("attributes" to attributes), token, headers)

    private fun candidate(
        project: String,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val response = create(mapOf("project" to project, "proposed_text" to "Arreglar el puente") + attributes)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals(false, tree(response.body)["attributes"]["ai_generated"].asBoolean())
        return tree(response.body)["id"].asString()
    }

    private fun accept(
        id: String,
        who: Account,
        attributes: Map<String, Any?> = reviewed(),
        headers: Map<String, String> = emptyMap()
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/commitment-candidates/$id/accept",
            mapOf("attributes" to attributes),
            who.token,
            ifMatch("commitment_candidate", id, who.token) + withReason() + headers
        )

    private fun reviewed(): Map<String, Any?> = mapOf("code" to uniqueName("C-"), "title" to "Puente", "amount" to 500, "currency" to "PEN")

    private fun commitments(code: String): Int = tree(send("GET", "/api/objects/commitment/records?code=$code", null, HttpStatus.OK))["totalElements"].asInt()

    @Test
    fun `el alta humana no fija el origen de IA ni la confianza`() {
        val refused =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates",
                mapOf("attributes" to mapOf("project" to testProject(), "proposed_text" to "x", "ai_generated" to true)),
                token
            )
        assertEquals(HttpStatus.BAD_REQUEST, refused.status)
        val decided = create(mapOf("project" to testProject(), "proposed_text" to "x", "duplicate_of" to commitment(testProject())))
        assertEquals(HttpStatus.BAD_REQUEST, decided.status, decided.body)
    }

    @Test
    fun `aceptar crea el compromiso en draft y lo vincula, y por la API generica se rechaza`() {
        val project = testProject()
        val id = candidate(project)
        val generic = transition("commitment_candidate", id, "accept")
        assertEquals(HttpStatus.FORBIDDEN, generic.status)
        assertEquals("COMMAND_ONLY", code(generic))
        val manager = member("COMMITMENT_MANAGER", project)
        val body = mapOf("attributes" to mapOf("code" to uniqueName("C-"), "title" to "Puente", "amount" to 500, "currency" to "PEN"))
        val accepted =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/accept",
                body,
                manager.token,
                ifMatch("commitment_candidate", id, manager.token) + withReason()
            )
        assertEquals(HttpStatus.CREATED, accepted.status, accepted.body)
        val result = tree(accepted.body)
        assertEquals("draft", result["commitment"]["state"].asString())
        assertEquals("accepted", result["candidate"]["state"].asString())
        assertEquals(result["commitment"]["id"].asString(), result["candidate"]["attributes"]["created_commitment"].asString())
    }

    // el compromiso toma el proyecto y la interacción del candidato (P54: los dos del mismo proyecto), nunca la cita restringida;
    // un segundo intento sobre el candidato ya aceptado es STATE_MISMATCH y el proyecto no llega del cuerpo
    @Test
    fun `el compromiso aceptado hereda proyecto e interaccion, no la cita, y no se acepta dos veces`() {
        val project = testProject()
        val origin = engagement(project)
        val id = candidate(project, mapOf("engagement" to origin, "evidence_quote" to "cita reservada de la fuente"))
        val manager = member("COMMITMENT_MANAGER", project)
        val withProject = accept(id, manager, reviewed() + ("project" to project()))
        assertEquals(HttpStatus.BAD_REQUEST, withProject.status, withProject.body)
        val accepted = accept(id, manager)
        assertEquals(HttpStatus.CREATED, accepted.status, accepted.body)
        val commitment = tree(accepted.body)["commitment"]
        assertEquals(project, commitment["attributes"]["project"].asString())
        assertEquals(origin, commitment["attributes"]["origin_engagement"].asString())
        assertFalse("cita reservada" in commitment.toString(), commitment.toString())
        val again = accept(id, manager)
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("STATE_MISMATCH", code(again))
    }

    // P54: el candidato no guarda una interacción de otro proyecto, que el compromiso aceptado heredaría
    @Test
    fun `un candidato con la interaccion de otro proyecto es PROJECT_MISMATCH`() {
        val refused = create(mapOf("project" to testProject(), "proposed_text" to "x", "engagement" to engagement(project())))
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PROJECT_MISMATCH", code(refused))
        assertEquals(listOf("engagement"), problemFields(refused.body))
    }

    // un reintento con la misma Idempotency-Key responde los mismos bytes y no crea otro compromiso
    @Test
    fun `aceptar dos veces con la misma clave crea un solo compromiso`() {
        val project = testProject()
        val id = candidate(project)
        val manager = member("COMMITMENT_MANAGER", project)
        val attributes = reviewed()
        val key = mapOf(IdempotencyKey.HEADER to UUID.randomUUID().toString())
        val first = accept(id, manager, attributes, key)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val replay = accept(id, manager, attributes, key)
        assertEquals(first.body, replay.body)
        assertEquals("true", replay.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals(1, commitments(attributes.getValue("code") as String))
    }

    @Test
    fun `marcar duplicado escribe duplicate_of con el comando, y sin el por la API generica se rechaza`() {
        val project = testProject()
        val id = candidate(project)
        val generic = transition("commitment_candidate", id, "mark_duplicate", reason = "repetido")
        assertEquals(HttpStatus.CONFLICT, generic.status)
        assertEquals("FIELD_REQUIRED", code(generic))
        val existing = commitment(project)
        val other = commitment(project())
        val elsewhere =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/duplicate",
                mapOf("duplicateOf" to other),
                token,
                ifMatch("commitment_candidate", id) + withReason()
            )
        assertEquals("DUPLICATE_OTHER_PROJECT", code(elsewhere))
        val missing =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/duplicate",
                mapOf("duplicateOf" to UUID.randomUUID().toString()),
                token,
                ifMatch("commitment_candidate", id) + withReason()
            )
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.body)
        assertEquals("DUPLICATE_NOT_FOUND", code(missing))
        val marked =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/duplicate",
                mapOf("duplicateOf" to existing),
                token,
                ifMatch("commitment_candidate", id) + withReason()
            )
        assertEquals(HttpStatus.OK, marked.status, marked.body)
        assertEquals("duplicate", tree(marked.body)["candidate"]["state"].asString())
        assertEquals(existing, tree(marked.body)["candidate"]["attributes"]["duplicate_of"].asString())
    }
}
