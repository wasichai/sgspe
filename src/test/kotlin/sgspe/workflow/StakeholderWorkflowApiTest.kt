package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// WF-B (workflows.md): sin estado final, retirar y reincorporar, y `assess` solo para quien evalúa. la matriz de las
// transiciones que la API genérica aplica (`profile`, `retire_identified`, `retire_profiled` y `reinstate`, con cada rol
// permitido, un rol denegado y un estado equivocado) está en WorkflowMatrixApiTest. `assess` y `retire_assessed` no tienen
// aquí su caso feliz: `assess` solo la aplica el endpoint de evaluaciones (CommandTransitionGuard rechaza la genérica) y
// `retire_assessed` sale de `assessed`, que solo se alcanza por él. el plan 2.2 los agrega: `assess` por el endpoint en
// StakeholderAssessmentApiTest y `assess` genérica rechazada, el motivo obligatorio de `retire_*` y `reinstate` y la guarda
// de `profile` en StakeholderWorkflowGuardsApiTest; `retire_assessed` desde `assessed` no está en la tabla de «Interfaces»
// de 2.2 y hay que escribirla allí (en el WorkflowMatrixApiTest, `DEFERRED` la excluye)
class StakeholderWorkflowApiTest : F2ApiTest() {
    // un grupo de interés con lo que `profile` exige (RF-STK-003): tipo, ámbito, motivo de inclusión y responsable
    private fun profileable(owner: Account): String =
        fixture(
            "stakeholder",
            mapOf(
                "stakeholder_type" to "ORGANIZACION COMUNAL",
                "stakeholder_scope" to "AIDS",
                "inclusion_reason" to "Organización del área de influencia directa",
                "owner_user_id" to owner.id,
                "owner_user_email" to owner.email
            )
        ).id

    @Test
    fun `wasichai acepta el workflow sin estado final`() {
        val workflow = tree(send("GET", "/api/objects/stakeholder/workflow", null, HttpStatus.OK))
        assertEquals("wf-b-stakeholder", workflow["name"].asString())
        assertFalse(workflow["definition"]["states"].values().any { it["type"].asString() == "FINAL" })
    }

    @Test
    fun `COMMUNITY_RELATIONS_OFFICER caracteriza pero no evalua`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val stakeholder = profileable(officer)
        assertEquals("identified", state("stakeholder", stakeholder))
        move("stakeholder", stakeholder, "profile", officer.token)
        assertEquals("profiled", state("stakeholder", stakeholder))
        assertEquals(HttpStatus.FORBIDDEN, transition("stakeholder", stakeholder, "assess", officer.token).status)
        assertEquals(false to "requires role STAKEHOLDER_MANAGER or SOCIAL_MANAGER", available("stakeholder", stakeholder, officer.token)["assess"])
    }

    @Test
    fun `assess sale de profiled`() {
        val manager = member("STAKEHOLDER_MANAGER")
        val stakeholder = profileable(manager)
        assertEquals(HttpStatus.CONFLICT, transition("stakeholder", stakeholder, "assess", manager.token).status)
        assertEquals("identified", state("stakeholder", stakeholder))
    }

    @Test
    fun `STAKEHOLDER_MANAGER retira y reincorpora, y vuelve a identified`() {
        val manager = member("STAKEHOLDER_MANAGER")
        val stakeholder = profileable(manager)
        move("stakeholder", stakeholder, "profile", manager.token)
        move("stakeholder", stakeholder, "retire_profiled", manager.token, "ya no tiene relación con el proyecto")
        assertEquals("retired", state("stakeholder", stakeholder))
        assertEquals(setOf("reinstate"), available("stakeholder", stakeholder, manager.token).keys)
        move("stakeholder", stakeholder, "reinstate", manager.token, "vuelve a la zona de trabajo")
        assertEquals("identified", state("stakeholder", stakeholder))
    }

    @Test
    fun `FIELD_AGENT, con u, no retira y el rol sin UPDATE recibe 403`() {
        val manager = member("STAKEHOLDER_MANAGER")
        val stakeholder = profileable(manager)
        assertEquals(HttpStatus.FORBIDDEN, transition("stakeholder", stakeholder, "retire_identified", member("FIELD_AGENT").token, "prueba").status)
        assertEquals(HttpStatus.FORBIDDEN, transition("stakeholder", stakeholder, "retire_identified", member("PROJECT_MANAGER").token, "prueba").status)
        assertEquals("identified", state("stakeholder", stakeholder))
    }
}
