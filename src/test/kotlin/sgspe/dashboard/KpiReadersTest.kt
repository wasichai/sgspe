package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.NotFoundException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// especificación de F8 §2.2.16: los KPI reciben su lector. la persona lee con sus permisos, su confidentialHidden y «mis
// proyectos»; la plataforma lee todo campo que el tenant tiene, sin ocultar nada, con todos los proyectos, dentro de
// asPlatform y nunca con una persona en el contexto (Review Focus 1)
class KpiReadersTest {
    private val organization = UUID.randomUUID()
    private val person = AuthenticatedUser(UUID.randomUUID(), organization, "analista@sgspe.test", listOf("ANALYST"))
    private val mine = listOf(UUID.randomUUID())
    private val all = listOf(UUID.randomUUID(), UUID.randomUUID())
    private val platformRuns = mutableListOf<UUID>()
    private var mineCalls = 0

    private fun definition(
        name: String,
        vararg fields: String
    ): ObjectDefinition {
        val objectId = UUID.randomUUID()
        return ObjectDefinition(
            CustomObject(objectId, organization, name, name, name, null, true, "${name}_tbl", null, null),
            fields.mapIndexed { i, field ->
                CustomField(
                    id = UUID.randomUUID(),
                    objectId = objectId,
                    name = field,
                    label = field,
                    type = FieldType.TEXT,
                    columnName = "c_$field",
                    required = false,
                    unique = false,
                    defaultValue = null,
                    description = null,
                    position = i,
                    enumOptions = null,
                    relationTargetObjectId = null,
                    attributes = emptyMap(),
                    visible = true,
                    editable = true
                )
            }
        )
    }

    private fun readers(user: AuthenticatedUser?) =
        KpiReaders(
            { user },
            { user ?: error("sin usuario") },
            { org, block ->
                platformRuns += org
                block()
            },
            { { path -> path == "stakeholder" } },
            { _, name -> if (name == "stakeholder") definition("stakeholder", "stance", "influence_group") else throw NotFoundException("no está") },
            { true },
            {
                mineCalls++
                mine
            },
            { all }
        )

    @Test
    fun `la persona lee con sus permisos, su marca y sus proyectos, sin asPlatform`() {
        val readers = readers(person)
        runBlocking {
            readers.reading(readers.person()) { ctx ->
                assertTrue(ctx.readable("stakeholder"))
                assertFalse(ctx.readable("stakeholder.stance"))
                assertTrue(ctx.confidentialHidden)
                assertFalse(ctx.tenantWide)
                assertNull(ctx.platform)
                assertEquals(mine, ctx.defaultProjects())
            }
        }
        // las asignaciones se leen una sola vez por cálculo (tenantWide y los proyectos por defecto)
        assertEquals(1, mineCalls)
        assertEquals(emptyList<UUID>(), platformRuns)
    }

    @Test
    fun `la plataforma lee todo campo que el tenant tiene, sin marca y con todos los proyectos`() {
        val readers = readers(null)
        runBlocking {
            readers.reading(readers.platform(organization)) { ctx ->
                assertTrue(ctx.readable("stakeholder.stance"))
                assertFalse(ctx.readable("stakeholder.religion"))
                assertFalse(ctx.readable("deliverable.code"))
                assertFalse(ctx.confidentialHidden)
                assertTrue(ctx.tenantWide)
                assertEquals(organization, ctx.platform)
                assertEquals(all, ctx.defaultProjects())
            }
        }
        assertEquals(listOf(organization), platformRuns)
    }

    @Test
    fun `el lector de una persona solo es la persona del contexto`() {
        val other = KpiReader.Person(AuthenticatedUser(UUID.randomUUID(), organization, "otra@sgspe.test", listOf("SOCIAL_MANAGER")))
        var ran = false
        assertThrows<IllegalStateException> { runBlocking { readers(person).reading(other) { ran = true } } }
        assertThrows<IllegalStateException> { runBlocking { readers(null).reading(other) { ran = true } } }
        // el mismo userId con otros roles tampoco: el lector se compara con el usuario entero del contexto
        val promoted = KpiReader.Person(person.copy(roles = listOf("ADMIN")))
        assertThrows<IllegalStateException> { runBlocking { readers(person).reading(promoted) { ran = true } } }
        assertFalse(ran)
    }

    @Test
    fun `la plataforma no se usa con una persona en el contexto`() {
        val readers = readers(person)
        assertThrows<IllegalStateException> { runBlocking { readers.platform(organization) } }
        var ran = false
        assertThrows<IllegalStateException> { runBlocking { readers.reading(KpiReader.Platform(organization)) { ran = true } } }
        assertFalse(ran)
        assertEquals(emptyList<UUID>(), platformRuns)
    }
}
