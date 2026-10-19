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
        idempotency: String? = null,
        logo: ByteArray? = null,
        accent: String? = null
    ): Response {
        val parts =
            MultipartBodyBuilder()
                .apply {
                    part("file", bytes, MediaType.APPLICATION_OCTET_STREAM).filename(fileName)
                    part("templateKey", key)
                    part("name", "Plantilla de la Asociación Ejemplo")
                    logo?.let { part("logo", it, MediaType.APPLICATION_OCTET_STREAM).filename("logo.png") }
                    accent?.let { part("accentColor", it) }
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

    private fun png(): ByteArray =
        java.io
            .ByteArrayOutputStream()
            .also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(300, 90, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()

    // ASM-140: la marca del PDF se sube con la plantilla: el logo queda en su campo FILE y el acento en mayúsculas
    @Test
    fun `la plantilla lleva el logo y el acento del PDF, y un logo que no es imagen o un acento mal escrito son 422`() {
        val key = uniqueName("marca-").lowercase()
        val created = upload(BaseTemplate.bytes, "sgspe-base.dotx", key, logo = png(), accent = "2e7d32")
        assertEquals(HttpStatus.CREATED, created.status, created.body)
        val id = tree(created.body)["id"].asString()
        val attributes = record("deliverable_template", id)["attributes"]
        assertEquals(BrandLogo.PNG, attributes["logo"]["contentType"].asString())
        assertEquals("2E7D32", attributes["accent_color"].asString())
        val listed = tree(send("GET", "/api/sgspe/deliverable-templates", null, HttpStatus.OK)).values().single { it["templateKey"].asString() == key }
        assertTrue(listed["hasLogo"].asBoolean())
        assertEquals("2E7D32", listed["accentColor"].asString())
        val svg = upload(BaseTemplate.bytes, "sgspe-base.dotx", uniqueName("svg-").lowercase(), logo = "<svg/>".toByteArray(), accent = "#123")
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, svg.status, svg.body)
        val codes = tree(svg.body)["violations"].values().map { it["code"].asString() }.toSet()
        assertEquals(setOf(BrandLogo.LOGO_INVALID, BrandLogo.ACCENT_INVALID), codes)
    }
}
