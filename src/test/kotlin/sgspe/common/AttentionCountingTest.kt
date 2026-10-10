package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// dashboards.md §3.3: cada planteamiento cuenta una vez, como el último registro de la cadena de reclasificaciones
class AttentionCountingTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun raised(
        type: String,
        id: String,
        state: String = "received",
        attributes: Map<String, Any?> = emptyMap()
    ) = Raised(type, id, state, attributes)

    @Test
    fun `un original reclasificado no cuenta y su registro nuevo si`() {
        val queja = raised("grievance", "q1", "closed")
        val consulta = raised("inquiry", "c1", "received", mapOf("reclassified_from_grievance" to "q1"))
        val otra = raised("grievance", "q2", "acknowledged")
        assertEquals(listOf(consulta, otra), AttentionCounting.counted(listOf(queja, consulta, otra)))
        assertEquals(setOf("grievance" to "q1"), AttentionCounting.originals(listOf(queja, consulta, otra)))
    }

    @Test
    fun `una cadena de dos reclasificaciones cuenta una sola vez, como el ultimo`() {
        val queja = raised("grievance", "q1", "closed")
        val solicitud = raised("stakeholder_request", "s1", "closed", mapOf("reclassified_from_grievance" to "q1"))
        val consulta = raised("inquiry", "c1", "answered", mapOf("reclassified_from_request" to "s1"))
        assertEquals(listOf(consulta), AttentionCounting.counted(listOf(queja, solicitud, consulta)))
    }

    @Test
    fun `sin el registro nuevo en la lista el original cuenta`() {
        // quien llama no lee el registro nuevo (una QCS confidencial): solo cuenta lo que ve
        val queja = raised("grievance", "q1", "closed")
        assertEquals(listOf(queja), AttentionCounting.counted(listOf(queja)))
    }

    @Test
    fun `los totales del reporte`() {
        val rows =
            listOf(
                raised("grievance", "q1", "closed", mapOf("responded_at" to "2026-10-01T15:00:00Z")),
                raised("grievance", "q2", "rejected", mapOf("responded_at" to "2026-10-01T15:00:00Z")),
                raised("grievance", "q3", "withdrawn"),
                raised("grievance", "q4", "in_action", mapOf("deadline_status" to "VENCIDO")),
                raised("inquiry", "c1", "received", mapOf("deadline_status" to "AL DIA")),
                raised("stakeholder_request", "s1", "declined", mapOf("responded_at" to "2026-10-02T15:00:00Z")),
                raised("inquiry", "c2", "closed", mapOf("deadline_status" to "VENCIDO"))
            )
        assertEquals(
            AttentionTotals(received = 7, open = 3, closed = 2, rejected = 1, withdrawn = 1, absolved = 3, overdue = 1),
            AttentionCounting.totals(rows)
        )
    }

    // los nombres de Attention existen en el modelo: un renombre rompería el conteo en silencio
    @Test
    fun `los vinculos de reclasificacion y los estados finales existen en el modelo`() {
        for ((objectName, links) in Attention.RECLASSIFIED_FROM) {
            for ((field, source) in links) assertEquals(source, catalog.manyToOneFields(objectName).toMap()[field], "$objectName.$field")
        }
        for ((objectName, states) in Attention.FINAL_STATES) {
            assertEquals(emptySet<String>(), states - catalog.states(objectName).toSet(), objectName)
        }
        for (objectName in Attention.OBJECTS) {
            val fields = catalog.objectOrNull(objectName)!!.fields.keys
            val expected =
                listOf(Attention.CONFIDENTIAL, Attention.OWNER, Attention.RECEIVER, Attention.RECEIVED_AT, Attention.RESPONDED_AT, Attention.DEADLINE_STATUS)
            assertEquals(emptyList<String>(), expected.filter { it !in fields }, objectName)
            assertEquals(true, Attention.READ_CONFIDENTIAL in catalog.objectOrNull(objectName)!!.actions, objectName)
        }
        assertEquals(Attention.HIGH_SEVERITY, catalog.enumOf("grievance", Attention.SEVERITY).takeLast(2).toSet())
        assertEquals("reclassified_from_request", Attention.reclassifiedField("inquiry", "stakeholder_request"))
    }
}
