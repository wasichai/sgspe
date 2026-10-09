package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.SgspeApiTest
import java.io.File
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

    // sin tipo de recurso no se sabe qué evidencia pedir: no cuenta como NO MATERIAL
    @Test
    fun `sin tipo de recurso D9 falla cerrada con FIELD_REQUIRED`() {
        val finding = Fulfillment.resourceTypeFinding(null)
        assertEquals("FIELD_REQUIRED", finding?.code)
        assertEquals("resource_type", finding?.field)
        assertNull(Fulfillment.resourceTypeFinding("NO MATERIAL"))
    }

    // RESOLVED (D10) y el congelamiento de las entregas (P55) contra el workflow de WF-D: son los estados a los que se llega
    // por register_fulfillment o por una cancelación, y los que salen de ellos sin volver a ejecución; ninguno admite entregas
    @Test
    fun `RESOLVED son los estados de WF-D desde el cumplimiento o la cancelacion, fuera de la ejecucion`() {
        assertEquals(setOf("fulfilled", "verified", "closed", "cancelled"), Fulfillment.RESOLVED)
        val transitions = workflow()
        val reached = mutableSetOf<String>()
        val frontier = ArrayDeque(transitions.filter { it["name"] == Fulfillment.REGISTER || it["name"] in Fulfillment.CANCELS }.map { it["to"] as String })
        while (frontier.isNotEmpty()) {
            val state = frontier.removeFirst()
            if (!reached.add(state)) continue
            transitions.filter { it["from"] == state && it["to"] !in Commitments.EXECUTING }.forEach { frontier.addLast(it["to"] as String) }
        }
        assertEquals(reached, Fulfillment.RESOLVED)
        assertTrue(Fulfillment.RESOLVED.none { it in Commitments.EXECUTING })
    }

    // el candado de FulfillmentLockRule cubre todas las cancelaciones del workflow, y solo esas
    @Test
    fun `CANCELS son las transiciones de WF-D que llegan a cancelled`() {
        assertEquals(workflow().filter { it["to"] == "cancelled" }.map { it["name"] }.toSet(), Fulfillment.CANCELS)
    }

    @Suppress("UNCHECKED_CAST")
    private fun workflow(): List<Map<String, Any?>> {
        val json = SgspeApiTest.JSON.readValue(File("model/workflows/commitment.json"), Map::class.java) as Map<String, Any?>
        return (json["definition"] as Map<String, Any?>)["transitions"] as List<Map<String, Any?>>
    }
}
