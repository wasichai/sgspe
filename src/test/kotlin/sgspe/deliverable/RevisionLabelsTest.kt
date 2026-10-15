package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// ASM-027, RQ-REP-016: borradores A, B… que vuelven a A en cada revisión nueva; emisiones 0, 1…
class RevisionLabelsTest {
    private val scheme = DeliverableProperties.Scheme()

    @Test
    fun `los borradores siguen la letra anterior y vuelven a la primera en cada revision`() {
        assertEquals("A", RevisionLabels.nextDraft(null, reset = false, scheme))
        assertEquals("B", RevisionLabels.nextDraft("A", reset = false, scheme))
        assertEquals("AA", RevisionLabels.nextDraft("Z", reset = false, scheme))
        assertEquals("AB", RevisionLabels.nextDraft("AA", reset = false, scheme))
        assertEquals("A", RevisionLabels.nextDraft("C", reset = true, scheme))
        assertEquals("A", RevisionLabels.nextDraft("1", reset = false, scheme))
    }

    @Test
    fun `las emisiones siguen a la revision vigente`() {
        assertEquals("0", RevisionLabels.nextIssue(null, scheme))
        assertEquals("1", RevisionLabels.nextIssue("0", scheme))
        assertEquals("3", RevisionLabels.nextIssue(null, DeliverableProperties.Scheme("B", 3)))
    }

    @Test
    fun `el esquema de una organizacion gana al general`() {
        val revisions = DeliverableProperties.Revisions(byOrganization = mapOf("demo" to DeliverableProperties.Scheme("B", 1)))
        assertEquals("B", revisions.of("demo").draftStart)
        assertEquals("A", revisions.of("otra").draftStart)
        assertEquals("A", revisions.of(null).draftStart)
    }
}
