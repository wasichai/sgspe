package sgspe.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import java.util.UUID

// RQ-AUD-006 y RNF-OBS-001: el identificador en toda respuesta, también en un 401 y en un error de wasichai, y una línea
// de acceso por petición sin la cadena de consulta
@ExtendWith(OutputCaptureExtension::class)
class ObservabilityApiTest : SgspeApiTest() {
    private fun id(response: Response): String? = response.headers.getFirst(CorrelationId.HEADER)

    @Test
    fun `devuelve el identificador del cliente y genera uno si no cumple el formato`() {
        assertEquals("prueba-api.1", id(exchange("GET", "/api/auth/me", headers = mapOf(CorrelationId.HEADER to "prueba-api.1"))))
        for (hostile in listOf("x".repeat(65), "con espacio", "<script>")) {
            val returned = id(exchange("GET", "/api/auth/me", headers = mapOf(CorrelationId.HEADER to hostile)))
            assertNotEquals(hostile, returned)
            UUID.fromString(returned)
        }
    }

    @Test
    fun `un 401 tambien lleva el identificador`() {
        val response = exchange("GET", "/api/auth/me", token = null, headers = mapOf(CorrelationId.HEADER to "prueba-401"))
        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertEquals("prueba-401", id(response))
    }

    @Test
    fun `un error de wasichai tambien lo lleva`() {
        val response = exchange("GET", "/api/objects/${uniqueName("ausente")}/records")
        assertEquals(HttpStatus.NOT_FOUND, response.status)
        UUID.fromString(id(response))
    }

    @Test
    fun `las sondas de salud lo llevan`() {
        val response = exchange("GET", "/actuator/health/liveness", token = null)
        assertEquals(HttpStatus.OK, response.status)
        UUID.fromString(id(response))
    }

    @Test
    fun `una linea de acceso por peticion, sin la cadena de consulta`(output: CapturedOutput) {
        exchange("GET", "/api/auth/me?pista=secreto-de-prueba", token = null, headers = mapOf(CorrelationId.HEADER to "acceso-1"))
        // la línea se escribe cuando termina la respuesta: se espera hasta 5 s
        val deadline = System.nanoTime() + 5_000_000_000
        while (!output.out.contains("correlationId=acceso-1") && System.nanoTime() < deadline) Thread.sleep(50)
        val line = output.out.lines().single { it.contains("correlationId=acceso-1") }
        assertTrue(line.contains("GET /api/auth/me 401"), line)
        assertFalse(output.out.contains("secreto-de-prueba"))
    }
}
