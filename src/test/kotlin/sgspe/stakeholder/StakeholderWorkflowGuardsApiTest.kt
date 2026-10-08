package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-B que dependen de las guardas de 2.2 (StakeholderWorkflowApiTest): assess por la API genérica se
// rechaza; caracterizar exige sus datos; retirar sin motivo es 400; las reglas de cargos y de la matriz de priorización.
// retire_assessed desde assessed se prueba en StakeholderAssessmentApiTest: assessed solo se alcanza por el endpoint de
// evaluaciones
class StakeholderWorkflowGuardsApiTest : DomainApiTest() {
    @Test
    fun `assess por la API generica es 403 aunque el rol la tenga`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = stakeholder(project, profileFields(manager))
        move("stakeholder", stakeholder, "profile", manager.token)
        assertEquals(HttpStatus.FORBIDDEN, transition("stakeholder", stakeholder, "assess", manager.token).status)
    }

    @Test
    fun `caracterizar exige tipo, ambito, motivo y responsable, y retirar y reincorporar exigen motivo`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = stakeholder(project)
        val response = transition("stakeholder", stakeholder, "profile", manager.token)
        assertEquals(HttpStatus.CONFLICT, response.status)
        assertEquals(listOf("stakeholder_type"), problemFields(response.body))
        assertEquals(listOf("reason"), problemFields(transition("stakeholder", stakeholder, "retire_identified", manager.token).body))
        move("stakeholder", stakeholder, "retire_identified", manager.token, "duplicado de otro registro")
        assertEquals(listOf("reason"), problemFields(transition("stakeholder", stakeholder, "reinstate", manager.token).body))
        move("stakeholder", stakeholder, "reinstate", manager.token, "se separó de la organización")
    }

    @Test
    fun `un cargo sin persona solo VACANTE o PREVISTO`() {
        val organization = organization()
        val vigente =
            exchange(
                "POST",
                "/api/objects/position/records",
                mapOf("attributes" to mapOf("organization" to organization, "position_title" to "Presidente", "position_status" to "VIGENTE"))
            )
        assertEquals(HttpStatus.BAD_REQUEST, vigente.status)
        assertEquals(listOf("position_status"), problemFields(vigente.body))
        createRecord("position", mapOf("organization" to organization, "position_title" to "Presidente", "position_status" to "VACANTE"))
    }

    @Test
    fun `dos filas sin tipo para la misma combinacion de la matriz se rechazan`() {
        val project = projectWithCrs()
        val row =
            mapOf(
                "project" to project,
                "influence_group" to "ESTRATEGICO",
                "stance" to "EN CONTRA",
                "stakeholder_scope" to "AIDS",
                "information_priority" to "MUY ALTA"
            )
        createRecord("prioritization_rule", row)
        assertEquals(HttpStatus.CONFLICT, exchange("POST", "/api/objects/prioritization_rule/records", mapOf("attributes" to row)).status)
        createRecord("prioritization_rule", row + ("stakeholder_type" to "AUTORIDAD LOCAL"))
    }
}
