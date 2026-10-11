package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// Permissions.has toma el id del objeto de la definición de la petición: dos preguntas sobre el mismo objeto cargan su
// definición una vez
class PermissionsTest {
    private val organization = UUID.randomUUID()
    private val user = AuthenticatedUser(UUID.randomUUID(), organization, "persona@sgspe.test", listOf("ANALYST"), null)
    private val ids = mutableMapOf<String, UUID>()
    private val loaded = mutableListOf<String>()

    private suspend fun load(
        organization: UUID,
        name: String
    ): ObjectDefinition {
        loaded += name
        val id = ids.getOrPut(name) { UUID.randomUUID() }
        return ObjectDefinition(CustomObject(id, organization, name, name, name, null, true, "${name}_tbl", null, null), emptyList())
    }

    // no lanza para «allowed» y lanza ForbiddenException para cualquier otro objeto
    private fun currentUser(allowed: String): CurrentUser {
        val allowedId = ids.getOrPut(allowed) { UUID.randomUUID() }
        return mock(CurrentUser::class.java) { call ->
            if (call.arguments[2] != allowedId) throw ForbiddenException("sin permiso")
            Unit
        }
    }

    @Test
    fun `dos preguntas sobre el mismo objeto cargan su definicion una vez`() {
        val permissions = Permissions(currentUser("project"), ObjectDefinitions(::load))
        runBlocking {
            RequestMemo.forRequest {
                assertTrue(permissions.has(user, Actions.READ, "project"))
                assertTrue(permissions.has(user, Actions.READ, "project"))
                assertFalse(permissions.has(user, Actions.READ, "stakeholder"))
                assertFalse(permissions.has(user, Actions.READ, "stakeholder"))
            }
        }
        assertEquals(listOf("project", "stakeholder"), loaded)
    }
}
