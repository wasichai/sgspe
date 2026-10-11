package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// ObjectDefinitions (plan corto de wasichai 1.1.0): un memo por petición sobre MetadataService.loadDefinition, que desde
// wasichai 1.1.0 guarda las definiciones entre peticiones. nunca guarda una ausencia, y dos organizaciones no comparten nada
class ObjectDefinitionsTest {
    private val organization = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val asked = mutableListOf<Pair<UUID, String>>()
    private val absent = mutableSetOf<String>()

    private fun definition(
        organizationId: UUID,
        name: String
    ) = ObjectDefinition(
        CustomObject(UUID.randomUUID(), organizationId, name, name, name, null, true, "${name}_tbl", null, null),
        emptyList()
    )

    private suspend fun load(
        organizationId: UUID,
        name: String
    ): ObjectDefinition {
        asked += organizationId to name
        if (name in absent) throw NotFoundException("Object '$name' does not exist")
        return definition(organizationId, name)
    }

    private val definitions = ObjectDefinitions(::load)

    @Test
    fun `current carga una vez por peticion, organizacion y objeto, y fuera de una peticion cada vez`() {
        runBlocking {
            RequestMemo.forRequest {
                repeat(3) { definitions.current(organization, "project") }
                definitions.current(other, "project")
            }
            RequestMemo.forRequest { definitions.current(organization, "project") }
        }
        assertEquals(listOf(organization to "project", other to "project", organization to "project"), asked)
        runBlocking { repeat(2) { definitions.current(organization, "project") } }
        assertEquals(5, asked.size)
    }

    @Test
    fun `una peticion ve una sola version de cada definicion`() {
        runBlocking {
            RequestMemo.forRequest {
                assertSame(definitions.current(organization, "project"), definitions.current(organization, "project"))
            }
        }
    }

    @Test
    fun `una ausencia no se guarda - el objeto que aparece se ve en la peticion siguiente`() {
        absent += "social_contribution"
        assertThrows(NotFoundException::class.java) {
            runBlocking { RequestMemo.forRequest { definitions.current(organization, "social_contribution") } }
        }
        absent.clear()
        val found = runBlocking { RequestMemo.forRequest { definitions.current(organization, "social_contribution") } }
        assertEquals("social_contribution", found.obj.name)
        assertEquals(2, asked.size)
    }

    @Test
    fun `dos organizaciones no comparten definiciones`() {
        val (mine, theirs) =
            runBlocking {
                RequestMemo.forRequest { definitions.current(organization, "project") to definitions.current(other, "project") }
            }
        assertEquals(organization, mine.obj.organizationId)
        assertEquals(other, theirs.obj.organizationId)
        assertEquals(listOf(organization to "project", other to "project"), asked)
    }
}
