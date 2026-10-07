package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-A que dependen de las guardas de 2.2 (workflows.md §Pruebas, ProjectWorkflowApiTest): approve sin
// working_crs o sin parte CLIENTE u OPERADOR se rechaza; con la política apagada pasa; resume vuelve a verificar; sin la
// acción APPROVE se rechaza; cambiar stage sin motivo es 400; devolver y anular sin motivo es 400
class ProjectWorkflowGuardsApiTest : DomainApiTest() {
    private fun inReview(attributes: Map<String, Any?> = emptyMap()): String {
        val project = projectWithCrs(attributes)
        move("project", project, "submit_for_review")
        return project
    }

    private fun party(
        project: String,
        role: String
    ) = createRecord("project_party", mapOf("project" to project, "organization" to organization(), "party_role" to role))

    @Test
    fun `approve exige working_crs y una parte CLIENTE u OPERADOR`() {
        val project = inReview(mapOf("working_crs" to null))
        party(project, "CONSULTORA")
        val response = transition("project", project, "approve")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("working_crs"), problemFields(response.body))
        edit("project", project, mapOf("working_crs" to "EPSG 32717"))
        assertEquals(listOf("project_party"), problemFields(transition("project", project, "approve").body))
        party(project, "OPERADOR")
        move("project", project, "approve")
    }

    @Test
    fun `con policy_activation_preconditions en falso approve pasa sin parte`() {
        val project = inReview(mapOf("policy_activation_preconditions" to false))
        move("project", project, "approve")
    }

    @Test
    fun `resume vuelve a verificar las precondiciones`() {
        val project = inReview()
        party(project, "CLIENTE")
        move("project", project, "approve")
        move("project", project, "suspend", reason = "paralización por lluvias")
        edit("project", project, mapOf("working_crs" to null))
        assertEquals(HttpStatus.CONFLICT, transition("project", project, "resume").status)
    }

    @Test
    fun `la guarda exige APPROVE aparte del rol, y lo informa en seco`() {
        val project = inReview()
        party(project, "CLIENTE")
        val manager = member("PROJECT_MANAGER", project)
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "project", "recordId" to project, "transition" to "approve"),
                    HttpStatus.OK,
                    manager.token
                )
            )
        assertEquals(listOf("ACTION_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        move("project", project, "approve", member("PROJECT_ADMIN", project).token)
    }

    @Test
    fun `cambiar stage sin motivo es 400 y con motivo pasa, y su primer valor no lo pide`() {
        val project = projectWithCrs()
        assertEquals(HttpStatus.OK, edit("project", project, mapOf("stage" to "EXPLORACION")).status)
        val response = edit("project", project, mapOf("stage" to "FACTIBILIDAD"))
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("reason"), problemFields(response.body))
        assertEquals(
            HttpStatus.OK,
            edit("project", project, mapOf("stage" to "FACTIBILIDAD"), headers = mapOf(REASON to reason("cambio de etapa aprobado"))).status
        )
    }

    @Test
    fun `devolver y anular sin motivo es 400`() {
        val project = inReview()
        assertEquals(listOf("reason"), problemFields(transition("project", project, "return_to_draft").body))
        move("project", project, "cancel_in_review", reason = "el cliente desistió")
    }
}
