package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog

// la regla «mismo proyecto» de la sugerencia de IA (plan 7.2b): su ai_interaction es de su proyecto o del tenant
class ProjectLinkF7Test {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `la sugerencia de IA se juzga por su ai_interaction`() {
        assertTrue("ai_suggestion" in ProjectLinkGuard.OBJECTS)
        assertEquals(listOf("ai_interaction" to "ai_interaction"), ProjectLinks.fieldsOf(catalog, "ai_suggestion"))
        assertTrue("ai_interaction" in ProjectLinks.tenantCapable(catalog))
    }
}
