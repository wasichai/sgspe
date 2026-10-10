package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-M con WarningWriteGuard (workflows.md, especificación de F4 §3.7, ASM-103): el incidente se revisa y se atiende sin
// severidad ni motivo obligatorio, y se cierra solo con su respuesta inmediata y sus consecuencias
class IncidentWorkflowApiTest : F4ApiTest() {
    @Test
    fun `cerrar sin consecuencias se rechaza y con ellas cierra`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val social = member("SOCIAL_MANAGER")
        val id = incident(testProject(), officer, mapOf("consequences" to null))
        move("incident", id, "start_review", officer.token)
        move("incident", id, "start_response", officer.token)
        val response = transition("incident", id, "close", social.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("FIELD_REQUIRED", code(response))
        assertEquals(listOf("consequences"), problemFields(response.body))
        assertEquals(HttpStatus.OK, edit("incident", id, mapOf("consequences" to "Dos horas sin acceso."), social.token).status)
        move("incident", id, "close", social.token)
        assertEquals("closed", state("incident", id))
    }
}
