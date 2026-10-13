package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import sgspe.common.RestoreAwareAutomationDispatcher
import wasichai.automation.AutomationDispatcher
import wasichai.test.WasichaiIntegrationTest
import java.security.MessageDigest
import java.util.HexFormat

// la app misma (SgspeApplication) sobre PostGIS: su salud responde, los módulos que instala responden, los que deja
// fuera no, y lo propio de sgspe exige token (adaptado de caja-backend@dc9e391, src/test/kotlin/caja/CajaSmokeTest.kt)
class SgspeSmokeTest : WasichaiIntegrationTest() {
    @Autowired
    private lateinit var context: ApplicationContext

    // un solo despachador de automatizaciones, el de sgspe (ronda branchfix de 4.2b, R-I2): dos duplicarían cada corrida
    @Test
    fun `hay un solo despachador de automatizaciones y es el de sgspe`() {
        val dispatchers = context.getBeansOfType(AutomationDispatcher::class.java).values
        assertEquals(1, dispatchers.size, dispatchers.toString())
        assertTrue(dispatchers.single() is RestoreAwareAutomationDispatcher)
    }

    @Test
    fun `la salud responde UP`() {
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `los modulos instalados responden y los que se dejan fuera no`() {
        val token = bearer()
        val name = uniqueName("objeto")
        client
            .post()
            .uri("/api/objects")
            .header(HttpHeaders.AUTHORIZATION, token)
            .bodyValue(
                mapOf(
                    "name" to name,
                    "label" to name,
                    "pluralLabel" to name,
                    "fields" to listOf(mapOf("name" to "code", "label" to "Código", "type" to "TEXT"))
                )
            ).exchange()
            .expectStatus()
            .isCreated
        // views, forms y pages: los starters de F1; gis desde F2
        listOf("/api/objects/$name/views", "/api/objects/$name/forms", "/api/pages", "/api/gis/layers").forEach { path ->
            client
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isOk
        }
        // workflow desde F2: un objeto sin workflow responde 404 con su problem+json, no la ruta inexistente
        client
            .get()
            .uri("/api/objects/$name/workflow")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("Object '$name' has no workflow")
        // documents, automation y notifications desde F3
        listOf("/api/objects/$name/document-types", "/api/automation-runs", "/api/auth/me/notifications").forEach { path ->
            client
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isOk
        }
        // agent desde F7: sin clave del proveedor (las pruebas la dejan en blanco) responde enabled=false
        client
            .get()
            .uri("/api/agent/status")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.enabled")
            .isEqualTo(false)
    }

    @Test
    fun `files acepta un campo FILE y guarda una subida con su hash`() {
        // D1 (WAS-ADR-0061): el tipo FILE existe en la metadata y una subida preparada responde su descriptor
        val token = bearer()
        val name = uniqueName("archivo")
        client
            .post()
            .uri("/api/objects")
            .header(HttpHeaders.AUTHORIZATION, token)
            .bodyValue(
                mapOf(
                    "name" to name,
                    "label" to name,
                    "pluralLabel" to name,
                    "fields" to listOf(mapOf("name" to "scan", "label" to "Archivo", "type" to "FILE"))
                )
            ).exchange()
            .expectStatus()
            .isCreated
        val pdf = "%PDF-1.4\n%prueba de sgspe\n".toByteArray()
        val parts = MultipartBodyBuilder().apply { part("file", pdf).filename("prueba.pdf") }.build()
        client
            .post()
            .uri("/api/objects/$name/files/scan")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(parts))
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.contentType")
            .isEqualTo("application/pdf")
            .jsonPath("$.size")
            .isEqualTo(pdf.size)
            .jsonPath("$.sha256")
            .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)))
    }

    @Test
    fun `una ruta de sgspe sin token da 401`() {
        client
            .get()
            .uri("/api/sgspe/no-existe")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}
