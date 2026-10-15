package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import wasichai.core.common.GlobalExceptionHandler
import java.util.UUID

// las rutas de subida cerradas de DeliverableContentController (api.md §4.12): cada POST es 403 FILE_WRITE_NOT_ALLOWED y
// no un 500. el manejador no puede ser suspend sin parámetros: la reflexión de Kotlin no halla su Continuation al armar el
// HandlerResult (NoSuchElementException «Array is empty», CI de la PR 74)
class DeliverableUploadRoutesTest {
    private val client =
        WebTestClient
            .bindToController(DeliverableContentController(mock(DeliverableContent::class.java)))
            .controllerAdvice(GlobalExceptionHandler("https://sgspe.test/problems"))
            .build()

    @Test
    fun `cada ruta de subida cerrada responde 403 FILE_WRITE_NOT_ALLOWED`() {
        val id = UUID.randomUUID()
        val parts = MultipartBodyBuilder().apply { part("file", "x".toByteArray(), MediaType.APPLICATION_PDF).filename("x.pdf") }.build()
        for (path in listOf(
            "/api/objects/deliverable/files/${Deliverables.DRAFT_INTERNAL_PDF}",
            "/api/objects/deliverable_version/files/pdf",
            "/api/objects/deliverable_template/files/file",
            "/api/objects/deliverable/records/$id/files/${Deliverables.DRAFT_INTERNAL_PDF}",
            "/api/objects/deliverable_version/records/$id/files/pdf",
            "/api/objects/deliverable_template/records/$id/files/file"
        )) {
            val body =
                client
                    .post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .exchange()
                    .expectStatus()
                    .isForbidden
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody!!
            assertTrue(body.contains("FILE_WRITE_NOT_ALLOWED"), "$path: $body")
        }
    }
}
