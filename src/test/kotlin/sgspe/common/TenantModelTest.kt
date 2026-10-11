package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// los objetos que tiene un tenant (TenantModel): uno sin el modelo de F4 no tiene las QCS, y su definición no existe
// (NotFoundException de wasichai). ObjectDefinitions es un doble con el modelo de F3 que cuenta las preguntas
class TenantModelTest {
    private val organization = UUID.randomUUID()
    private var asked = 0

    private val f3: ObjectDefinitions =
        mock(ObjectDefinitions::class.java) { invocation ->
            if (invocation.method.name != "current") return@mock null
            asked++
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

    // la ausencia solo vive lo que la petición: el objeto que trae una subida de fase se ve en la siguiente
    @Test
    fun `dentro de una peticion cada objeto se pregunta una vez, tambien el que falta`() {
        val model = TenantModel(f3)
        runBlocking { RequestMemo.forRequest { repeat(3) { model.present(organization, Attention.OBJECTS + "commitment") } } }
        assertEquals(Attention.OBJECTS.size + 1, asked)
        runBlocking { RequestMemo.forRequest { model.has(organization, Attention.REQUEST) } }
        assertEquals(Attention.OBJECTS.size + 2, asked)
    }

    // un tenant que no tiene el modelo de F6 tiene community sin code: la importación no ofrece ese destino
    @Test
    fun `un campo se pregunta en la definicion de la organizacion`() {
        fun field(name: String) =
            CustomField(UUID.randomUUID(), UUID.randomUUID(), name, name, FieldType.TEXT, name, false, false, null, null, 0, null, null, emptyMap(), true, true)
        val f5: ObjectDefinitions =
            mock(ObjectDefinitions::class.java) { invocation ->
                if (invocation.method.name != "current") return@mock null
                ObjectDefinition(
                    CustomObject(UUID.randomUUID(), organization, "community", "community", "community", null, true, "community_tbl", null, null),
                    listOf(field("name"), field("territory"))
                )
            }
        val model = TenantModel(f5)
        runBlocking {
            assertTrue(model.hasField(organization, "community", "name"))
            assertFalse(model.hasField(organization, "community", "code"))
        }
    }
}
