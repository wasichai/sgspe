package sgspe.attention

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// api.md §4.4: se deriva una pregunta pendiente; una ya respondida (en el lugar o después) no
class ReferralsTest {
    @Test
    fun `una pregunta respondida en el lugar o despues ya no esta pendiente`() {
        assertTrue(Referrals.answered(mapOf("answered_on_site" to true)))
        assertTrue(Referrals.answered(mapOf("answered_at" to "2026-01-13T15:00:00Z")))
        assertFalse(Referrals.answered(mapOf("answered_on_site" to false, "answered_at" to "  ")))
        assertFalse(Referrals.answered(emptyMap()))
    }
}
