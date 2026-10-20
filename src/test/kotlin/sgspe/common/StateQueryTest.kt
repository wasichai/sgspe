package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// ?state= y ?state_not= sobre la lista genérica (D10 de la revisión de UX): los estados van ligados, nunca escritos en el SQL,
// y se validan contra el workflow del modelo
class StateQueryTest {
    private val query = StateQuery(ModelCatalog.fromClasspath())

    private fun definition(name: String) =
        ObjectDefinition(CustomObject(UUID.randomUUID(), UUID.randomUUID(), name, name, name, null, true, "${name}_tbl", null, null), emptyList())

    // la condición y sus valores ligados, en orden
    private fun condition(
        params: Map<String, String>,
        objectName: String = "commitment"
    ): Pair<String, List<List<Any?>>> {
        val bound = mutableListOf<List<Any?>>()
        val sql =
            query.parse(params)!!.condition(definition(objectName)) { value ->
                bound += (value as Array<*>).toList()
                ":c${bound.size - 1}"
            }
        return sql to bound
    }

    @Test
    fun `sin state ni state_not no agrega nada, y un valor vacio tampoco`() {
        assertNull(query.parse(mapOf("project" to "p1")))
        assertNull(query.parse(mapOf("state" to "", "state_not" to " , ")))
        assertEquals(setOf("state", "state_not"), query.parameters)
    }

    @Test
    fun `state admite varios estados separados por comas`() {
        val (sql, bound) = condition(mapOf("state" to "blocked, in_progress,blocked"))
        assertEquals("workflow_state = ANY(:c0)", sql)
        assertEquals(listOf(listOf("blocked", "in_progress")), bound)
    }

    @Test
    fun `state_not deja fuera esos estados y conserva los registros sin estado`() {
        val (sql, bound) = condition(mapOf("state_not" to "closed,cancelled"))
        assertEquals("(workflow_state IS NULL OR workflow_state <> ALL(:c0))", sql)
        assertEquals(listOf(listOf("closed", "cancelled")), bound)
    }

    @Test
    fun `los dos juntos se suman`() {
        val (sql, bound) = condition(mapOf("state" to "blocked", "state_not" to "closed"))
        assertEquals("workflow_state = ANY(:c0) AND (workflow_state IS NULL OR workflow_state <> ALL(:c1))", sql)
        assertEquals(listOf(listOf("blocked"), listOf("closed")), bound)
    }

    @Test
    fun `un estado que el workflow no tiene es 400 sobre su parametro`() {
        val error = assertThrows<ValidationException> { condition(mapOf("state_not" to "closed,'; drop table x")) }
        assertEquals("state_not", error.violations.single().field)
    }

    @Test
    fun `un objeto sin workflow es 400, porque no tiene la columna de estado`() {
        val error = assertThrows<ValidationException> { condition(mapOf("state" to "draft"), objectName = "locality") }
        assertEquals("state", error.violations.single().field)
    }
}
