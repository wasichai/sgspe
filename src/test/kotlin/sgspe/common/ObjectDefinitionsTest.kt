package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.ObjectDefinition
import java.time.Duration
import java.util.UUID

// ObjectDefinitions (plan corto de la caché del esquema): current carga una vez por petición de /api; recent guarda además
// entre peticiones a lo sumo `ttl`, por organización y objeto, nunca una ausencia, con un tope de entradas. el reloj es un
// contador propio de la prueba (la caché no usa el Clock de sgspe, que las pruebas de integración fijan)
class ObjectDefinitionsTest {
    private val organization = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private var now = 0L
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

    private fun definitions(
        ttl: Duration = Duration.ofSeconds(30),
        maxEntries: Int = 100
    ) = ObjectDefinitions(::load, ttl, maxEntries) { now }

    private fun advance(seconds: Long) {
        now += Duration.ofSeconds(seconds).toNanos()
    }

    @Test
    fun `current carga una vez por peticion, organizacion y objeto, y fuera de una peticion cada vez`() {
        val definitions = definitions()
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
    fun `recent guarda entre peticiones hasta ttl y despues vuelve a cargar`() {
        val definitions = definitions()
        runBlocking {
            val first = RequestMemo.forRequest { definitions.recent(organization, "commitment") }
            advance(29)
            val second = RequestMemo.forRequest { definitions.recent(organization, "commitment") }
            assertSame(first, second)
            advance(1)
            val third = RequestMemo.forRequest { definitions.recent(organization, "commitment") }
            assertNotSame(first, third)
        }
        assertEquals(2, asked.size)
        assertEquals(2L, definitions.misses())
    }

    @Test
    fun `una ausencia no se guarda - el objeto que aparece se ve en la peticion siguiente`() {
        absent += "social_contribution"
        val definitions = definitions()
        assertThrows(NotFoundException::class.java) { runBlocking { definitions.recent(organization, "social_contribution") } }
        absent.clear()
        assertEquals("social_contribution", runBlocking { definitions.recent(organization, "social_contribution") }.obj.name)
        assertEquals(2, asked.size)
    }

    @Test
    fun `dos organizaciones no comparten definiciones`() {
        val definitions = definitions()
        val mine = runBlocking { definitions.recent(organization, "project") }
        val theirs = runBlocking { definitions.recent(other, "project") }
        assertEquals(organization, mine.obj.organizationId)
        assertEquals(other, theirs.obj.organizationId)
        assertEquals(listOf(organization to "project", other to "project"), asked)
    }

    @Test
    fun `con ttl cero no guarda entre peticiones, pero si dentro de una`() {
        val definitions = definitions(ttl = Duration.ZERO)
        runBlocking {
            RequestMemo.forRequest { repeat(2) { definitions.recent(organization, "project") } }
            RequestMemo.forRequest { definitions.recent(organization, "project") }
        }
        assertEquals(2, asked.size)
        assertEquals(0, definitions.size)
    }

    @Test
    fun `el tope de entradas se respeta y la ultima entrada queda`() {
        val definitions = definitions(maxEntries = 3)
        val names = listOf("project", "commitment", "social_contribution", "program_indicator", "social_program")
        runBlocking { names.forEach { definitions.recent(organization, it) } }
        assertTrue(definitions.size <= 3, "entradas: ${definitions.size}")
        runBlocking { definitions.recent(organization, names.last()) }
        assertEquals(names.size, asked.size)
    }

    @Test
    fun `un ttl negativo o un tope sin entradas no arrancan`() {
        assertThrows(IllegalArgumentException::class.java) { definitions(ttl = Duration.ofSeconds(-1)) }
        assertThrows(IllegalArgumentException::class.java) { definitions(maxEntries = 0) }
    }
}
