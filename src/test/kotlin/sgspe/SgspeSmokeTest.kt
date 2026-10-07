package sgspe

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import wasichai.test.WasichaiIntegrationTest

// la app misma (SgspeApplication) sobre PostGIS: su salud responde, los módulos que instala responden, los que deja
// fuera no, y lo propio de sgspe exige token (adaptado de caja-backend@dc9e391, src/test/kotlin/caja/CajaSmokeTest.kt)
class SgspeSmokeTest : WasichaiIntegrationTest() {
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
        // documents, automation y agent llegan en su fase: hoy sus rutas no existen
        listOf(
            "/api/objects/$name/document-types",
            "/api/automation-runs",
            "/api/agent/status"
        ).forEach { path ->
            client
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isNotFound
        }
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
