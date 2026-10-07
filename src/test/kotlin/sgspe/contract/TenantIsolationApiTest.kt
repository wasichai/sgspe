package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest

// RQ-SEC-002 y RQ-SEC-017: el tenant es el del token; el administrador de otra organización no ve nada de la primera
class TenantIsolationApiTest : SgspeApiTest() {
    @Test
    fun `el administrador de otra organizacion no ve los objetos, los registros ni la auditoria de la primera`() {
        val name = uniqueName("aislado")
        createObject(name, listOf(field("code", "TEXT")))
        val id = createRecord(name, mapOf("code" to "A-1"))
        val other = provisionTenant()

        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/$name/records/$id", token = other.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/$name", token = other.token).status)
        val audit = tree(send("GET", "/api/audit?limit=500", null, HttpStatus.OK, other.token))
        assertTrue(audit.values().none { it["recordId"]?.asString() == id }, audit.toString())
        assertNotEquals(me()["organizationId"].asString(), me(other.token)["organizationId"].asString())
    }

    @Test
    fun `cada organizacion tiene su propia copia del modelo y de los roles`() {
        val other = provisionTenant()
        // sin apply_roles.py para ella, la organización nueva solo tiene el ADMIN que crea wasichai
        val roles = tree(send("GET", "/api/roles", null, HttpStatus.OK, other.token)).values().map { it["name"].asString() }
        assertEquals(listOf("ADMIN"), roles)
        assertEquals(0, tree(send("GET", "/api/objects", null, HttpStatus.OK, other.token)).size())
    }
}
