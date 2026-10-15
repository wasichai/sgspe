package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// especificación de F8 §3.3 y §3.5, §5 (DeliverableDraftsApiTest), deliverables.md §8.2 y §13.3 («Almacenamiento»): el pedido,
// JOB-05 de punta a punta contra wasichai real con los dobles de PDF y mapas, y los siete archivos adjuntos por PersonDraftFiles
// mientras UPS-31 siga abierta (la Tarea 15 los adjunta como la plataforma)
class DeliverableDraftsApiTest : F8ApiTest() {
    private fun codes(response: Response): List<String> = tree(response.body)["errors"].values().map { it["message"].asString() }

    private fun draft(id: String) = tree(send("GET", "/api/sgspe/deliverables/$id/draft", null, HttpStatus.OK))

    @Test
    fun `un pedido fuera de draft u observed es 409, sin parametros 422 por campo y un T1 todavia no se genera`() {
        val project = testProject()
        val bare = deliverable(project)
        val missing = requestDraft(bare)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, missing.status, missing.body)
        assertEquals(setOf("community", "project.working_crs"), tree(missing.body)["errors"].values().map { it["field"].asString() }.toSet())
        assertTrue(codes(missing).all { it == "DELIVERABLE_PARAMETERS_MISSING" })
        val t1 = requestDraft(deliverable(project, SOCIAL_DIAGNOSIS))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, t1.status, t1.body)
        assertEquals(listOf("TYPE_NOT_AVAILABLE"), codes(t1))
        val approved = approvedDeliverable(project)
        val late = requestDraft(approved)
        assertEquals(HttpStatus.CONFLICT, late.status, late.body)
        assertEquals(listOf("STATE_MISMATCH"), codes(late))
    }

    @Test
    fun `JOB-05 deja LISTO con los siete archivos y un borrador nuevo los reemplaza con la letra siguiente`() {
        val id = requestedFieldReport()
        assertTrue(draft(id)["pending"].asBoolean())
        assertTrue(generate() >= 1)
        val first = record("deliverable", id)["attributes"]
        assertEquals(Deliverables.READY, first[Deliverables.STATUS].asString(), "$first")
        assertEquals("A", first[Deliverables.DRAFT_REVISION].asString())
        for (field in Deliverables.DRAFT_FILES) assertTrue(first[field]?.has("sha256") == true, field)
        assertEquals(first[Deliverables.MODEL_SHA256].asString(), first[Deliverables.DRAFT_MODEL]["sha256"].asString())
        val status = draft(id)
        assertEquals(false, status["pending"].asBoolean())
        assertEquals(
            first[Deliverables.CANDIDATE_EXTERNAL_PDF]["sha256"].asString(),
            status["levels"]
                .values()
                .single {
                    it["level"].asString() == "EXTERNO"
                }["candidateSha256"]
                .asString()
        )
        assertTrue(fakePdf.requests.any { it.pdfa } && fakePdf.requests.any { !it.pdfa })
        assertEquals(HttpStatus.ACCEPTED, requestDraft(id).status)
        generate()
        val second = record("deliverable", id)["attributes"]
        assertEquals("B", second[Deliverables.DRAFT_REVISION].asString())
        assertNotEquals(first[Deliverables.DRAFT_INTERNAL_PDF]["id"].asString(), second[Deliverables.DRAFT_INTERNAL_PDF]["id"].asString())
    }

    @Test
    fun `sin conversor queda FALLIDO con PDF_CONVERTER_UNAVAILABLE y un mapa fallido deja LISTO con MAP_FAILED`() {
        val failed = requestedFieldReport()
        fakePdf.unavailable = true
        try {
            generate()
        } finally {
            fakePdf.unavailable = false
        }
        assertEquals(Deliverables.FAILED, record("deliverable", failed)["attributes"][Deliverables.STATUS].asString())
        assertEquals(listOf(Deliverables.PDF_CONVERTER_UNAVAILABLE), draft(failed)["warnings"].values().map { it["code"].asString() })
        val mapless = requestedFieldReport()
        fakeMaps.failing = true
        try {
            generate()
        } finally {
            fakeMaps.failing = false
        }
        assertEquals(Deliverables.READY, record("deliverable", mapless)["attributes"][Deliverables.STATUS].asString())
        val warnings = draft(mapless)["warnings"].values().map { it["code"].asString() to it["section"]?.asString() }
        assertTrue(Deliverables.MAP_FAILED to "portada" in warnings, "$warnings")
    }

    @Test
    fun `un pedido EN PROCESO viejo se retoma y uno reciente no`() {
        val recent = requestedFieldReport()
        platformUpdate("deliverable", recent, mapOf(Deliverables.STATUS to Deliverables.RUNNING, Deliverables.GENERATED_AT to Instant.now().toString()))
        val old = requestedFieldReport()
        platformUpdate(
            "deliverable",
            old,
            mapOf(
                Deliverables.STATUS to Deliverables.RUNNING,
                Deliverables.GENERATED_AT to Instant.now().minus(20, ChronoUnit.MINUTES).toString()
            )
        )
        generate()
        assertEquals(Deliverables.RUNNING, record("deliverable", recent)["attributes"][Deliverables.STATUS].asString())
        assertEquals(Deliverables.READY, record("deliverable", old)["attributes"][Deliverables.STATUS].asString())
    }

    @Test
    fun `nadie escribe draft_ ni current_version por la API generica, y reenviar los descriptores leidos no es cambiarlos`() {
        val id = deliverable(testProject())
        readyDraft(id)
        val resent = edit("deliverable", id, mapOf("title" to "Informe de campo y avance corregido"), headers = withReason("título"))
        assertEquals(HttpStatus.OK, resent.status, resent.body)
        assertEquals(Deliverables.READY, record("deliverable", id)["attributes"][Deliverables.STATUS].asString())
        val other = stage(admin(), "deliverable", Deliverables.DRAFT_INTERNAL_PDF, PDF, "otro.pdf", "application/pdf")
        val swapped = edit("deliverable", id, mapOf(Deliverables.DRAFT_INTERNAL_PDF to other), headers = withReason("cambio"))
        assertEquals(HttpStatus.BAD_REQUEST, swapped.status, swapped.body)
        assertEquals(listOf("DERIVED_FIELD"), codes(swapped))
        val status = edit("deliverable", id, mapOf(Deliverables.STATUS to Deliverables.FAILED), headers = withReason("cambio"))
        assertEquals(listOf("DERIVED_FIELD"), codes(status))
        assertEquals(UUID.fromString(id).toString(), record("deliverable", id)["id"].asString())
    }

    @Test
    fun `una plantilla inexistente es 422 al pedir, y si falta al generar deja FALLIDO una sola vez`() {
        val scenario = reportScenario()
        platformUpdate("deliverable", scenario.fieldReport, mapOf(Deliverables.TEMPLATE_KEY to "no-existe"))
        val refused = requestDraft(scenario.fieldReport)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, refused.status, refused.body)
        assertEquals(listOf(Deliverables.TEMPLATE_UNAVAILABLE), codes(refused))
        platformUpdate("deliverable", scenario.fieldReport, mapOf(Deliverables.TEMPLATE_KEY to null))
        val id = requestedFieldReport(scenario)
        platformUpdate("deliverable", id, mapOf(Deliverables.TEMPLATE_KEY to "no-existe"))
        generate()
        val failed = record("deliverable", id)
        assertEquals(Deliverables.FAILED, failed["attributes"][Deliverables.STATUS].asString())
        assertEquals(listOf(Deliverables.TEMPLATE_UNAVAILABLE), draft(id)["warnings"].values().map { it["code"].asString() })
        assertEquals(false, draft(id)["pending"].asBoolean())
        generate()
        assertEquals(failed["updatedAt"].asString(), record("deliverable", id)["updatedAt"].asString())
    }
}
