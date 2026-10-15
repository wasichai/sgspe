package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest

// especificación de F8 §2.2.5 y §3.5, deliverables.md §8.4, workflows.md WF-H y AUT-12 (Review Focus 3): la emisión por su
// endpoint como la persona; los casos de IssueCertificateApiTest del plan 8.1 viven aquí desde 8.2b
class DeliverableIssueApiTest : F8ApiTest() {
    private fun codes(response: Response): List<String> = tree(response.body)["errors"].values().map { it["message"].asString() }

    private fun versions(id: String) =
        tree(send("GET", "/api/objects/deliverable_version/records?deliverable=$id&size=10", null, HttpStatus.OK))["content"].values()

    @Test
    fun `emitir crea INTERNO y EXTERNO con la misma revision, el SHA-256 del modelo, export_log EMISION y la constancia`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val id = approvedDeliverable(project, manager)
        val sha = record("deliverable", id)["attributes"][Deliverables.MODEL_SHA256].asString()
        val response = issue(id, manager)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals("issued", state("deliverable", id))
        val all = versions(id)
        assertEquals(setOf("INTERNO", "EXTERNO"), all.map { it["attributes"]["disclosure_level"].asString() }.toSet())
        assertTrue(all.all { it["attributes"]["revision"].asString() == "0" && it["attributes"]["model_sha256"].asString() == sha })
        val internal = all.single { it["attributes"]["disclosure_level"].asString() == "INTERNO" }
        val external = all.single { it["attributes"]["disclosure_level"].asString() == "EXTERNO" }
        assertEquals(internal["id"].asString(), external["attributes"]["derived_from"].asString())
        assertTrue(external["attributes"]["docx"].isNull || external["attributes"]["docx"].isMissingNode)
        assertEquals(internal["id"].asString(), record("deliverable", id)["attributes"]["current_version"].asString())
        // las filas de quien emitió (la suite comparte la base: la matriz también emite)
        val logged =
            tree(send("GET", "/api/objects/export_log/records?export_kind=EMISION&exporter_user_id=${manager.id}&size=50", null, HttpStatus.OK))["content"]
                .values()
        assertEquals(all.map { it["id"].asString() }.toSet(), logged.map { it["attributes"]["deliverable_version"].asString() }.toSet())
        assertEquals(2, logged.size)
        assertEquals("SUCCEEDED", awaitRun("deliverable", "aut-12-issue-certificate", id)["status"].asString())
        val certificate = tree(send("GET", "/api/objects/deliverable/records/$id/documents", null, HttpStatus.OK)).values().single()
        assertTrue(certificate["number"].asString().startsWith("CE-"), "$certificate")
        assertEquals("VALID", certificate["status"].asString())
        // sin la tabla de las versiones (UPS-32, decisión 11 del plan 8.1): no se reintroduce hasta que wasichai lo arregle
        val values = certificate["snapshot"]["values"]
        assertEquals(record("deliverable", id)["attributes"]["document_code"].asString(), values["document_code"].asString(), "$certificate")
        assertEquals("0", values["current_revision"].asString())
        assertTrue(certificate["snapshot"]["related"].isEmpty, "${certificate["snapshot"]["related"]}")
        // deliverable_version es apiOnly y appendOnly: la API genérica responde 403 antes de mirar appendOnly (api.md §1 y
        // §3.3), como en stakeholder_assessment e indicator_measurement
        val changed = edit("deliverable_version", internal["id"].asString(), mapOf("revision" to "9"))
        assertEquals(HttpStatus.FORBIDDEN, changed.status, changed.body)
    }

    @Test
    fun `otro SHA-256, un mapa fallido, una seccion cambiada o sin la lista no emiten nada`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val id = approvedDeliverable(project, manager)
        assertEquals(listOf("MODEL_MISMATCH"), codes(issue(id, manager, sha = "0".repeat(64))))
        assertEquals(listOf("CHECKLIST_REQUIRED"), codes(issue(id, manager, checklist = false)))
        platformUpdate("deliverable", id, mapOf(Deliverables.WARNINGS to "MAP_FAILED portada"))
        assertEquals(listOf("MAP_FAILED"), codes(issue(id, manager)))
        platformUpdate("deliverable", id, mapOf(Deliverables.WARNINGS to null))
        edit("deliverable_section", sectionId(id, "conclusiones"), mapOf("content" to "Cambio después de aprobar."))
        val changed = issue(id, manager)
        assertEquals(HttpStatus.CONFLICT, changed.status, changed.body)
        assertEquals(listOf("SECTIONS_CHANGED"), codes(changed))
        assertEquals("approved", state("deliverable", id))
        assertTrue(versions(id).isEmpty())
        move("deliverable", id, "return_to_draft", manager.token, "corregir la sección")
        assertEquals("draft", state("deliverable", id))
    }

    @Test
    fun `sin ISSUE es 403, sin If-Match 428, y emitir por la API generica sigue siendo COMMAND_ONLY`() {
        val project = testProject()
        val id = approvedDeliverable(project)
        assertEquals(HttpStatus.FORBIDDEN, issue(id, member("PROJECT_MANAGER", project)).status)
        val noIfMatch =
            exchange(
                "POST",
                "/api/sgspe/deliverables/$id/issue",
                mapOf("draftModelSha256" to "x", "checklistConfirmed" to true),
                headers = withReason("emisión")
            )
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, noIfMatch.status, noIfMatch.body)
        val generic = transition("deliverable", id, "issue", token, "emisión")
        assertEquals(HttpStatus.FORBIDDEN, generic.status)
        assertEquals(listOf("COMMAND_ONLY"), codes(generic))
    }

    @Test
    fun `una revision nueva conserva las versiones y la constancia es el unico tipo de documento`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val id = approvedDeliverable(project, manager)
        assertEquals(HttpStatus.CREATED, issue(id, manager).status)
        move("deliverable", id, "start_revision", manager.token, "revisión 1")
        assertEquals("draft", state("deliverable", id))
        assertEquals(2, versions(id).size)
        val types = tree(send("GET", "/api/objects/deliverable/document-types", null, HttpStatus.OK)).values()
        assertEquals(mapOf("deliverable-issue-certificate" to "CE"), types.associate { it["name"].asString() to it["prefix"].asString() })
        val automations = tree(send("GET", "/api/objects/deliverable/automations", null, HttpStatus.OK)).values().map { it["name"].asString() }
        assertEquals(listOf("aut-12-issue-certificate"), automations)
    }

    // current_version es una relación, no un derivado: DerivedFieldsKeeper no la repone, y el PUT de wasichai deja vacío lo que
    // el cuerpo no trae. DeliverableWriteGuard rechaza que un PUT sin la marca la cambie, también omitiéndola; reenviarla tal
    // como se leyó pasa (revisión de la Tarea 10 del plan 8.2b)
    @Test
    fun `un PUT del entregable que omite current_version no la borra, y reenviarla como se leyo pasa`() {
        val project = testProject()
        val manager = member("SOCIAL_MANAGER", project)
        val id = approvedDeliverable(project, manager)
        assertEquals(HttpStatus.CREATED, issue(id, manager).status)
        val current = record("deliverable", id, manager.token)["attributes"]
        val version = current[Deliverables.CURRENT_VERSION].asString()
        val omitted =
            current
                .properties()
                .filter { it.key != Deliverables.CURRENT_VERSION }
                .associate { (k, v) -> k to json(v) }
        val put = exchange("PUT", "/api/objects/deliverable/records/$id", mapOf("attributes" to omitted), manager.token, withReason("edición de prueba"))
        assertEquals(HttpStatus.BAD_REQUEST, put.status, put.body)
        assertEquals(listOf("DERIVED_FIELD"), codes(put))
        assertEquals(version, record("deliverable", id)["attributes"][Deliverables.CURRENT_VERSION].asString())
        val resent = edit("deliverable", id, mapOf("title" to "Informe de campo y avance corregido"), manager.token, withReason("edición de prueba"))
        assertEquals(HttpStatus.OK, resent.status, resent.body)
        assertEquals(version, record("deliverable", id)["attributes"][Deliverables.CURRENT_VERSION].asString())
    }
}
