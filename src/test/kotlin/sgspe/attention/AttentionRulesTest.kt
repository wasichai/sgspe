package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.util.UUID

// las reglas de datos de las QCS (especificación de F4 §3.2), sin base: el código y el campo de cada una
class AttentionRulesTest {
    private val id = UUID.randomUUID()
    private val writer = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(findings: List<sgspe.common.Finding>) = findings.map { it.code to it.field }

    @Test
    fun `una QCS anonima no lleva persona ni grupo de interes`() {
        assertEquals(listOf("ANONYMOUS_WITH_IDENTITY" to "person"), codes(AttentionRules.record("inquiry", null, mapOf("anonymous" to true, "person" to "p1"))))
        assertEquals(
            listOf("ANONYMOUS_WITH_IDENTITY" to "stakeholder"),
            codes(
                AttentionRules.record(
                    "grievance",
                    id,
                    mapOf(
                        "anonymous" to true,
                        "stakeholder" to "s1"
                    )
                )
            )
        )
        assertEquals(emptyList<Pair<String, String?>>(), codes(AttentionRules.record("grievance", id, mapOf("anonymous" to false, "person" to "p1"))))
    }

    @Test
    fun `una queja no es duplicado de si misma`() {
        assertEquals(listOf("INVALID_LINK" to "duplicate_of"), codes(AttentionRules.record("grievance", id, mapOf("duplicate_of" to id.toString()))))
    }

    @Test
    fun `atender y resolver una queja exigen sus datos`() {
        assertEquals(listOf("FIELD_REQUIRED" to "severity"), codes(AttentionRules.transition("grievance", "start_action", mapOf("category" to "EMPLEO"))))
        assertEquals(listOf("FIELD_REQUIRED" to "category"), codes(AttentionRules.transition("grievance", "investigate", mapOf("severity" to "BAJA"))))
        assertEquals(
            listOf("FIELD_REQUIRED" to "resolution", "FIELD_REQUIRED" to "response"),
            codes(AttentionRules.transition("grievance", "resolve_high_severity", emptyMap()))
        )
    }

    @Test
    fun `las transiciones que comunican una respuesta exigen response y responded_at`() {
        assertEquals(
            listOf("FIELD_REQUIRED" to "response", "FIELD_REQUIRED" to "responded_at"),
            codes(AttentionRules.transition("grievance", "reject_received", emptyMap()))
        )
        assertEquals(listOf("FIELD_REQUIRED" to "responded_at"), codes(AttentionRules.transition("inquiry", "close_referred", mapOf("response" to "x"))))
        assertEquals(listOf("FIELD_REQUIRED" to "response"), codes(AttentionRules.transition("stakeholder_request", "decline", emptyMap())))
        assertEquals(
            emptyList<Pair<String, String?>>(),
            codes(
                AttentionRules.transition(
                    "inquiry",
                    "answer",
                    mapOf(
                        "response" to "x",
                        "responded_at" to "2026-10-08T15:00:00Z"
                    )
                )
            )
        )
    }

    @Test
    fun `no se reabre una queja con conformidad`() {
        assertEquals(
            listOf("CONFORMITY_GIVEN" to "complainant_conformity_given"),
            codes(
                AttentionRules.transition(
                    "grievance",
                    "reopen",
                    mapOf("complainant_conformity_given" to true)
                )
            )
        )
        assertEquals(
            emptyList<Pair<String, String?>>(),
            codes(AttentionRules.transition("grievance", "reopen", mapOf("complainant_conformity_given" to false)))
        )
    }

    @Test
    fun `la confidencialidad protegida y el receptor de una confidencial`() {
        val before = mapOf("confidential" to true, "owner_user_id" to "a", "received_by_user_id" to "b")
        assertEquals(listOf("confidential"), AttentionRules.protectedChanges(before, before + ("confidential" to false)))
        assertEquals(
            listOf("owner_user_id", "received_by_user_id"),
            AttentionRules.protectedChanges(
                before,
                before + mapOf("owner_user_id" to "c", "received_by_user_id" to null)
            )
        )
        assertEquals(emptyList<String>(), AttentionRules.protectedChanges(before + ("confidential" to false), mapOf("owner_user_id" to "c")))
        assertTrue(AttentionRules.foreignReceiver(mapOf("confidential" to true, "received_by_user_id" to null), writer))
        assertEquals(false, AttentionRules.foreignReceiver(mapOf("confidential" to true, "received_by_user_id" to writer.toString()), writer))
        assertEquals(false, AttentionRules.foreignReceiver(mapOf("confidential" to false), writer))
    }

    // la reclasificación copia el receptor del original: es una copia del sistema (la marca SgspeWrite de Records) con el
    // vínculo reclassified_from_* informado, no un receptor elegido por quien llama; sin la marca o sin el vínculo, no
    @Test
    fun `el receptor copiado por la reclasificacion no es un receptor ajeno`() {
        val copy = mapOf("confidential" to true, "received_by_user_id" to UUID.randomUUID().toString())
        assertTrue(AttentionRules.receiverCopied("inquiry", copy + ("reclassified_from_grievance" to id.toString()), marked = true))
        assertTrue(AttentionRules.receiverCopied("grievance", copy + ("reclassified_from_request" to id.toString()), marked = true))
        assertEquals(false, AttentionRules.receiverCopied("inquiry", copy + ("reclassified_from_grievance" to id.toString()), marked = false))
        assertEquals(false, AttentionRules.receiverCopied("inquiry", copy + ("reclassified_from_grievance" to null), marked = true))
        // el vínculo de otro objeto no cuenta: una consulta no se reclasifica desde otra consulta
        assertEquals(false, AttentionRules.receiverCopied("inquiry", copy + ("reclassified_from_inquiry" to id.toString()), marked = true))
    }

    // las tablas nombran transiciones por texto: si el modelo renombra una, la regla dejaría de vigilarla en silencio
    @Test
    fun `cada transicion de las reglas existe en su workflow`() {
        val named =
            AttentionRules.RESPONDED_AT.flatMap { (o, ts) -> ts.map { o to it } } + AttentionRules.RESPONSE.flatMap { (o, ts) -> ts.map { o to it } } +
                (AttentionRules.ATTEND + AttentionRules.RESOLVE + AttentionRules.REOPEN).map { "grievance" to it } +
                ("stakeholder_request" to AttentionRules.CLOSE_UNFULFILLED)
        assertEquals(emptyList<Pair<String, String>>(), named.filter { (o, t) -> catalog.transition(o, t) == null })
        assertTrue(AttentionWriteGuard.POLICY in catalog.objectOrNull("project")!!.fields)
    }
}
