package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import java.io.File

// RQ-SEC-003 y RQ-SEC-007: los 15 roles declarados y ADMIN con sus etiquetas; AUDITOR lee la auditoría y el resto no; un
// rol declarado no administra usuarios ni roles
class RolesApiTest : SgspeApiTest() {
    private val declared: Map<String, String> =
        JSON.readTree(File("model/roles.json"))["roles"].values().associate { it["name"].asString() to it["label"].asString() }

    @Test
    fun `api roles muestra los 15 roles declarados y ADMIN, con su etiqueta y sin ownRecordsOnly`() {
        // ProjectReadScopeApiTest crea roles PRUEBA_* por la API y no los borra: no son parte de lo declarado
        val roles = tree(send("GET", "/api/roles", null, HttpStatus.OK))
        val names =
            roles
                .values()
                .map { it["name"].asString() }
                .filterNot { it.startsWith("PRUEBA_") }
                .toSet()
        assertEquals(declared.keys + "ADMIN", names)
        for (role in roles) {
            val name = role["name"].asString()
            if (name.startsWith("PRUEBA_")) continue
            if (name != "ADMIN") assertEquals(declared.getValue(name), role["label"].asString(), name)
            assertFalse(role["ownRecordsOnly"].asBoolean(), name)
        }
        val auditor = roles.values().single { it["name"].asString() == "AUDITOR" }
        val grants = auditor["permissions"].values().map { (if (it["objectName"].isNull) null else it["objectName"].asString()) to it["action"].asString() }
        assertEquals(listOf(null to "READ"), grants)
    }

    @Test
    fun `AUDITOR lee la auditoria y los registros de todo el tenant y no escribe`() {
        val name = uniqueName("leido")
        createObject(name, listOf(field("code", "TEXT")))
        createRecord(name, mapOf("code" to "L-1"))
        val auditor = userWithRole("AUDITOR")

        assertEquals(HttpStatus.OK, exchange("GET", "/api/audit", token = auditor.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/$name/records", token = auditor.token).status)
        val write = exchange("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "L-2")), auditor.token)
        assertEquals(HttpStatus.FORBIDDEN, write.status)
        assertFalse(tree(send("GET", "/api/auth/me/permissions", null, HttpStatus.OK, auditor.token))["admin"].asBoolean())
    }

    @Test
    fun `un rol sin permisos recibe 403 en la auditoria`() {
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/audit", token = userWithRole("ANALYST").token).status)
    }

    @Test
    fun `un rol declarado recibe 403 en usuarios y roles`() {
        for (role in listOf("AUDITOR", "ANALYST")) {
            val account = userWithRole(role)
            assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/users", token = account.token).status, role)
            assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/roles", token = account.token).status, role)
            val create = exchange("POST", "/api/roles", mapOf("name" to "INTRUSO", "label" to "Intruso"), account.token)
            assertEquals(HttpStatus.FORBIDDEN, create.status, role)
        }
    }
}
