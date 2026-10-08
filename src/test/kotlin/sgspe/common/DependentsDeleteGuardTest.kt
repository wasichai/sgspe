package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// las relaciones que DependentsDeleteGuard vigila salen del catálogo: MANY_TO_ONE, obligatorias y con ese objeto de destino
class DependentsDeleteGuardTest {
    private val catalog =
        ModelCatalog.parse(
            model =
                """
                {"objects": [], "relationships": [
                  {"name": "a", "source": "planned_activity", "target": "engagement_plan", "fieldName": "engagement_plan", "required": true},
                  {"name": "b", "source": "engagement_participant", "target": "engagement", "fieldName": "engagement", "required": true},
                  {"name": "c", "source": "engagement_question", "target": "engagement", "fieldName": "engagement", "required": true},
                  {"name": "d", "source": "locality_profile", "target": "study", "fieldName": "study"},
                  {"name": "e", "source": "engagement", "target": "stakeholder", "type": "MANY_TO_MANY", "required": true}
                ]}
                """.trimIndent(),
            roles = """{"roles": []}"""
        )

    @Test
    fun `solo cuentan las relaciones obligatorias MANY_TO_ONE hacia el objeto`() {
        assertEquals(listOf("planned_activity" to "engagement_plan"), catalog.requiredDependents("engagement_plan"))
        assertEquals(
            listOf("engagement_participant" to "engagement", "engagement_question" to "engagement"),
            catalog.requiredDependents("engagement")
        )
        // una relación opcional se pone en NULL sin romper nada, y una MANY_TO_MANY vive en su tabla de unión
        assertTrue(catalog.requiredDependents("study").isEmpty())
        assertTrue(catalog.requiredDependents("stakeholder").isEmpty())
        assertTrue(catalog.requiredDependents("no_existe").isEmpty())
    }

    @Test
    fun `el modelo real vigila a los padres de los hijos obligatorios`() {
        val real = ModelCatalog.fromClasspath()
        assertTrue(("planned_activity" to "engagement_plan") in real.requiredDependents("engagement_plan"))
        assertTrue(("engagement_participant" to "engagement") in real.requiredDependents("engagement"))
        assertTrue(real.requiredDependents("project").any { it.first == "stakeholder" })
        assertTrue(real.requiredDependents("study").none { it.first == "locality_profile" })
    }
}
