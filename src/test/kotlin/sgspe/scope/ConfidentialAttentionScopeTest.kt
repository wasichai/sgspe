package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

// la condición de las QCS confidenciales por caso (security.md §9.6), sin base: qué lee cada quien y que falla cerrada
class ConfidentialAttentionScopeTest {
    private val user = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val columns =
        mapOf(
            "confidential" to "\"confidential\"",
            "owner_user_id" to "\"owner_user_id\"",
            "received_by_user_id" to "\"received_by_user_id\"",
            "severity" to "\"severity\""
        )

    private fun condition(
        objectName: String,
        readConfidential: Boolean = false,
        approve: Boolean = false,
        columns: Map<String, String?> = this.columns
    ): Pair<String?, List<Any>> {
        val bound = mutableListOf<Any>()
        val text =
            ConfidentialCondition.condition(objectName, columns, user, readConfidential, approve) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        return text to bound
    }

    @Test
    fun `fuera de las QCS o con READ_CONFIDENTIAL no restringe`() {
        assertNull(condition("commitment").first)
        assertNull(condition("grievance", readConfidential = true).first)
        assertNull(condition("stakeholder_request", readConfidential = true).first)
    }

    @Test
    fun `los demas ven la no confidencial, la suya como responsable y la que recibieron`() {
        val (text, bound) = condition("inquiry")
        assertEquals("\"confidential\" IS NOT TRUE OR \"owner_user_id\" = :c0 OR \"received_by_user_id\" = :c1", text)
        assertEquals(listOf<Any>(user, user), bound)
    }

    @Test
    fun `con APPROVE una queja ve ademas las ALTA o CRITICA, y las otras QCS no`() {
        val (text, bound) = condition("grievance", approve = true)
        assertEquals(
            "\"confidential\" IS NOT TRUE OR \"owner_user_id\" = :c0 OR \"received_by_user_id\" = :c1 OR \"severity\" = ANY(:c2)",
            text
        )
        assertEquals(listOf("ALTA", "CRITICA"), (bound[2] as Array<*>).toList())
        assertEquals(2, condition("stakeholder_request", approve = true).second.size)
    }

    @Test
    fun `sin una columna no admite ningun registro`() {
        assertEquals("false", condition("grievance", columns = columns - "received_by_user_id").first)
        assertEquals("false", condition("grievance", approve = true, columns = columns + ("severity" to null)).first)
    }

    // ConfidentialRelease (decisión 4 del plan 4.2a): suma los registros cuyo campo vale el enlazado; sin la columna, nada
    @Test
    fun `una liberacion del sistema suma los registros de su campo`() {
        val bound = mutableListOf<Any>()
        val commitment = UUID.fromString("00000000-0000-0000-0000-00000000000c")
        val text =
            ConfidentialCondition.released("\"confidential\" IS NOT TRUE", "\"created_commitment\"", commitment) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        assertEquals("(\"confidential\" IS NOT TRUE) OR \"created_commitment\" = :c0", text)
        assertEquals(listOf<Any>(commitment), bound)
        assertEquals("false", ConfidentialCondition.released("false", null, commitment) { ":x" })
    }
}
