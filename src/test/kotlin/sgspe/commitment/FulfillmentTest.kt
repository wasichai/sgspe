package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// D9 de WF-D: la entrega con conformidad y la evidencia según el tipo de recurso (RN-COM-001, ASM-009), sin base
class FulfillmentTest {
    private fun delivery(
        types: Set<String>,
        given: Boolean = true,
        by: String? = "Junta directiva"
    ) = Fulfillment.DeliveryEvidence(given, by, types)

    @Test
    fun `MATERIAL exige acta de entrega y foto en una misma entrega con conformidad`() {
        assertNull(Fulfillment.finding("MATERIAL", listOf(delivery(setOf("ACTA DE ENTREGA", "FOTO")))))
        assertEquals("EVIDENCE_REQUIRED", Fulfillment.finding("MATERIAL", listOf(delivery(setOf("ACTA DE ENTREGA"))))?.code)
        assertEquals(listOf("FOTO"), Fulfillment.missing("MATERIAL", delivery(setOf("ACTA DE ENTREGA"))))
        // la foto en otra entrega no completa la primera
        assertEquals("EVIDENCE_REQUIRED", Fulfillment.finding("MATERIAL", listOf(delivery(setOf("ACTA DE ENTREGA")), delivery(setOf("FOTO"), by = null)))?.code)
    }

    @Test
    fun `NO MATERIAL exige conformidad con quienes la dieron y un acta, una carta u otro`() {
        assertNull(Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("CARTA")))))
        assertEquals("EVIDENCE_REQUIRED", Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("FOTO"))))?.code)
        assertEquals("DELIVERY_REQUIRED", Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("CARTA"), by = null)))?.code)
        assertEquals("DELIVERY_REQUIRED", Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("CARTA"), given = false)))?.code)
        assertEquals("DELIVERY_REQUIRED", Fulfillment.finding("MATERIAL", emptyList())?.code)
    }

    // la evidencia de otro proyecto (o sin proyecto) no cuenta para D9, aunque esté vinculada a la entrega (P54)
    @Test
    fun `solo cuenta la evidencia del proyecto del compromiso`() {
        val project = UUID.randomUUID()
        val evidence =
            listOf(
                mapOf("project" to project.toString(), "evidence_type" to "ACTA DE ENTREGA"),
                mapOf("project" to UUID.randomUUID().toString(), "evidence_type" to "FOTO"),
                mapOf("evidence_type" to "CARTA")
            )
        assertEquals(setOf("ACTA DE ENTREGA"), Fulfillment.countedTypes(project, evidence))
        assertEquals(emptySet<String>(), Fulfillment.countedTypes(null, evidence))
    }

    // un compromiso cumplido o cancelado ya no bloquea a quien depende de él (D10) y congela sus entregas (P55)
    @Test
    fun `cumplido y cancelado resuelven la dependencia y congelan las entregas`() {
        assertEquals(setOf("fulfilled", "verified", "closed", "cancelled"), Fulfillment.RESOLVED)
        assertEquals(Commitments.SETTLED, Fulfillment.RESOLVED)
        assertTrue(Commitments.SETTLED.none { it in Commitments.EXECUTING })
    }
}
