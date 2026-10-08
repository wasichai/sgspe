package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import wasichai.core.common.ValidationException
import java.time.LocalDate

// lo que valida el endpoint de evaluaciones antes de escribir: los rangos de 1 a 5 que wasichai no tiene, los ENUM, la
// fecha no futura y que lo calculado no se manda
class AssessmentInputTest {
    private val catalog =
        ModelCatalog.parse(
            """
            {"enums": {"stance": ["A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA"],
                       "assessment_basis": ["ENTREVISTA", "ENCUESTA"],
                       "influence_group": ["ESTRATEGICO", "POLITICO", "PARA MONITOREO", "VULNERABLE"]},
             "objects": [{"name": "stakeholder_assessment", "fields": [
               {"name": "stance", "type": "ENUM", "enum": "stance"},
               {"name": "basis", "type": "ENUM", "enum": "assessment_basis"},
               {"name": "influence_group", "type": "ENUM", "enum": "influence_group"}]}]}
            """.trimIndent(),
            """{"roles": []}"""
        )
    private val today = LocalDate.parse("2026-05-20")

    private fun parse(attributes: Map<String, Any?>) = AssessmentInput.parse(mapOf("attributes" to attributes), catalog, today, requireDate = true)

    @Test
    fun `una evaluacion valida separa el ajuste manual del grupo`() {
        val input =
            parse(
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "affect_environment" to 4,
                    "power_legal_action" to 2.0,
                    "stance" to "EN CONTRA",
                    "trust_score" to 2,
                    "influence_group" to "ESTRATEGICO",
                    "override_reason" to "lidera la mesa de diálogo"
                )
            )
        assertEquals(4, input.indicators["affect_environment"])
        assertEquals(2, input.indicators["power_legal_action"])
        assertEquals("ESTRATEGICO", input.influenceGroup)
        assertFalse("influence_group" in input.attributes)
        assertEquals("lidera la mesa de diálogo", input.overrideReason)
        // como lo manda el portal (Fase 2.3): group_overridden acompaña al ajuste, y en false lo descarta
        val portal =
            parse(
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "group_overridden" to true,
                    "influence_group" to "POLITICO",
                    "override_reason" to "peso real"
                )
            )
        assertEquals("POLITICO", portal.influenceGroup)
        assertFalse("group_overridden" in portal.attributes)
        assertEquals(
            null,
            parse(
                mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA", "group_overridden" to false, "influence_group" to "POLITICO")
            ).influenceGroup
        )
        val noGroup = assertThrows<ValidationException> { parse(mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA", "group_overridden" to true)) }
        assertEquals("attributes.influence_group", noGroup.violations.single().field)
    }

    @Test
    fun `fuera de rango, fraccion, ENUM desconocido, fecha futura y campo calculado son 400`() {
        val error =
            assertThrows<ValidationException> {
                parse(
                    mapOf("assessment_date" to "2026-06-01", "basis" to "RUMOR", "affect_environment" to 6, "power_legal_action" to 2.5, "stance" to "EN DUDA")
                )
            }
        assertEquals(
            setOf("attributes.affect_environment", "attributes.power_legal_action", "attributes.assessment_date", "attributes.basis", "attributes.stance"),
            error.violations.map { it.field }.toSet()
        )
        // un texto no es un indicador, aunque parezca un número
        val text = assertThrows<ValidationException> { parse(mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA", "affect_livelihood" to "4")) }
        assertEquals("attributes.affect_livelihood", text.violations.single().field)
        val computed = assertThrows<ValidationException> { parse(mapOf("interest_index" to 3)) }
        assertEquals("attributes.interest_index", computed.violations.single().field)
        val missing = assertThrows<ValidationException> { parse(emptyMap()) }
        assertEquals(setOf("attributes.assessment_date", "attributes.basis"), missing.violations.map { it.field }.toSet())
    }
}
