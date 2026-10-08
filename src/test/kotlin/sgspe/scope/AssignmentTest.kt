package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

    // el día de Lima cambia a las 05:00 UTC: a las 03:00 UTC del 13 todavía es el 12 (security.md §9.2)
    @Test
    fun `hoy es el dia de Lima y no el de UTC`() {
        fun today(instant: String) = Assignments.todayIn(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC))
        assertEquals(LocalDate.parse("2026-05-12"), today("2026-05-13T03:00:00Z"))
        assertEquals(LocalDate.parse("2026-05-12"), today("2026-05-13T04:59:59Z"))
        assertEquals(LocalDate.parse("2026-05-13"), today("2026-05-13T05:00:00Z"))
    }
}
