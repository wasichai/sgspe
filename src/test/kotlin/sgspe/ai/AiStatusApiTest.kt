package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// GET /api/sgspe/ai/status con la configuración por defecto (ASM-015, ASM-128): la IA viene apagada y no hay proveedor
class AiStatusApiTest : F7ApiTest() {
    @Test
    fun `la IA viene apagada y el estado lo dice`() {
        val body = tree(send("GET", "/api/sgspe/ai/status", null, HttpStatus.OK))
        assertFalse(body["enabled"].asBoolean())
        assertFalse(body["providerAvailable"].asBoolean())
        assertEquals(emptyList<String>(), body["useCases"].values().map { it.asString() })
    }

    @Test
    fun `sin token es 401 y un parametro desconocido es 422`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/sgspe/ai/status", null, null).status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/ai/status?caso=AI-05").status)
    }
}
