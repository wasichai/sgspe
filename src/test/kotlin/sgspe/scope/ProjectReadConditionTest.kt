package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// la condición de lectura por proyecto por grupo (security.md §9.7, ASM-106), sin base: qué lee cada quien, que los valores
// van siempre enlazados y que falla cerrada sin una columna o con un objeto sin clasificar
class ProjectReadConditionTest {
    private val organization = UUID.fromString("00000000-0000-0000-0000-0000000000a0")
    private val user = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val today = LocalDate.parse("2026-10-14")
    private val assignments =
        AssignmentColumns("\"data\".\"pa_tbl\"", "\"project\"", "\"member_user_id\"", "\"active\"", "\"start_date\"", "\"end_date\"")

    // «mis proyectos» con sus parámetros a partir de :c<first>
    private fun mine(first: Int): String =
        "SELECT a.\"project\" FROM \"data\".\"pa_tbl\" a WHERE a.organization_id = :c$first AND a.\"member_user_id\" = :c${first + 1} " +
            "AND a.\"active\" IS TRUE AND (a.\"start_date\" IS NULL OR a.\"start_date\" <= :c${first + 2}) " +
            "AND (a.\"end_date\" IS NULL OR a.\"end_date\" >= :c${first + 3})"

    private fun condition(
        group: ReadGroup,
        columns: ReadColumns
    ): Pair<String?, List<Any>> {
        val bound = mutableListOf<Any>()
        val text =
            ProjectReadCondition.condition(group, columns, organization, user, today) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        return text to bound
    }

    @Test
    fun `un proyecto se lee si esta entre mis proyectos`() {
        val (text, bound) = condition(ReadGroup.PROJECT, ReadColumns(ProjectReadCondition.ID_COLUMN, assignments))
        assertEquals("\"id\" IN (${mine(0)})", text)
        assertEquals(listOf<Any>(organization, user, today, today), bound)
    }

    @Test
    fun `cada uno ve sus asignaciones, aun vencidas, y las de sus equipos`() {
        val (text, bound) = condition(ReadGroup.ASSIGNMENT, ReadColumns("\"project\"", assignments, member = "\"member_user_id\""))
        assertEquals("\"member_user_id\" = :c0 OR \"project\" IN (${mine(1)})", text)
        assertEquals(listOf<Any>(user, organization, user, today, today), bound)
    }

    @Test
    fun `una raiz se lee por su proyecto y, si es opcional, tambien sin proyecto`() {
        assertEquals("\"project\" IN (${mine(0)})", condition(ReadGroup.ROOT, ReadColumns("\"project\"", assignments)).first)
        assertEquals(
            "\"project\" IS NULL OR \"project\" IN (${mine(0)})",
            condition(ReadGroup.ROOT, ReadColumns("\"project\"", assignments, optional = true)).first
        )
    }

    @Test
    fun `un hijo se lee por el proyecto de su padre y, con padre opcional, tambien sin padre`() {
        val parent = ParentColumns("\"data\".\"commitment_tbl\"", "\"project\"")
        val child = "\"commitment\" IN (SELECT t.\"id\" FROM \"data\".\"commitment_tbl\" t WHERE t.organization_id = :c0 AND t.\"project\" IN (${mine(1)}))"
        val (text, bound) = condition(ReadGroup.CHILD, ReadColumns("\"commitment\"", assignments, parent = parent))
        assertEquals(child, text)
        assertEquals(listOf<Any>(organization, organization, user, today, today), bound)
        assertEquals(
            "\"commitment\" IS NULL OR $child",
            condition(ReadGroup.CHILD, ReadColumns("\"commitment\"", assignments, optional = true, parent = parent)).first
        )
    }

    @Test
    fun `un maestro del tenant no se restringe`() {
        assertNull(condition(ReadGroup.TENANT, ReadColumns(null, null)).first)
    }

    @Test
    fun `sin clasificar o sin una columna no admite ningun registro`() {
        assertEquals("false", condition(ReadGroup.UNCLASSIFIED, ReadColumns("\"project\"", assignments)).first)
        assertEquals("false", condition(ReadGroup.ROOT, ReadColumns(null, assignments)).first)
        assertEquals("false", condition(ReadGroup.ROOT, ReadColumns("\"project\"", null)).first)
        assertEquals("false", condition(ReadGroup.ASSIGNMENT, ReadColumns("\"project\"", assignments)).first)
        assertEquals("false", condition(ReadGroup.CHILD, ReadColumns("\"commitment\"", assignments)).first)
        assertEquals("false", condition(ReadGroup.PROJECT, ReadColumns(ProjectReadCondition.ID_COLUMN, null)).first)
    }

    @Test
    fun `ningun valor va en el texto`() {
        val (text, _) = condition(ReadGroup.ROOT, ReadColumns("\"project\"", assignments))
        for (value in listOf(organization.toString(), user.toString(), today.toString())) {
            assertEquals(false, text!!.contains(value), value)
        }
    }
}
