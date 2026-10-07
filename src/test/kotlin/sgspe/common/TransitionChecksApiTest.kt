package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import java.util.UUID

// POST /api/sgspe/transition-checks (api.md §4.1): lo que bloquearía una transición y sus advertencias, sin escribir
class TransitionChecksApiTest : DomainApiTest() {
    private fun check(
        objectName: String,
        id: String,
        transition: String
    ) = tree(send("POST", "/api/sgspe/transition-checks", mapOf("object" to objectName, "recordId" to id, "transition" to transition), HttpStatus.OK))

    @Test
    fun `una transicion que pide motivo trae la advertencia, y una de comando se bloquea`() {
        val project = projectWithCrs()
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de prueba"))
        val cancel = check("field_visit", visit, "cancel")
        assertTrue(cancel["allowed"].asBoolean())
        assertEquals("REASON_REQUIRED", cancel["warnings"][0]["code"].asString())
        // la forma de cada ítem es la que fija 2.3: code, message y field (null si no apunta a un campo)
        assertEquals(setOf("code", "message", "field"), cancel["warnings"][0].propertyNames().toSet())
        assertEquals("reason", cancel["warnings"][0]["field"].asString())
        val stakeholder = stakeholder(project)
        val assess = check("stakeholder", stakeholder, "assess")
        assertEquals(setOf("COMMAND_ONLY", "STATE_MISMATCH"), assess["blocking"].values().map { it["code"].asString() }.toSet())
        val stateMismatch = assess["blocking"].values().first { it["code"].asString() == "STATE_MISMATCH" }
        assertTrue(stateMismatch["field"].isNull)
    }

    @Test
    fun `en seco no escribe nada y lo bloqueado por datos trae su campo`() {
        val project = projectWithCrs(mapOf("working_crs" to null))
        move("project", project, "submit_for_review")
        val before = tree(send("GET", "/api/objects/project/records/$project", null, HttpStatus.OK))["updatedAt"].asString()
        val approve = check("project", project, "approve")
        assertEquals(false, approve["allowed"].asBoolean())
        val byCode = approve["blocking"].values().associate { it["code"].asString() to it["field"].asString() }
        assertEquals(mapOf("WORKING_CRS_REQUIRED" to "working_crs", "PARTY_REQUIRED" to "project_party"), byCode)
        assertEquals("in_review", state("project", project))
        assertEquals(before, tree(send("GET", "/api/objects/project/records/$project", null, HttpStatus.OK))["updatedAt"].asString())
    }

    @Test
    fun `un cuerpo con una propiedad desconocida es 400 y un registro de otro es 404`() {
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf(
                    "object" to "engagement",
                    "recordId" to UUID.randomUUID().toString(),
                    "transition" to "close",
                    "extra" to 1
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("extra"), problemFields(response.body))
        val missing =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "engagement", "recordId" to UUID.randomUUID().toString(), "transition" to "close")
            )
        assertEquals(HttpStatus.NOT_FOUND, missing.status)
    }

    @Test
    fun `un parametro de consulta desconocido es 422`() {
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks?proyecto=1",
                mapOf("object" to "engagement", "recordId" to UUID.randomUUID().toString(), "transition" to "close")
            )
        assertEquals(HttpStatus.valueOf(422), response.status)
        assertEquals(listOf("proyecto"), problemFields(response.body))
    }
}
