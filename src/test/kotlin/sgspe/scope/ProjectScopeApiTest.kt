package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Alcance, opción A» (RQ-SEC-006): sin asignación vigente no se escribe en el proyecto; con ella sí;
// ADMIN queda exento; los hijos se acotan por su raíz; cambiar de proyecto exige los dos
class ProjectScopeApiTest : DomainApiTest() {
    // el 403 del alcance lleva en errors[0] el campo de la relación de alcance y, como message, el código NOT_ASSIGNED: un 403
    // por permiso o por otra guarda no lo trae, así que la prueba no se queda verde si otra guarda rechaza antes
    private fun assertNotAssigned(
        response: Response,
        field: String
    ) {
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        val error = tree(response.body)["errors"].values().single()
        assertEquals("NOT_ASSIGNED", error["message"].asString(), response.body)
        assertEquals(field, error["field"].asString(), response.body)
    }

    private fun delete(
        objectName: String,
        id: String,
        token: String
    ) = exchange("DELETE", "/api/objects/$objectName/records/$id", token = token)

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
        assertNotAssigned(createEngagement(project, outsider.token), "project")
        assign(outsider, project, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.CREATED, createEngagement(project, outsider.token).status)
    }

    @Test
    fun `una asignacion inactiva o vencida no da alcance, y ADMIN no la necesita`() {
        val project = projectWithCrs()
        val inactive = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(inactive, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("active" to false))
        assertNotAssigned(createEngagement(project, inactive.token), "project")
        val expired = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(expired, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("start_date" to "2020-01-01", "end_date" to "2020-12-31"))
        assertNotAssigned(createEngagement(project, expired.token), "project")
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
        assertNotAssigned(participant, "engagement")
        val own = engagement(mine, token = officer.token)
        assertNotAssigned(edit("engagement", own, mapOf("project" to other), officer.token), "project")
        assign(officer, other, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.OK, edit("engagement", own, mapOf("project" to other), officer.token).status)
    }

    // las bajas, las transiciones, los vínculos (desde el lado del grupo de interés) y sacar un registro de un proyecto ajeno
    // también piden asignación: wasichai manda en todos esos caminos la fila completa como `before`
    @Test
    fun `sin asignacion no se borra, ni se transiciona, ni se vincula, ni se saca un registro del proyecto`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val outsider = member("SOCIAL_MANAGER", other)
        val plan = fixture("engagement_plan", mapOf("project" to mine)).id
        assertNotAssigned(delete("engagement_plan", plan, outsider.token), "project")

        val foreign = engagement(mine)
        assertNotAssigned(transition("engagement", foreign, "schedule", outsider.token, "prueba"), "project")

        val holder = stakeholder(mine)
        assertNotAssigned(link("stakeholder", holder, "engagement_stakeholder", foreign, outsider.token), "project")
        // con asignación al proyecto, el mismo vínculo se acepta
        val insider = member("SOCIAL_MANAGER", mine)
        val linked = link("stakeholder", holder, "engagement_stakeholder", foreign, insider.token)
        assertTrue(linked.status.is2xxSuccessful, linked.body)

        // asignado solo al destino: sacar un registro del proyecto de origen exige asignación a él
        val ownProject = member("COMMUNITY_RELATIONS_OFFICER", other)
        assertNotAssigned(edit("engagement", foreign, mapOf("project" to other), ownProject.token), "project")
    }

    // un padre con dependientes no se borra (DependentsDeleteGuard, P13): wasichai declara las relaciones obligatorias NOT NULL
    // con ON DELETE SET NULL, así que el borrado fallaría en la base. por eso no hay registros huérfanos por la API en los
    // objetos del alcance, cuyas relaciones de alcance son obligatorias (las opcionales, export_log.project y
    // locality_profile.study, quedan del tenant a propósito, security.md §9.2). la guarda cerrada ante un registro sin dueño
    // es defensa en profundidad y la cubre ProjectResolverTest, sin base
    @Test
    fun `un plan con actividades no se borra, ni siquiera el administrador, y sin actividades si`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val owner = member("SOCIAL_MANAGER", mine)
        val outsider = member("SOCIAL_MANAGER", other)
        val plan = fixture("engagement_plan", mapOf("project" to mine)).id
        val activity = fixture("planned_activity", mapOf("engagement_plan" to plan)).id

        for (caller in listOf(owner.token, token)) {
            val refused = delete("engagement_plan", plan, caller)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            val error = tree(refused.body)["errors"].values().single()
            assertEquals("HAS_DEPENDENTS", error["message"].asString(), refused.body)
            assertEquals("planned_activity.engagement_plan", error["field"].asString(), refused.body)
        }
        // el alcance habla primero: quien no es del equipo recibe 403, sin enterarse de los dependientes
        assertNotAssigned(delete("engagement_plan", plan, outsider.token), "project")
        // ambos registros siguen, y la actividad sigue ligada al plan
        assertEquals(plan, record("planned_activity", activity)["attributes"]["engagement_plan"].asString())
        assertEquals(mine, record("engagement_plan", plan)["attributes"]["project"].asString())

        assertEquals(HttpStatus.NO_CONTENT, delete("planned_activity", activity, owner.token).status)
        assertEquals(HttpStatus.NO_CONTENT, delete("engagement_plan", plan, owner.token).status)
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
