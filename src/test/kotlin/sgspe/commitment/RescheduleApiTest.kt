package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// POST /api/sgspe/commitments/{id}/reschedules (RF-COM-003, api.md §4.5)
class RescheduleApiTest : F3DomainApiTest() {
    private fun body(date: String = "2027-02-01") =
        mapOf("attributes" to mapOf("new_due_date" to date, "reschedule_reason" to "Lluvias en la zona", "agreed_with_stakeholder" to true))

    @Test
    fun `reprograma en una transaccion, deja la fecha anterior y la nueva y devuelve el ETag del compromiso`() {
        val id = approvedCommitment(testProject())
        val response = exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, ifMatch("commitment", id) + withReason())
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val result = tree(response.body)
        assertEquals("2026-12-15", result["reschedule"]["attributes"]["previous_due_date"].asString())
        assertEquals("2027-02-01", result["commitment"]["attributes"]["due_date"].asString())
        assertEquals("\"${result["commitment"]["updatedAt"].asString()}\"", response.headers.getFirst(HttpHeaders.ETAG))
    }

    @Test
    fun `sin If-Match 428, con uno viejo 412, la misma fecha 400 y en draft 409`() {
        val project = testProject()
        val id = approvedCommitment(project)
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, withReason()).status)
        val stale = ifMatch("commitment", id)
        assertEquals(HttpStatus.CREATED, exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, stale + withReason()).status)
        assertEquals(
            HttpStatus.PRECONDITION_FAILED,
            exchange("POST", "/api/sgspe/commitments/$id/reschedules", body("2027-03-01"), token, stale + withReason()).status
        )
        val same = exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, ifMatch("commitment", id) + withReason())
        assertEquals("SAME_DUE_DATE", code(same))
        val draft = commitment(project)
        val inDraft = exchange("POST", "/api/sgspe/commitments/$draft/reschedules", body(), token, ifMatch("commitment", draft) + withReason())
        assertEquals(HttpStatus.CONFLICT, inDraft.status)
        assertEquals("STATE_MISMATCH", code(inDraft))
    }

    @Test
    fun `una reprogramacion no se crea por la API generica`() {
        val id = approvedCommitment(testProject())
        val direct =
            exchange(
                "POST",
                "/api/objects/commitment_reschedule/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "commitment" to id,
                            "previous_due_date" to "2026-12-15",
                            "new_due_date" to "2027-01-01",
                            "reschedule_reason" to "x",
                            "agreed_with_stakeholder" to true
                        )
                ),
                token
            )
        assertEquals(HttpStatus.FORBIDDEN, direct.status)
    }

    // la marca exime a la reprogramación; la misma edición por la API genérica sigue siendo DUE_DATE_RESCHEDULE_ONLY
    @Test
    fun `el comando cambia due_date de un compromiso aprobado y el PUT generico sigue con 409`() {
        val id = approvedCommitment(testProject(), start = false)
        val generic = edit("commitment", id, mapOf("due_date" to "2027-01-10"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, generic.status, generic.body)
        assertEquals("DUE_DATE_RESCHEDULE_ONLY", code(generic))
        val response = exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, ifMatch("commitment", id) + withReason())
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals("2027-02-01", tree(response.body)["commitment"]["attributes"]["due_date"].asString())
        val again = edit("commitment", id, mapOf("due_date" to "2027-03-10"), headers = withReason())
        assertEquals("DUE_DATE_RESCHEDULE_ONLY", code(again))
    }
}
