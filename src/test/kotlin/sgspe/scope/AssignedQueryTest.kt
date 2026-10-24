package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// ?assigned=true sobre la lista genérica (B-04, wasichai/sgspe#162): deja los registros de los proyectos con asignación vigente
// de quien llama, que AssignedProjectsFilter resuelve con ProjectReach.assigned antes del controlador. sin proyectos (o fuera de
// una petición que los resolvió) no admite ninguno; un objeto sin `project` y un valor que no es true ni false son 400
class AssignedQueryTest {
    private val query = AssignedQuery()

    private fun field(name: String) =
        CustomField(
            UUID.randomUUID(),
            UUID.randomUUID(),
            name,
            name,
            FieldType.TEXT,
            "c_$name",
            false,
            false,
            null,
            null,
            0,
            null,
            null,
            emptyMap(),
            true,
            true
        )

    private fun definition(
        name: String,
        vararg fields: String
    ) = ObjectDefinition(
        CustomObject(UUID.randomUUID(), UUID.randomUUID(), name, name, name, null, true, "${name}_tbl", null, null),
        fields.map { field(it) }
    )

    private fun condition(
        criterion: RecordCriterion,
        definition: ObjectDefinition
    ): Pair<String, List<Any>> {
        val bound = mutableListOf<Any>()
        val sql =
            criterion.condition(definition) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        return sql to bound
    }

    private fun parsed(
        projects: List<UUID>?,
        params: Map<String, String>
    ): RecordCriterion? = runBlocking(AssignedProjects.element(projects)) { query.parse(params) }

    @Test
    fun `sin assigned o con false no agrega nada`() {
        assertEquals(setOf("assigned"), query.parameters)
        assertNull(query.parse(mapOf("project" to "p1")))
        assertNull(parsed(listOf(UUID.randomUUID()), mapOf("assigned" to "false")))
        assertNull(parsed(listOf(UUID.randomUUID()), mapOf("assigned" to " FALSE ")))
    }

    @Test
    fun `con assigned deja los registros de los proyectos asignados, ligados`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val criterion = parsed(listOf(a, b), mapOf("assigned" to "true"))!!
        val (sql, bound) = condition(criterion, definition("task", "project", "title"))
        assertEquals("\"c_project\" IN (:c0, :c1)", sql)
        assertEquals(listOf<Any>(a, b), bound)
        // en project, el proyecto mismo
        assertEquals("\"id\" IN (:c0, :c1)" to listOf<Any>(a, b), condition(criterion, definition("project", "code")))
    }

    @Test
    fun `sin asignaciones vigentes, o sin la peticion que las resuelve, no admite ninguno`() {
        assertEquals("false" to emptyList<Any>(), condition(parsed(emptyList(), mapOf("assigned" to "true"))!!, definition("task", "project")))
        assertEquals("false" to emptyList<Any>(), condition(parsed(null, mapOf("assigned" to "true"))!!, definition("task", "project")))
        assertEquals("false" to emptyList<Any>(), condition(query.parse(mapOf("assigned" to "true"))!!, definition("task", "project")))
    }

    @Test
    fun `un objeto sin project y un valor que no es true ni false son de validacion`() {
        val criterion = parsed(listOf(UUID.randomUUID()), mapOf("assigned" to "true"))!!
        val sinProyecto = assertThrows<ValidationException> { condition(criterion, definition("topic", "name")) }
        assertEquals("assigned", sinProyecto.violations.single().field)
        val valor = assertThrows<ValidationException> { parsed(emptyList(), mapOf("assigned" to "si")) }
        assertEquals("assigned", valor.violations.single().field)
    }
}
