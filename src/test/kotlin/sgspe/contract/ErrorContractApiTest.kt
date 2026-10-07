package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import sgspe.SgspeApiTest
import tools.jackson.databind.JsonNode
import wasichai.test.WasichaiIntegrationTest

// RNF-INT-001 (api.md §3.3): Problem Details de wasichai para 400, 401, 403, 404 y 409
class ErrorContractApiTest : SgspeApiTest() {
    private fun problem(
        response: Response,
        status: HttpStatus
    ): JsonNode {
        assertEquals(status, response.status, response.body)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.headers.contentType), "${response.headers.contentType}")
        val problem = tree(response.body)
        assertEquals(status.value(), problem["status"].asInt())
        assertEquals("https://wasichai.dev/problems/${status.value()}", problem["type"].asString())
        assertTrue(problem["title"].asString().isNotBlank())
        return problem
    }

    @Test
    fun `400 nombra el campo con un valor invalido`() {
        val name = uniqueName("errado")
        createObject(name, listOf(field("visits", "INTEGER")))
        val problem = problem(exchange("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("visits" to "muchas"))), HttpStatus.BAD_REQUEST)
        assertEquals("visits", problem["errors"][0]["field"].asString())
    }

    @Test
    fun `un atributo que el objeto no tiene se ignora sin error`() {
        // contrato de wasichai 0.3.2 fijado a propósito (decisión 10 del plan de F1): el atributo desconocido no se guarda
        // y no hay 400. un nombre mal escrito se pierde en silencio: las pantallas y comandos de sgspe mandan solo campos
        // del modelo. si wasichai empieza a rechazarlo, esta prueba falla y se actualiza api.md
        val name = uniqueName("ignorado")
        createObject(name, listOf(field("code", "TEXT")))
        val created = tree(send("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "I-1", "no_existe" to "x")), HttpStatus.CREATED))
        assertEquals("I-1", created["attributes"]["code"].asString())
        assertTrue(created["attributes"]["no_existe"] == null)
    }

    @Test
    fun `401 del login es Problem Details`() {
        val body = mapOf("email" to WasichaiIntegrationTest.ADMIN_EMAIL, "password" to "errada-de-prueba")
        problem(exchange("POST", "/api/auth/login", body, token = null), HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `401 sin token lo emite spring security con WWW-Authenticate y sin cuerpo`() {
        // contrato de wasichai 0.3.2 fijado a propósito: api.md §3.3 pide Problem Details también aquí. si wasichai empieza
        // a mandarlo, esta prueba falla y se actualiza api.md (decisión 7 del plan de F1)
        val response = exchange("GET", "/api/auth/me", token = null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertTrue(response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE)!!.startsWith("Bearer"))
        assertEquals("", response.body)
    }

    @Test
    fun `403 cuando falta el permiso`() {
        val name = uniqueName("vedado")
        createObject(name, listOf(field("code", "TEXT")))
        problem(exchange("GET", "/api/objects/$name/records", token = userWithRole("ANALYST").token), HttpStatus.FORBIDDEN)
    }

    @Test
    fun `404 de un objeto que no existe`() {
        problem(exchange("GET", "/api/objects/${uniqueName("ausente")}/records"), HttpStatus.NOT_FOUND)
    }

    @Test
    fun `409 nombra el campo unico repetido`() {
        val name = uniqueName("unico")
        createObject(name, listOf(field("code", "TEXT", unique = true)))
        createRecord(name, mapOf("code" to "U-1"))
        val problem = problem(exchange("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "U-1"))), HttpStatus.CONFLICT)
        assertEquals("code", problem["errors"][0]["field"].asString())
    }
}
