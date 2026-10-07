package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Alcance, opción A» (RQ-SEC-006): sin asignación vigente no se escribe en el proyecto; con ella sí;
// ADMIN queda exento; los hijos se acotan por su raíz; cambiar de proyecto exige los dos
class ProjectScopeApiTest : DomainApiTest() {
    private fun createEngagement(
        project: String,
        token: String
    ) = exchange(
        "POST",
        "/api/objects/engagement/records",
        mapOf("attributes" to mapOf("project" to project, "engagement_type" to "REUNION", "title" to "Reunión")),
        token
    )

    @Test
    fun `sin asignacion es 403 y con una asignacion vigente se escribe`() {
        val project = projectWithCrs()
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val denied = createEngagement(project, outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assign(outsider, project, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.CREATED, createEngagement(project, outsider.token).status)
    }

    @Test
    fun `una asignacion inactiva o vencida no da alcance, y ADMIN no la necesita`() {
        val project = projectWithCrs()
        val inactive = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(inactive, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("active" to false))
        assertEquals(HttpStatus.FORBIDDEN, createEngagement(project, inactive.token).status)
        val expired = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(expired, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("start_date" to "2020-01-01", "end_date" to "2020-12-31"))
        assertEquals(HttpStatus.FORBIDDEN, createEngagement(project, expired.token).status)
        assertEquals(HttpStatus.CREATED, createEngagement(project, token).status)
    }

    @Test
    fun `un hijo se acota por su raiz y mover un registro de proyecto exige asignacion a los dos`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", mine)
        val foreign = engagement(other)
        val participant =
            exchange(
                "POST",
                "/api/objects/engagement_participant/records",
                mapOf("attributes" to mapOf("engagement" to foreign, "represented_people_count" to 1)),
                officer.token
            )
        assertEquals(HttpStatus.FORBIDDEN, participant.status)
        val own = engagement(mine, token = officer.token)
        assertEquals(HttpStatus.FORBIDDEN, edit("engagement", own, mapOf("project" to other), officer.token).status)
        assign(officer, other, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.OK, edit("engagement", own, mapOf("project" to other), officer.token).status)
    }

    @Test
    fun `el alta de un proyecto y las asignaciones no piden asignacion previa`() {
        val admin = userWithRole("PROJECT_ADMIN")
        val project =
            tree(
                send(
                    "POST",
                    "/api/objects/project/records",
                    mapOf("attributes" to mapOf("code" to uniqueName("PRY-"), "name" to "Nuevo")),
                    HttpStatus.CREATED,
                    admin.token
                )
            )["id"].asString()
        val response =
            exchange(
                "POST",
                "/api/objects/project_assignment/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to project,
                            "member_user_id" to admin.id,
                            "member_user_email" to admin.email,
                            "role_name" to "PROJECT_ADMIN",
                            "active" to true
                        )
                ),
                admin.token
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals(HttpStatus.OK, transition("project", project, "submit_for_review", admin.token).status)
    }
}
