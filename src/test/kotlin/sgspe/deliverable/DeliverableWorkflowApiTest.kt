package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest

// workflows.md WF-H y «Pruebas» (DeliverableWorkflowApiTest), deliverables.md §8.3 y §8.6: lo que DeliverableWriteGuard exige
// por la API genérica, las secciones del tipo al dar de alta y el aviso de aprobación. la emisión y sus 409 están en
// DeliverableIssueApiTest
class DeliverableWorkflowApiTest : F8ApiTest() {
    private fun codes(response: Response): List<String> = tree(response.body)["errors"].values().map { it["message"].asString() }

    private fun fields(response: Response): List<String> = tree(response.body)["errors"].values().map { it["field"].asString() }

    @Test
    fun `enviar a revision sin borrador LISTO es 409 STATE_MISMATCH sobre draft`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val id = deliverable(project, creator = manager)
        val response = transition("deliverable", id, "submit_for_review", manager.token, "envío")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("STATE_MISMATCH"), codes(response))
        assertEquals(listOf("draft"), fields(response))
    }

    @Test
    fun `una seccion cambiada despues de congelar es 409 SECTIONS_CHANGED en enviar y en aprobar`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val id = deliverable(project, creator = manager)
        val section = sectionId(id, "conclusiones")
        readyDraft(id)
        edit("deliverable_section", section, mapOf("content" to "Texto corregido después del borrador."))
        val submit = transition("deliverable", id, "submit_for_review", manager.token, "envío")
        assertEquals(HttpStatus.CONFLICT, submit.status, submit.body)
        assertEquals(listOf("SECTIONS_CHANGED"), codes(submit))
        assertEquals(listOf("deliverable_section.conclusiones"), fields(submit))
        readyDraft(id)
        move("deliverable", id, "submit_for_review", manager.token, "envío")
        edit("deliverable_section", section, mapOf("content" to "Otra corrección."))
        val approve = transition("deliverable", id, "approve", manager.token, "aprobación")
        assertEquals(listOf("SECTIONS_CHANGED"), codes(approve))
        assertEquals("in_review", state("deliverable", id))
    }

    @Test
    fun `el tipo no cambia y current_version no se escribe por la API generica`() {
        val id = deliverable(testProject())
        val type = edit("deliverable", id, mapOf("deliverable_type" to SOCIAL_DIAGNOSIS), headers = withReason("cambio"))
        assertEquals(HttpStatus.BAD_REQUEST, type.status, type.body)
        assertEquals(listOf("FIELD_NOT_EDITABLE"), codes(type))
        val version = deliverableVersion(id)
        val current = edit("deliverable", id, mapOf("current_version" to version), headers = withReason("cambio"))
        assertEquals(HttpStatus.BAD_REQUEST, current.status, current.body)
        assertEquals(listOf("DERIVED_FIELD"), codes(current))
    }

    @Test
    fun `una seccion asistida por IA pide motivo, no vuelve a HUMANO y no se borra`() {
        val id = deliverable(testProject())
        val section = sectionId(id, "estrategias")
        val adopted = edit("deliverable_section", section, mapOf("content_origin" to "ASISTIDO POR IA", "content" to "Texto adoptado."))
        assertEquals(HttpStatus.BAD_REQUEST, adopted.status, adopted.body)
        assertEquals(listOf("FIELD_REASON_REQUIRED"), codes(adopted))
        val ok =
            edit("deliverable_section", section, mapOf("content_origin" to "ASISTIDO POR IA", "content" to "Texto adoptado."), headers = withReason("adopción"))
        assertEquals(HttpStatus.OK, ok.status, ok.body)
        val back = edit("deliverable_section", section, mapOf("content_origin" to "HUMANO"), headers = withReason("vuelta"))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, back.status, back.body)
        assertEquals(listOf("AI_ORIGIN_LOCKED"), codes(back))
        // borrarla y crearla de nuevo con la misma clave la sacaría de ai_assisted_sections
        val deleted = exchange("DELETE", "/api/objects/deliverable_section/records/$section", headers = withReason("baja"))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, deleted.status, deleted.body)
        assertEquals(listOf("AI_ORIGIN_LOCKED"), codes(deleted))
    }

    @Test
    fun `la clave y el entregable de una seccion no cambian, asistida o no`() {
        val project = testProject()
        val id = deliverable(project)
        val assisted = sectionId(id, "estrategias")
        edit("deliverable_section", assisted, mapOf("content_origin" to "ASISTIDO POR IA", "content" to "Texto adoptado."), headers = withReason("adopción"))
        val human = sectionId(id, "conclusiones")
        for (section in listOf(assisted, human)) {
            val renamed = edit("deliverable_section", section, mapOf("section_key" to uniqueName("otra-")), headers = withReason("cambio"))
            assertEquals(HttpStatus.BAD_REQUEST, renamed.status, renamed.body)
            assertEquals(listOf("FIELD_NOT_EDITABLE"), codes(renamed))
        }
        val moved = edit("deliverable_section", assisted, mapOf("deliverable" to deliverable(project)), headers = withReason("cambio"))
        assertEquals(HttpStatus.BAD_REQUEST, moved.status, moved.body)
        assertEquals(listOf("FIELD_NOT_EDITABLE"), codes(moved))
    }

    @Test
    fun `una version INTERNO sin DOCX o con un SHA-256 distinto del archivo se rechaza`() {
        val id = deliverable(testProject())
        val pdf = stage(admin(), "deliverable_version", "pdf", PDF, "v.pdf", "application/pdf")
        val base =
            mapOf(
                "deliverable" to id,
                "version_number" to 1,
                "revision" to "0",
                "issued_at" to "2026-10-15T15:00:00Z",
                "issued_by_user_id" to admin().id,
                "issued_by_user_email" to admin().email,
                "disclosure_level" to "INTERNO",
                "pdf" to pdf,
                "pdf_sha256" to sha256(PDF),
                "model_sha256" to sha256(MODEL),
                "template_sha256" to sha256(MODEL)
            )
        val noDocx = runCatching { createAs(admin(), "deliverable_version", base, "versión") }.exceptionOrNull()
        assertTrue("FIELD_REQUIRED" in noDocx.toString(), noDocx.toString())
        val external = base + mapOf("disclosure_level" to "EXTERNO", "pdf_sha256" to "0".repeat(64))
        val mismatch = runCatching { createAs(admin(), "deliverable_version", external, "versión") }.exceptionOrNull()
        assertTrue("SHA256_MISMATCH" in mismatch.toString(), mismatch.toString())
    }

    @Test
    fun `el alta de un T2 crea sus secciones y un T1 todavia no`() {
        val t2 = deliverable(testProject())
        val keys =
            tree(send("GET", "/api/objects/deliverable_section/records?deliverable=$t2&size=100", null, HttpStatus.OK))["content"].values().map {
                it["attributes"]["section_key"].asString()
            }
        assertEquals(FieldReportType().sections.map { it.key }.sorted(), keys.sorted())
        val t1 = deliverable(testProject(), SOCIAL_DIAGNOSIS)
        assertTrue(tree(send("GET", "/api/objects/deliverable_section/records?deliverable=$t1", null, HttpStatus.OK))["content"].isEmpty)
    }

    @Test
    fun `enviar y reenviar a revision avisan a quien aprueba y observar lo resuelve`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val reviewer = member("STAKEHOLDER_MANAGER", project)
        val id = deliverable(project, creator = manager)
        readyDraft(id)
        move("deliverable", id, "submit_for_review", manager.token, "envío")
        assertTrue(pending(reviewer.token, id))
        assertFalse(pending(manager.token, id))
        move("deliverable", id, "observe", reviewer.token, "faltan los cargos")
        assertFalse(pending(reviewer.token, id))
        readyDraft(id)
        move("deliverable", id, "resubmit", manager.token, "corregido")
        assertTrue(pending(reviewer.token, id))
    }

    private fun pending(
        token: String,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values().any {
            it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION"
        }
}
