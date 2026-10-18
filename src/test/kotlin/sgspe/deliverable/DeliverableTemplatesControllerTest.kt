package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import sgspe.common.Idempotency
import wasichai.core.common.ForbiddenException
import wasichai.core.common.GlobalExceptionHandler
import java.util.concurrent.CopyOnWriteArrayList

// api.md §4.12 (#83): la subida de plantillas comprueba MANAGE_METADATA antes de leer el cuerpo. quien no lo tiene recibe el
// 403 aunque mande un archivo mayor que el tope, y el servicio no ve el archivo
class DeliverableTemplatesControllerTest {
    private val calls = CopyOnWriteArrayList<String>()

    private val templates =
        mock(DeliverableTemplates::class.java) { invocation ->
            calls += invocation.method.name
            if (invocation.method.name == "authorizeUpload") throw ForbiddenException("Subir una plantilla exige la acción MANAGE_METADATA sobre deliverable")
            null
        }

    private val client =
        WebTestClient
            .bindToController(
                DeliverableTemplatesController(
                    templates,
                    mock(Idempotency::class.java),
                    DeliverableProperties(templates = DeliverableProperties.Templates(maxBytes = 10)),
                    GlobalExceptionHandler("https://sgspe.test/problems")
                )
            ).controllerAdvice(GlobalExceptionHandler("https://sgspe.test/problems"))
            .build()

    @Test
    fun `sin permiso es 403 antes de leer el cuerpo, aunque el archivo pase del tope`() {
        val parts =
            MultipartBodyBuilder()
                .apply {
                    part("file", ByteArray(100) { 'x'.code.toByte() }, MediaType.APPLICATION_OCTET_STREAM).filename("base.docx")
                    part("templateKey", "base")
                }.build()
        client
            .post()
            .uri("/api/sgspe/deliverable-templates")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(parts))
            .exchange()
            .expectStatus()
            .isForbidden
        assertEquals(listOf("authorizeUpload"), calls)
    }
}
