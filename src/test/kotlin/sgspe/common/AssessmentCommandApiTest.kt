package sgspe.common

import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import java.util.UUID

// la base de las pruebas de Idempotency-Key y de JOB-08 (plan 3.2, tareas 3 y 4): el registro de evaluaciones de F2, el
// primer comando que honra la clave. no usa el modelo de F3, así que estas pruebas no esperan al plan 3.1
abstract class AssessmentCommandApiTest : DomainApiTest() {
    protected fun withKey(key: String = UUID.randomUUID().toString()): Map<String, String> = mapOf(IdempotencyKey.HEADER to key)

    // los quince indicadores de una evaluación (RF-STK-006), todos en `value`
    protected fun indicators(value: Int = 3): Map<String, Any?> =
        listOf(
            "affect_environment",
            "affect_livelihood",
            "affect_property_status",
            "opportunity_work",
            "opportunity_development",
            "opportunity_benefits",
            "power_legal_action",
            "power_credibility",
            "power_permit_control",
            "power_action_scope",
            "power_organization_level",
            "power_convening_capacity",
            "power_network_membership",
            "power_group_mobilization",
            "power_other_pressure"
        ).associateWith { value }

    // un grupo de interés caracterizado y quien lo evalúa (STAKEHOLDER_MANAGER asignado al proyecto de la prueba)
    protected fun assessable(): Pair<String, Account> {
        val project = testProject()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val id = stakeholder(project, profileFields(manager, "AUTORIDAD LOCAL"))
        move("stakeholder", id, "profile", manager.token)
        return id to manager
    }

    protected fun assess(
        stakeholder: String,
        account: Account,
        headers: Map<String, String>,
        date: String = "2026-05-12",
        value: Int = 3
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/stakeholders/$stakeholder/assessments",
            mapOf("attributes" to indicators(value) + mapOf("assessment_date" to date, "basis" to "ENTREVISTA")),
            account.token,
            headers + withReason("evaluación")
        )

    protected fun assessments(stakeholder: String): Int =
        tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK))["totalElements"].asInt()
}
