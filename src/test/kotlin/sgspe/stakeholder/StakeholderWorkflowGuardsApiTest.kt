package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-B que dependen de las guardas de 2.2 (StakeholderWorkflowApiTest): assess por la API genérica se
// rechaza; caracterizar exige sus datos; retirar sin motivo es 400; las reglas de cargos y de la matriz de priorización;
// y retire_assessed desde assessed (P2), que ninguna prueba de 2.1 alcanza porque assessed solo se llega por el endpoint de
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

    // un grupo de interés evaluado por el endpoint, que es la única puerta a assessed
    private fun assessed(
        project: String,
        evaluator: Account
    ): String {
        val stakeholder = stakeholder(project, profileFields(evaluator))
        val response =
            exchange(
                "POST",
                "/api/sgspe/stakeholders/$stakeholder/assessments",
                mapOf("attributes" to InfluenceClassifier.INDICATORS.associateWith { 3 } + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA")),
                evaluator.token,
                mapOf(REASON to reason("evaluación de prueba"))
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals("assessed", state("stakeholder", stakeholder))
        return stakeholder
    }

    @Test
    fun `retire_assessed lo aplican STAKEHOLDER_MANAGER y SOCIAL_MANAGER con motivo`() {
        val project = projectWithCrs()
        listOf("STAKEHOLDER_MANAGER", "SOCIAL_MANAGER").forEach { role ->
            val who = member(role, project)
            val stakeholder = assessed(project, who)
            assertEquals(true, available("stakeholder", stakeholder, who.token)["retire_assessed"]?.first, role)
            move("stakeholder", stakeholder, "retire_assessed", who.token, "ya no participa del proyecto")
            assertEquals("retired", state("stakeholder", stakeholder), role)
        }
    }

    @Test
    fun `retire_assessed fuera de la lista de roles es 403 y el listado dice requires role`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val stakeholder = assessed(project, manager)
        val denied = transition("stakeholder", stakeholder, "retire_assessed", officer.token, "ya no participa del proyecto")
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals(
            false to "requires role STAKEHOLDER_MANAGER or SOCIAL_MANAGER",
            available("stakeholder", stakeholder, officer.token)["retire_assessed"]
        )
        assertEquals("assessed", state("stakeholder", stakeholder))
    }

    @Test
    fun `retire_assessed desde profiled es 409, y sin motivo es 400 sobre reason`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val profiled = stakeholder(project, profileFields(manager))
        move("stakeholder", profiled, "profile", manager.token)
        assertEquals(HttpStatus.CONFLICT, transition("stakeholder", profiled, "retire_assessed", manager.token, "ya no participa del proyecto").status)
        assertEquals("profiled", state("stakeholder", profiled))
        val stakeholder = assessed(project, manager)
        val response = transition("stakeholder", stakeholder, "retire_assessed", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("reason"), problemFields(response.body))
        assertEquals("assessed", state("stakeholder", stakeholder))
    }
}
