package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.DerivedFieldsJob

// CommitmentDerivationsListener y su recalculador (domain-model §7.4, §7.5, dashboards.md §12): las fechas de transición en la
// transacción de la transición y requires_senior_approval, que solo informa
class CommitmentDerivationsApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Test
    fun `cancelar fecha cancelled_date en la transicion, con el motivo del listener`() {
        val id = commitment(testProject())
        move("commitment", id, "cancel_draft", reason = "ya no aplica")
        assertTrue(record("commitment", id)["attributes"]["cancelled_date"].asString().isNotBlank())
        val history = tree(send("GET", "/api/objects/commitment/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"].asString() == CommitmentDerivationsListener.reason("cancel_draft") })
    }

    @Test
    fun `un origen en mesa de dialogo marca requires_senior_approval al crear`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        assertEquals(true, record("commitment", id)["attributes"]["requires_senior_approval"].asBoolean())
        assertEquals(false, record("commitment", commitment(project))["attributes"]["requires_senior_approval"].asBoolean(false))
    }

    @Test
    fun `JOB-02 corrige requires_senior_approval cuando cambia la politica del proyecto, y la segunda vuelta no escribe`() {
        val project = project(mapOf("policy_senior_approval_for_dialogue" to false))
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        assertEquals(false, record("commitment", id)["attributes"]["requires_senior_approval"].asBoolean(false))
        // cambiar la política del proyecto no toca el compromiso: lo recoge la vuelta diaria de JOB-02
        assertEquals(HttpStatus.OK, edit("project", project, mapOf("policy_senior_approval_for_dialogue" to true)).status)
        runBlocking { job.runOnce(full = true) }
        val after = record("commitment", id)
        assertEquals(true, after["attributes"]["requires_senior_approval"].asBoolean())
        runBlocking { job.runOnce(full = true) }
        assertEquals(after["updatedAt"].asString(), record("commitment", id)["updatedAt"].asString())
    }
}
