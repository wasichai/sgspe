package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardReadabilityTest {
    @Test
    fun `un objeto ilegible se nombra sin sus campos y sin repetirse`() {
        val paths = listOf("community.territory", "community", "locality.boundary", "community", "administrative_unit")
        assertEquals(listOf("community", "locality.boundary", "administrative_unit"), CardReadability.collapse(paths))
    }

    @Test
    fun `las comunidades piden el territorio y el limite o el punto`() {
        assertTrue(CardReadability.communitiesComputable(boundary = false, location = true, territory = true))
        assertFalse(CardReadability.communitiesComputable(boundary = false, location = false, territory = true))
        assertFalse(CardReadability.communitiesComputable(boundary = true, location = true, territory = false))
    }
}
