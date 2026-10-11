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
    fun `un hijo se lee por el proyecto de su padre y, con un unico padre opcional, tambien sin padre`() {
        val path = ParentPath("\"study\"", listOf(Hop("\"data\".\"study_tbl\"", "\"project\"")))
        val child = "\"study\" IN (SELECT t1.\"id\" FROM \"data\".\"study_tbl\" t1 WHERE t1.organization_id = :c0 AND t1.\"project\" IN (${mine(1)}))"
        val (text, bound) = condition(ReadGroup.CHILD, ReadColumns(null, assignments, parents = listOf(path)))
        assertEquals(child, text)
        assertEquals(listOf<Any>(organization, organization, user, today, today), bound)
        assertEquals(
            "\"study\" IS NULL OR $child",
            condition(ReadGroup.CHILD, ReadColumns(null, assignments, optional = true, parents = listOf(path))).first
        )
    }

    // ASM-114: un nieto (program_measurement → program_indicator → social_program) anida una subconsulta por salto, cada
    // una con su alias, y la organización va enlazada en cada salto
    @Test
    fun `un nieto se lee por su cadena, una subconsulta por salto`() {
        val path =
            ParentPath(
                "\"program_indicator\"",
                listOf(Hop("\"data\".\"pi_tbl\"", "\"social_program\""), Hop("\"data\".\"sp_tbl\"", "\"project\""))
            )
        val (text, bound) = condition(ReadGroup.CHILD, ReadColumns(null, assignments, parents = listOf(path)))
        assertEquals(
            "\"program_indicator\" IN (SELECT t1.\"id\" FROM \"data\".\"pi_tbl\" t1 WHERE t1.organization_id = :c0 AND " +
                "t1.\"social_program\" IN (SELECT t2.\"id\" FROM \"data\".\"sp_tbl\" t2 WHERE t2.organization_id = :c1 AND " +
                "t2.\"project\" IN (${mine(2)})))",
            text
        )
        assertEquals(listOf<Any>(organization, organization, organization, user, today, today), bound)
    }

    // ASM-114: un hijo con dos padres (delivery) es la disyunción, cada término entre paréntesis; un NULL no cumple ningún
    // término, así que la entrega huérfana no la lee nadie (salvo ADMIN y READ_ALL_PROJECTS, que no pasan por aquí)
    @Test
    fun `un hijo con dos padres se lee por el que tenga, y sin ninguno no lo lee nadie`() {
        val commitment = ParentPath("\"commitment\"", listOf(Hop("\"data\".\"commitment_tbl\"", "\"project\"")))
        val contribution = ParentPath("\"social_contribution\"", listOf(Hop("\"data\".\"sc_tbl\"", "\"project\"")))
        val (text, _) = condition(ReadGroup.CHILD, ReadColumns(null, assignments, parents = listOf(commitment, contribution)))
        assertEquals(
            "(\"commitment\" IN (SELECT t1.\"id\" FROM \"data\".\"commitment_tbl\" t1 WHERE t1.organization_id = :c0 AND " +
                "t1.\"project\" IN (${mine(1)}))) OR (\"social_contribution\" IN (SELECT t1.\"id\" FROM \"data\".\"sc_tbl\" t1 " +
                "WHERE t1.organization_id = :c5 AND t1.\"project\" IN (${mine(6)})))",
            text
        )
        // ningún padre admite el vacío («mis proyectos» sí lleva sus propios IS NULL de vigencia)
        assertEquals(false, text!!.contains("\"commitment\" IS NULL") || text.contains("\"social_contribution\" IS NULL"))
        // con varios padres, la forma «sin padre, del tenant» no vale: falla cerrada
        assertEquals("false", condition(ReadGroup.CHILD, ReadColumns(null, assignments, optional = true, parents = listOf(commitment, contribution))).first)
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
        assertEquals("false", condition(ReadGroup.CHILD, ReadColumns(null, assignments)).first)
        // a un salto de la cadena le falta su columna, o al hijo la del padre
        val broken = ParentPath("\"program_indicator\"", listOf(Hop("\"data\".\"pi_tbl\"", null), Hop("\"data\".\"sp_tbl\"", "\"project\"")))
        assertEquals("false", condition(ReadGroup.CHILD, ReadColumns(null, assignments, parents = listOf(broken))).first)
        val noColumn = ParentPath(null, listOf(Hop("\"data\".\"study_tbl\"", "\"project\"")))
        assertEquals("false", condition(ReadGroup.CHILD, ReadColumns(null, assignments, parents = listOf(noColumn))).first)
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
