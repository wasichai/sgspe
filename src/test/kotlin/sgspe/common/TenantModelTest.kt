package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// los objetos que tiene un tenant (TenantModel): uno sin el modelo de F4 no tiene las QCS, y su definición no existe
// (NotFoundException de wasichai). MetadataService es un doble con el modelo de F3
class TenantModelTest {
    private val organization = UUID.randomUUID()

    private val f3: MetadataService =
        mock(MetadataService::class.java) { invocation ->
            if (invocation.method.name != "loadDefinition") return@mock null
            if (invocation.arguments[1] in Attention.OBJECTS) throw NotFoundException("sin ${invocation.arguments[1]}")
            mock(ObjectDefinition::class.java)
        }

    @Test
    fun `un tenant con el modelo de F3 no tiene las QCS y si los compromisos`() {
        val model = TenantModel(f3)
        runBlocking {
            assertFalse(model.has(organization, Attention.REQUEST))
            assertTrue(model.has(organization, "commitment"))
            assertEquals(emptyList<String>(), model.present(organization, Attention.OBJECTS))
            assertEquals(listOf("commitment"), model.present(organization, listOf("grievance", "commitment")))
        }
    }
}
