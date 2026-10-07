package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// WF-O (workflows.md): una versión nace en draft y sus dos transiciones son de SOCIAL_MANAGER y PROJECT_MANAGER. que
// solo el endpoint de aprobación las aplique (CommandTransitionGuard), que reemplace la versión anterior en la misma
// transacción y que congele los miembros (TerritoryWriteGuard) lo agrega el plan 2.2 a esta clase
class AreaOfInfluenceWorkflowApiTest : F2ApiTest() {
    @Test
    fun `una version nace en draft y el listado ofrece approve solo a sus roles`() {
        val area = fixture("area_of_influence", mapOf("area_type" to "AIDS", "version_number" to 1)).id
        assertEquals("draft", state("area_of_influence", area))
        assertEquals(true to null, available("area_of_influence", area, member("SOCIAL_MANAGER").token)["approve"])
        assertEquals(true to null, available("area_of_influence", area, member("PROJECT_MANAGER").token)["approve"])
        assertEquals(false to "requires role SOCIAL_MANAGER or PROJECT_MANAGER", available("area_of_influence", area, member("GIS_ANALYST").token)["approve"])
    }

    @Test
    fun `GIS_ANALYST arma los miembros de una version en borrador pero no la aprueba`() {
        val gis = member("GIS_ANALYST")
        val area = fixture("area_of_influence", mapOf("area_type" to "AIIS", "version_number" to 1), gis).id
        fixture("area_of_influence_member", mapOf("area_of_influence" to area, "member_condition" to "INCLUIDO"), gis)
        assertEquals(HttpStatus.FORBIDDEN, transition("area_of_influence", area, "approve", gis.token).status)
        assertEquals(HttpStatus.FORBIDDEN, transition("area_of_influence", area, "approve", member("COMMUNITY_RELATIONS_OFFICER").token).status)
        assertEquals("draft", state("area_of_influence", area))
    }

    @Test
    fun `supersede sale de approved`() {
        val area = fixture("area_of_influence", mapOf("area_type" to "AIDS", "version_number" to 2)).id
        assertEquals(HttpStatus.CONFLICT, transition("area_of_influence", area, "supersede", member("SOCIAL_MANAGER").token).status)
        assertEquals("draft", state("area_of_influence", area))
    }
}
