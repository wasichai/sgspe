package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import sgspe.F8ApiTest

// security.md §14 («Descargas»), api.md §4.12, deliverables.md §13.2 criterios 14, 22, 23 y 24 (Review Focus 2): las rutas
// propias y las nativas sirven el borrador con la marca a quien tiene DOWNLOAD_RESTRICTED y dejan su fila DESCARGA; las
// candidatas, el modelo y el DOCX fuera de INTERNO no se sirven; nada se sube por la API genérica
class DeliverableDownloadsApiTest : F8ApiTest() {
    private fun downloads(id: String): Int =
        tree(send("GET", "/api/objects/export_log/records?deliverable=$id&export_kind=DESCARGA", null, HttpStatus.OK))["content"].size()

    @Test
    fun `el borrador se baja con DOWNLOAD_RESTRICTED, con ETag y su fila DESCARGA, por la ruta propia y la nativa`() {
        val project = testProject()
        val id = deliverable(project)
        readyDraft(id)
        val manager = member("SOCIAL_MANAGER", project)
        val pdf = exchange("GET", "/api/sgspe/deliverables/$id/draft/content?level=INTERNO&format=pdf", token = manager.token)
        assertEquals(HttpStatus.OK, pdf.status, pdf.body)
        assertEquals(record("deliverable", id)["attributes"][Deliverables.DRAFT_INTERNAL_PDF]["sha256"].asString(), pdf.headers.eTag?.trim('"'))
        assertTrue(pdf.headers.contentDisposition.isAttachment)
        assertEquals("nosniff", pdf.headers.getFirst("X-Content-Type-Options"))
        assertEquals(
            HttpStatus.OK,
            exchange("GET", "/api/objects/deliverable/records/$id/files/${Deliverables.DRAFT_EXTERNAL_PDF}", token = manager.token).status
        )
        assertEquals(2, downloads(id))
        // HEAD no sirve nada y no deja fila
        assertEquals(
            HttpStatus.METHOD_NOT_ALLOWED,
            exchange("HEAD", "/api/sgspe/deliverables/$id/draft/content?level=INTERNO&format=pdf", token = manager.token).status
        )
        assertEquals(2, downloads(id))
        val reader = member("PROJECT_MANAGER", project)
        val denied = exchange("GET", "/api/sgspe/deliverables/$id/draft/content?level=INTERNO&format=pdf", token = reader.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals(2, downloads(id))
    }

    // revisión final del plan 8.2b, I4: tras un intento FALLIDO se sirve el último borrador LISTO, y su fila DESCARGA lo dice
    // con el SHA-256 del archivo servido, sin la letra del intento nuevo
    @Test
    fun `tras un intento fallido la fila DESCARGA nombra el archivo servido y no la letra nueva`() {
        val project = testProject()
        val id = deliverable(project)
        readyDraft(id)
        platformUpdate("deliverable", id, mapOf(Deliverables.STATUS to Deliverables.FAILED, Deliverables.DRAFT_REVISION to "B"))
        val manager = member("SOCIAL_MANAGER", project)
        val pdf = exchange("GET", "/api/sgspe/deliverables/$id/draft/content?level=INTERNO&format=pdf", token = manager.token)
        assertEquals(HttpStatus.OK, pdf.status, pdf.body)
        val served = pdf.headers.eTag!!.trim('"')
        val row =
            tree(send("GET", "/api/objects/export_log/records?deliverable=$id&export_kind=DESCARGA", null, HttpStatus.OK))["content"]
                .values()
                .single()
        val description = row["attributes"]["filters_description"].asString()
        assertTrue(served in description && "último borrador LISTO" in description, description)
        assertTrue("borrador B" !in description, description)
    }

    @Test
    fun `candidatas, modelo y DOCX fuera de INTERNO son 404 y un formato desconocido 422`() {
        val id = deliverable(testProject())
        readyDraft(id)
        for (field in listOf(
            Deliverables.DRAFT_MODEL,
            Deliverables.CANDIDATE_INTERNAL_DOCX,
            Deliverables.CANDIDATE_INTERNAL_PDF,
            Deliverables.CANDIDATE_EXTERNAL_PDF
        )) {
            assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/deliverable/records/$id/files/$field").status, field)
        }
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/deliverables/$id/draft/content?level=EXTERNO&format=docx").status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/deliverables/$id/draft/content?level=INTERNO&format=model").status)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/deliverables/$id/draft/content?level=INTERNO&format=odt").status)
        assertEquals(0, downloads(id))
    }

    @Test
    fun `una version EXTERNO se baja con EXPORT y una INTERNO con DOWNLOAD_RESTRICTED, y su modelo nunca`() {
        val project = testProject()
        val id = deliverable(project)
        val internal = deliverableVersion(id, 1, "INTERNO")
        deliverableVersion(id, 2, "EXTERNO", derivedFrom = internal)
        val reader = member("PROJECT_MANAGER", project)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/sgspe/deliverables/$id/versions/2/content", token = reader.token).status)
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/deliverables/$id/versions/1/content", token = reader.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/deliverables/$id/versions/2/content?format=docx").status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/sgspe/deliverables/$id/versions/1/content?format=docx").status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/deliverable_version/records/$internal/files/model").status)
    }

    @Test
    fun `subir o reemplazar un archivo de entregable por la API generica es 403 FILE_WRITE_NOT_ALLOWED`() {
        val id = deliverable(testProject())
        val parts = MultipartBodyBuilder().apply { part("file", PDF, MediaType.APPLICATION_PDF).filename("x.pdf") }.build()
        for (path in listOf(
            "/api/objects/deliverable/files/${Deliverables.DRAFT_INTERNAL_PDF}",
            "/api/objects/deliverable/records/$id/files/${Deliverables.DRAFT_INTERNAL_PDF}",
            "/api/objects/deliverable_version/files/pdf",
            "/api/objects/deliverable_template/files/file"
        )) {
            val body =
                client
                    .post()
                    .uri(path)
                    .header("Authorization", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .exchange()
                    .expectStatus()
                    .isForbidden
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody!!
            assertEquals("FILE_WRITE_NOT_ALLOWED", tree(body)["errors"][0]["message"].asString(), path)
        }
    }
}
