package sgspe.common

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// los roles de las transiciones de comando salen de model/workflows (ModelCatalog.transition): un endpoint que aplica
// varias transiciones las comprueba todas antes de escribir
class TransitionRolesTest {
    private val catalog =
        ModelCatalog.parse(
            model = """{"enums": {}, "objects": [{"name": "area_of_influence", "fields": []}], "relationships": []}""",
            roles = """{"mappedToAdmin": ["SUPER_ADMIN"], "roles": [{"name": "SOCIAL_MANAGER"}, {"name": "GIS_ANALYST"}]}""",
            workflows =
                mapOf(
                    "area_of_influence" to
                        """
                        {"definition": {"transitions": [
                          {"name": "approve", "from": "draft", "to": "approved", "roles": ["SOCIAL_MANAGER"]},
                          {"name": "supersede", "from": "approved", "to": "superseded", "roles": ["SOCIAL_MANAGER"]}]}}
                        """.trimIndent()
                )
        )

    private fun user(vararg roles: String) = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", roles.toList(), null)

    @Test
    fun `quien tiene el rol de cada transicion pasa, y el admin tambien`() {
        assertDoesNotThrow { TransitionRoles.require(catalog, user("SOCIAL_MANAGER"), "area_of_influence", listOf("approve", "supersede")) }
        assertDoesNotThrow { TransitionRoles.require(catalog, user(AuthenticatedUser.ADMIN_ROLE), "area_of_influence", listOf("approve", "supersede")) }
    }

    @Test
    fun `sin el rol de alguna transicion es 403 ROLE_REQUIRED que nombra la transicion y los roles`() {
        val refused =
            assertThrows<RuleException> {
                TransitionRoles.require(catalog, user("GIS_ANALYST"), "area_of_influence", listOf("approve", "supersede"))
            }
        assertEquals(HttpStatus.FORBIDDEN, refused.status)
        assertEquals("ROLE_REQUIRED", refused.violations.single().message)
        assertEquals("La transición approve exige uno de los roles SOCIAL_MANAGER", refused.message)
    }

    @Test
    fun `una transicion que el workflow no tiene no la puede aplicar nadie salvo el admin`() {
        val refused = assertThrows<RuleException> { TransitionRoles.require(catalog, user("SOCIAL_MANAGER"), "area_of_influence", listOf("archive")) }
        assertEquals(HttpStatus.FORBIDDEN, refused.status)
    }
}
