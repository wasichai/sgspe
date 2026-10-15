package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import sgspe.F8ApiTest
import sgspe.common.IdempotencyKey

// api.md §4.12, ASM-135, security.md §14: subir una plantilla exige MANAGE_METADATA (ADMIN); TemplateValidator rechaza con 422
// TEMPLATE_INVALID y sus violaciones; la válida queda como deliverable_template con su archivo DOCX y la lista devuelve la más
// reciente de cada clave
class DeliverableTemplatesApiTest : F8ApiTest() {
    private fun upload(
        bytes: ByteArray,
        fileName: String,
        key: String,
        token: String = this.token,
        idempotency: String? = null
    ): Response {
        val parts =
            MultipartBodyBuilder()
                .apply {
                    part("file", bytes, MediaType.APPLICATION_OCTET_STREAM).filename(fileName)
                    part("templateKey", key)
                    part("name", "Plantilla de la Asociación Ejemplo")
                }.build()
        val result =
            client
                .post()
                .uri("/api/sgspe/deliverable-templates")
                .header(HttpHeaders.AUTHORIZATION, token)
                .headers { h -> idempotency?.let { h.set(IdempotencyKey.HEADER, it) } }
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
        return Response(HttpStatus.valueOf(result.status.value()), result.responseBody ?: "", result.responseHeaders)
    }

    @Test
    fun `el admin sube la base y queda como la mas reciente de su clave`() {
        val key = uniqueName("informe-").lowercase()
        val first = upload(BaseTemplate.bytes, "sgspe-base.dotx", key, idempotency = "plantilla-$key")
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val body = tree(first.body)
        assertEquals(key, body["templateKey"].asString())
        val replay = upload(BaseTemplate.bytes, "sgspe-base.dotx", key, idempotency = "plantilla-$key")
        assertEquals(first.body, replay.body)
        assertEquals("true", replay.headers.getFirst(IdempotencyKey.REPLAYED))
        val second = tree(upload(BaseTemplate.bytes, "sgspe-base.dotx", key).also { assertEquals(HttpStatus.CREATED, it.status, it.body) }.body)
        val listed = tree(send("GET", "/api/sgspe/deliverable-templates", null, HttpStatus.OK)).values().single { it["templateKey"].asString() == key }
        assertEquals(second["id"].asString(), listed["id"].asString())
        assertEquals(body["sha256"].asString(), listed["sha256"].asString())
        val stored = record("deliverable_template", second["id"].asString())["attributes"]["file"]
        assertEquals(Deliverables.DOCX_TYPE, stored["contentType"].asString())
    }

    @Test
    fun `una plantilla con macros es 422 TEMPLATE_INVALID con sus violaciones`() {
        val response = upload(BaseTemplate.bytes, "plantilla.dotm", uniqueName("macro-").lowercase())
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.status, response.body)
        val problem = tree(response.body)
        assertEquals("TEMPLATE_INVALID", problem["errors"][0]["message"].asString())
        assertTrue(problem["violations"].values().any { it["code"].asString() == TemplateValidator.MACROS }, response.body)
    }

    @Test
    fun `sin MANAGE_METADATA es 403 y la lista exige READ sobre deliverable`() {
        val manager = member("SOCIAL_MANAGER")
        assertEquals(HttpStatus.FORBIDDEN, upload(BaseTemplate.bytes, "p.dotx", "sin-permiso", manager.token).status)
        send("GET", "/api/sgspe/deliverable-templates", null, HttpStatus.OK, manager.token)
        send("GET", "/api/sgspe/deliverable-templates", null, HttpStatus.FORBIDDEN, userWithRole("FIELD_AGENT").token)
    }
}
