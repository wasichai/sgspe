package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// las guardas transversales de sgspe.common por la API genérica, con los objetos de F2: derivados (domain-model §4.6),
// «exactamente uno de» (§4.4) y pares de usuario (ASM-023)
class CrossGuardsApiTest : DomainApiTest() {
    @Test
    fun `un derivado enviado al crear es un 400 que lo nombra y nada se guarda`() {
        val project = projectWithCrs()
        val organization = organization()
        val response =
            exchange(
                "POST",
                "/api/objects/stakeholder/records",
                mapOf("attributes" to mapOf("project" to project, "organization" to organization, "interest_index" to 4.5))
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("interest_index"), problemFields(response.body))
        assertEquals(0, tree(send("GET", "/api/objects/stakeholder/records?organization=$organization", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `el portal reenvia los derivados tal cual y pasa, cambiarlos es un 400`() {
        val stakeholder = stakeholder(projectWithCrs())
        assertEquals(HttpStatus.OK, edit("stakeholder", stakeholder, mapOf("profile_summary" to "Ronda de un caserío")).status)
        val changed = edit("stakeholder", stakeholder, mapOf("influence_group" to "ESTRATEGICO"))
        assertEquals(HttpStatus.BAD_REQUEST, changed.status, changed.body)
        assertEquals(listOf("influence_group"), problemFields(changed.body))
    }

    @Test
    fun `vincular no es escribir un derivado`() {
        val project = projectWithCrs()
        val engagement = engagement(project)
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", engagement, "engagement_stakeholder", stakeholder(project)).status)
    }

    @Test
    fun `exactamente uno de persona u organizacion, y de los extremos de una relacion entre actores`() {
        val project = projectWithCrs()
        val both =
            exchange(
                "POST",
                "/api/objects/stakeholder/records",
                mapOf("attributes" to mapOf("project" to project, "person" to person(), "organization" to organization()))
            )
        assertEquals(HttpStatus.BAD_REQUEST, both.status)
        assertEquals(listOf("person"), problemFields(both.body))
        val none =
            exchange("POST", "/api/objects/contact_method/records", mapOf("attributes" to mapOf("channel" to "TELEFONO", "contact_value" to "000000000")))
        assertEquals(HttpStatus.BAD_REQUEST, none.status)
        val relation =
            exchange(
                "POST",
                "/api/objects/actor_relationship/records",
                mapOf(
                    "attributes" to
                        mapOf("relationship_type" to "APOYA", "from_person" to person(), "to_person" to person(), "to_organization" to organization())
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, relation.status)
        assertEquals(listOf("to_person"), problemFields(relation.body))
    }

    @Test
    fun `un par de usuario informado debe ser un usuario de la organizacion con su correo`() {
        val project = projectWithCrs()
        val someone = userWithRole("ANALYST")
        val wrongEmail =
            exchange(
                "POST",
                "/api/objects/project_assignment/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to project,
                            "member_user_id" to someone.id,
                            "member_user_email" to "otra@sgspe.test",
                            "role_name" to "ANALYST",
                            "active" to true
                        )
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, wrongEmail.status)
        assertEquals(listOf("member_user_id"), problemFields(wrongEmail.body))
        val half =
            exchange(
                "POST",
                "/api/objects/stakeholder/records",
                mapOf("attributes" to mapOf("project" to project, "organization" to organization(), "owner_user_id" to someone.id))
            )
        assertEquals(HttpStatus.BAD_REQUEST, half.status)
        assertTrue("owner_user_email" in problemFields(half.body))
        assign(someone, project, "ANALYST")
    }
}
