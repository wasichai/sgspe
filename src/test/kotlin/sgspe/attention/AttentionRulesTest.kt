package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
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

    // D-3: marcar confidencial una QCS existente lo hace su receptor o su responsable guardados (o quien tiene
    // READ_CONFIDENTIAL, que mira la guarda); ponerse de responsable en la misma escritura no cuenta
    @Test
    fun `marcar confidencial una QCS existente lo hace su receptor o su responsable`() {
        val other = UUID.randomUUID().toString()
        val before = mapOf("confidential" to false, "owner_user_id" to other, "received_by_user_id" to other)
        val marked = before + ("confidential" to true)
        assertTrue(AttentionRules.strangerMarks(before, marked, writer))
        assertFalse(AttentionRules.strangerMarks(before + ("received_by_user_id" to writer.toString()), marked, writer))
        assertFalse(AttentionRules.strangerMarks(before + ("owner_user_id" to writer.toString()), marked, writer))
        assertTrue(AttentionRules.strangerMarks(before, marked + ("owner_user_id" to writer.toString()), writer))
        // sin el paso de no confidencial a confidencial no hay nada que juzgar
        assertFalse(AttentionRules.strangerMarks(marked, marked, writer))
        assertFalse(AttentionRules.strangerMarks(before, before + ("description" to "otra"), writer))
        assertFalse(AttentionRules.strangerMarks(marked, before, writer))
        val finding = AttentionRules.confidentialMarkForbidden()
        assertEquals("CONFIDENTIAL_MARK_FORBIDDEN" to "confidential", finding.code to finding.field)
        assertEquals(HttpStatus.FORBIDDEN, finding.status)
        assertEquals("Marcar confidencial una QCS existente lo hace su receptor, su responsable o quien tiene READ_CONFIDENTIAL", finding.message)
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
        // sin receptor no hay copia que respetar
        assertEquals(
            false,
            AttentionRules.receiverCopied(
                "inquiry",
                copy + mapOf("received_by_user_id" to null, "reclassified_from_grievance" to id.toString()),
                marked = true
            )
        )
        // el vínculo de otro objeto no cuenta: una consulta no se reclasifica desde otra consulta
        assertEquals(false, AttentionRules.receiverCopied("inquiry", copy + ("reclassified_from_inquiry" to id.toString()), marked = true))
    }

    // los vínculos que solo escriben los comandos (reclasificación y aceptación): una persona sin la marca SgspeWrite que los
    // fija, cambia o vacía recibe 403 COMMAND_ONLY sobre el campo; la plataforma y los comandos sí los escriben, y un cambio
    // que no los toca pasa
    @Test
    fun `los vinculos de los comandos solo los escriben sus comandos`() {
        val refused = AttentionRules.commandOnly("inquiry", setOf("title", "reclassified_from_grievance"), person = true, marked = false)
        assertEquals(listOf("COMMAND_ONLY" to "reclassified_from_grievance"), codes(listOfNotNull(refused)))
        assertEquals(403, refused!!.status.value())
        assertEquals(
            listOf("COMMAND_ONLY" to "created_commitment"),
            codes(listOfNotNull(AttentionRules.commandOnly("stakeholder_request", setOf("created_commitment"), person = true, marked = false)))
        )
        assertEquals(null, AttentionRules.commandOnly("inquiry", setOf("reclassified_from_grievance"), person = true, marked = true))
        assertEquals(null, AttentionRules.commandOnly("inquiry", setOf("reclassified_from_grievance"), person = false, marked = false))
        assertEquals(null, AttentionRules.commandOnly("inquiry", setOf("title"), person = true, marked = false))
        // created_commitment de una queja no existe: solo la solicitud lo tiene
        assertEquals(null, AttentionRules.commandOnly("grievance", setOf("created_commitment"), person = true, marked = false))
        // cada campo es una relación M2O del objeto en model.json: un nombre que el modelo no tiene no protegería nada
        for ((objectName, fields) in AttentionRules.COMMAND_FIELDS) {
            for (name in fields) {
                assertTrue(catalog.relationships.any { it.source == objectName && it.fieldName == name && it.type == "MANY_TO_ONE" }, "$objectName.$name")
            }
        }
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
