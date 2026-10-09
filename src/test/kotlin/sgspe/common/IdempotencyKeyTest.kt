package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ValidationException

// el contrato de Idempotency-Key de wasichai (WAS-ADR-0058) en los comandos de /api/sgspe/** (decisión D3): la cabecera, la
// identidad de una petición y la respuesta que recibe un reenvío
class IdempotencyKeyTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `sin cabecera no hay clave, y una clave valida pasa tal cual`() {
        assertNull(IdempotencyKey.parse(null))
        assertEquals("0b8e9d2a-3c1f", IdempotencyKey.parse("0b8e9d2a-3c1f"))
        assertEquals("x".repeat(128), IdempotencyKey.parse("x".repeat(128)))
    }

    @Test
    fun `una clave vacia, en blanco, de mas de 128 caracteres o fuera de ASCII imprimible es un 400 sobre la cabecera`() {
        for (bad in listOf("", "   ", "x".repeat(129), "clave-ñ", "tab\tclave")) {
            val e = assertThrows<ValidationException> { IdempotencyKey.parse(bad) }
            assertEquals(IdempotencyKey.HEADER, e.violations.single().field, bad)
        }
    }

    @Test
    fun `el orden de las claves y los espacios no cambian la peticion, la ruta, el metodo o un valor si`() {
        val a = IdempotencyKey.hash("POST", "/api/sgspe/x", IdempotencyKey.canonical(json, mapOf("b" to 1, "a" to mapOf("d" to 2, "c" to 3))))
        val b =
            IdempotencyKey.hash(
                "POST",
                "/api/sgspe/x",
                IdempotencyKey.canonical(json, json.readValue("""{ "a": {"c":3, "d":2},  "b":1 }""", Map::class.java))
            )
        assertEquals(a, b)
        assertNotEquals(a, IdempotencyKey.hash("POST", "/api/sgspe/y", IdempotencyKey.canonical(json, mapOf("a" to mapOf("c" to 3, "d" to 2), "b" to 1))))
        assertNotEquals(a, IdempotencyKey.hash("PATCH", "/api/sgspe/x", IdempotencyKey.canonical(json, mapOf("a" to mapOf("c" to 3, "d" to 2), "b" to 1))))
        assertNotEquals(a, IdempotencyKey.hash("POST", "/api/sgspe/x", IdempotencyKey.canonical(json, mapOf("a" to mapOf("c" to 3, "d" to 2), "b" to 2))))
        assertTrue(Regex("^[0-9a-f]{64}$").matches(a))
    }

    @Test
    fun `la respuesta guardada vuelve con sus bytes, su ETag y su Location, y el reenvio lo dice`() {
        val stored = StoredAnswer(201, """{"commitment":{"id":"1"}}""", "\"2026-10-09T15:00:00Z\"", "/api/objects/commitment/records/1")
        val first = IdempotencyKey.entity(stored, replayed = false)
        val again = IdempotencyKey.entity(stored, replayed = true)
        assertEquals(201, first.statusCode.value())
        assertEquals(stored.body, String(first.body!!, Charsets.UTF_8))
        assertEquals(stored.body, String(again.body!!, Charsets.UTF_8))
        assertEquals("\"2026-10-09T15:00:00Z\"", first.headers.getFirst(HttpHeaders.ETAG))
        assertEquals("/api/objects/commitment/records/1", first.headers.location.toString())
        assertNull(first.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals("true", again.headers.getFirst(IdempotencyKey.REPLAYED))
    }

    // la respuesta que se guarda para los reenvíos es acotada: un comando responde uno o dos registros, así que pasar el tope
    // es un error del comando, que se revierte entero, y nunca una fila sin límite en sgspe.idempotency_key
    @Test
    fun `la respuesta que se guarda para un reenvio tiene un tope en bytes UTF-8`() {
        val max = IdempotencyKey.MAX_RESPONSE_BYTES
        val atCap = "x".repeat(max)
        assertEquals(atCap, IdempotencyKey.bounded(atCap))
        assertThrows<IllegalStateException> { IdempotencyKey.bounded("x".repeat(max + 1)) }
        // se cuentan bytes, no caracteres: una ñ ocupa dos
        assertThrows<IllegalStateException> { IdempotencyKey.bounded("ñ".repeat(max / 2 + 1)) }
    }
}
