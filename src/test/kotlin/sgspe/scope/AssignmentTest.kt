package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// la vigencia de una asignación (security.md §9.2): active y, si tiene fechas, hoy entre ellas
class AssignmentTest {
    private val project = UUID.randomUUID().toString()
    private val user = UUID.randomUUID().toString()

    private fun assignment(
        active: Any?,
        start: String? = null,
        end: String? = null
    ) = Assignment.of(
        UUID.randomUUID().toString(),
        mapOf(
            "project" to project,
            "member_user_id" to user,
            "member_user_email" to "a@sgspe.test",
            "role_name" to "FIELD_AGENT",
            "active" to active,
            "start_date" to start,
            "end_date" to end
        )
    )!!

    @Test
    fun `vigente con active y dentro de sus fechas, incluidos los extremos`() {
        val today = LocalDate.parse("2026-05-12")
        assertTrue(assignment(true).inForce(today))
        assertTrue(assignment(true, "2026-05-12", "2026-05-12").inForce(today))
        assertFalse(assignment(false).inForce(today))
        assertFalse(assignment(true, "2026-05-13").inForce(today))
        assertFalse(assignment(true, null, "2026-05-11").inForce(today))
    }

    @Test
    fun `una fila sin proyecto o sin usuario no es una asignacion`() {
        assertNull(Assignment.of(UUID.randomUUID().toString(), mapOf("member_user_id" to user)))
        assertEquals("FIELD_AGENT", assignment(true).roleName)
    }

    @Test
    fun `el alcance cubre raices, hijos y el proyecto, y exime las asignaciones`() {
        assertTrue(ProjectScope.covers("project"))
        assertTrue(ProjectScope.covers("engagement"))
        assertTrue(ProjectScope.covers("engagement_participant"))
        assertFalse(ProjectScope.covers("locality"))
        assertFalse(ProjectScope.covers("person"))
        assertEquals("engagement" to "engagement", ProjectScope.CHILDREN["engagement_question"])
        assertTrue("project_assignment" in ProjectScope.EXEMPT)
    }
}
