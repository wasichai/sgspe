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
        val query = "/api/audit?recordId=$id&limit=500"
        val audit = tree(send("GET", query, null, HttpStatus.OK, other.token))
        assertTrue(audit.values().none { it["recordId"]?.asString() == id }, audit.toString())
        // control de la consulta: la misma, como la primera organización, sí devuelve el registro
        val own = tree(send("GET", query, null, HttpStatus.OK))
        assertTrue(own.values().any { it["recordId"]?.asString() == id }, own.toString())
        assertNotEquals(me()["organizationId"].asString(), me(other.token)["organizationId"].asString())
    }

    @Test
    fun `el administrador de otra organizacion no puede cambiar, borrar ni listar lo de la primera`() {
        val name = uniqueName("protegido")
        createObject(name, listOf(field("code", "TEXT")))
        val id = createRecord(name, mapOf("code" to "P-1"))
        val other = provisionTenant()
        val path = "/api/objects/$name/records/$id"

        assertEquals(HttpStatus.NOT_FOUND, exchange("PUT", path, mapOf("attributes" to mapOf("code" to "P-2")), other.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("DELETE", path, token = other.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/$name/records", token = other.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "$path/history", token = other.token).status)
        // la primera organización sigue viendo su registro intacto, con una sola entrada de historia
        assertEquals("P-1", tree(send("GET", path, null, HttpStatus.OK))["attributes"]["code"].asString())
        val history = tree(send("GET", "$path/history", null, HttpStatus.OK))
        assertEquals(listOf("CREATE"), history.values().map { it["operation"].asString() })
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
