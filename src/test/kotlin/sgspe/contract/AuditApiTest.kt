package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import wasichai.test.WasichaiIntegrationTest
import java.time.Instant

// RQ-AUD-001 y RQ-AUD-002 (security.md §13.1): quién, qué, cuándo, por qué, desde y hacia; motivo obligatorio con tildes
// de ida y vuelta; appendOnly responde 409 y el intento no se audita
class AuditApiTest : SgspeApiTest() {
    @Test
    fun `una escritura deja quien, que, cuando, por que, desde y hacia`() {
        val name = uniqueName("auditado")
        createObject(name, listOf(field("visits", "INTEGER")))
        val before = Instant.now().minusSeconds(60)
        val id = createRecord(name, mapOf("visits" to 10))
        send(
            "PUT",
            "/api/objects/$name/records/$id",
            mapOf("attributes" to mapOf("visits" to 20)),
            HttpStatus.OK,
            headers =
                mapOf(REASON to reason("corrección del conteo"))
        )

        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, HttpStatus.OK))
        assertEquals(listOf("UPDATE", "CREATE"), history.values().map { it["operation"].asString() })
        val update = history.values().first()
        assertEquals(WasichaiIntegrationTest.ADMIN_EMAIL, update["userEmail"].asString())
        assertEquals(name, update["objectName"].asString())
        assertEquals(id, update["recordId"].asString())
        assertTrue(Instant.parse(update["occurredAt"].asString()).isAfter(before))
        assertEquals("corrección del conteo", update["reason"].asString())
        val change = update["changes"].values().single { it["field"].asString() == "visits" }
        assertEquals(10, change["before"].asInt())
        assertEquals(20, change["after"].asInt())
        // quien lee todo el tenant ve lo mismo en /api/audit
        assertEquals(2, tree(send("GET", "/api/audit?objectName=$name&recordId=$id", null, HttpStatus.OK)).size())
    }

    @Test
    fun `un objeto requiresReason rechaza la escritura sin motivo y no guarda nada`() {
        val name = uniqueName("motivado")
        createObject(name, listOf(field("code", "TEXT")), mapOf("requiresReason" to true))
        val problem = tree(send("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "M-1")), HttpStatus.BAD_REQUEST))
        assertEquals("reason", problem["errors"][0]["field"].asString())
        assertEquals(0, tree(send("GET", "/api/objects/$name/records", null, HttpStatus.OK))["totalElements"].asInt())
        createRecord(name, mapOf("code" to "M-1"), mapOf(REASON to reason("alta con motivo")))
        assertEquals(1, tree(send("GET", "/api/objects/$name/records", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `un objeto appendOnly responde 409 al cambiarlo o borrarlo y el intento no se audita`() {
        val name = uniqueName("historia")
        createObject(name, listOf(field("code", "TEXT")), mapOf("appendOnly" to true))
        val id = createRecord(name, mapOf("code" to "H-1"))
        send("PUT", "/api/objects/$name/records/$id", mapOf("attributes" to mapOf("code" to "H-2")), HttpStatus.CONFLICT)
        send("DELETE", "/api/objects/$name/records/$id", null, HttpStatus.CONFLICT)
        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, HttpStatus.OK))
        assertEquals(listOf("CREATE"), history.values().map { it["operation"].asString() })
    }
}
