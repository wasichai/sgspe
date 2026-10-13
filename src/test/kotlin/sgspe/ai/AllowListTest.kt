package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog

// la lista permitida de ai-architecture.md §4.2, cerrada (§15: «pares fuera de la lista»): cada fila de F7 se reconoce y todo par
// de tipo, objeto y campo que no está, un campo derivado o workflow_state no tiene fila
class AllowListTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `cada fila de F7 se reconoce`() {
        val rows =
            mapOf(
                Triple("CLASIFICACION", "grievance", """{"field":"category","value":"EMPLEO"}""") to AcceptRow.CLASSIFICATION,
                Triple("CLASIFICACION", "inquiry", """{"field":"priority","value":"ALTA"}""") to AcceptRow.CLASSIFICATION,
                Triple("CLASIFICACION", "stakeholder_request", """{"field":"topic","value":"t1"}""") to AcceptRow.CLASSIFICATION,
                Triple("CLASIFICACION", "engagement_question", """{"field":"topic","value":"t1"}""") to AcceptRow.CLASSIFICATION,
                Triple("CLASIFICACION", "media_mention", """{"field":"tone","value":"NEGATIVO"}""") to AcceptRow.CLASSIFICATION,
                Triple("EXTRACCION", "engagement", """{"item":"topic","topic":"t1"}""") to AcceptRow.TOPIC,
                Triple("EXTRACCION", "engagement", """{"item":"place","field":"community","value":"c1"}""") to AcceptRow.PLACE,
                Triple("EXTRACCION", "agreement", """{"item":"date","field":"expiry_date","value":"2027-03-31"}""") to AcceptRow.DATE,
                Triple("EXTRACCION", "agreement", """{"item":"clause","clause_number":"5","clause_text":"x","creates_commitment":true}""") to AcceptRow.CLAUSE,
                Triple("EXTRACCION", "organization", """{"item":"actor","kind":"organization","name":"Junta Ejemplo"}""") to AcceptRow.ORGANIZATION,
                Triple("EXTRACCION", "person", """{"item":"actor","kind":"person","name":"[PERSONA]"}""") to AcceptRow.PERSON,
                Triple("BORRADOR DE ACTA", "engagement", "## Acta") to AcceptRow.MINUTES,
                Triple("DUPLICADO", "grievance", """{"other_record_id":"g2","reasons":["Misma fecha"]}""") to AcceptRow.GRIEVANCE_DUPLICATE,
                Triple("DUPLICADO", "organization", """{"other_record_id":"o2"}""") to AcceptRow.ORGANIZATION_DUPLICATE
            )
        for ((key, row) in rows) assertEquals(row, AllowList.rowOf(key.first, key.second, key.third), key.toString())
        for (target in listOf("person", "stakeholder", "commitment", "inquiry", "stakeholder_request")) {
            assertEquals(AcceptRow.DECISION_DUPLICATE, AllowList.rowOf("DUPLICADO", target, """{"other_record_id":"x"}"""), target)
        }
    }

    @Test
    fun `lo que no esta en la lista no tiene fila`() {
        val outside =
            listOf(
                Triple("CLASIFICACION", "stakeholder", """{"field":"stance","value":"A FAVOR"}"""),
                Triple("CLASIFICACION", "grievance", """{"field":"workflow_state","value":"closed"}"""),
                Triple("CLASIFICACION", "grievance", """{"field":"response_due_date","value":"2026-12-01"}"""),
                Triple("CLASIFICACION", "media_mention", """{"field":"category","value":"OTRO"}"""),
                Triple("EXTRACCION", "engagement", """{"item":"date","field":"held_at","value":"2026-10-01"}"""),
                Triple("EXTRACCION", "engagement", """{"item":"place","field":"location","value":"x"}"""),
                Triple("EXTRACCION", "person", """{"item":"actor","kind":"organization","name":"x"}"""),
                Triple("EXTRACCION", "commitment", """{"item":"commitment","text":"x"}"""),
                Triple("DUPLICADO", "evidence", """{"other_record_id":"ev2"}"""),
                Triple("BORRADOR DE ACTA", "project", "## Acta"),
                Triple("RESUMEN EJECUTIVO", "project", "## Resumen"),
                Triple("BORRADOR DE SECCION", "deliverable_section", "## Sección"),
                Triple("OTRO", "grievance", "{}"),
                Triple("CLASIFICACION", "grievance", "no es JSON")
            )
        for ((kind, target, value) in outside) assertNull(AllowList.rowOf(kind, target, value), "$kind $target $value")
    }

    @Test
    fun `ningun campo de la lista es derivado`() {
        for ((target, fields) in AllowList.CLASSIFICATION_FIELDS) {
            for (field in fields) assertFalse(field in catalog.derivedFields(target), "$target.$field")
        }
        for (field in AllowList.PLACE_FIELDS) assertFalse(field in catalog.derivedFields("engagement"), field)
        for (field in AllowList.DATE_FIELDS) assertFalse(field in catalog.derivedFields("agreement"), field)
    }

    @Test
    fun `un marcador por llamada o estable se reconoce en un valor`() {
        for (text in listOf("[PERSONA]", "Hola [PERSONA_2]", "[PERSONA:00000000-0000-0000-0000-0000000000a1]", "[DOCUMENTO]", "[CORREO_1]", "[CITA]")) {
            assertTrue(AllowList.hasMarker(text), text)
        }
        for (text in listOf("Ana Ejemplo", "[Persona]", "[PERSONAS]", "(PERSONA_1)")) assertFalse(AllowList.hasMarker(text), text)
    }

    @Test
    fun `item y kind no cambian de fila`() {
        assertNull(AllowList.misplaced(AcceptRow.PERSON, mapOf("item" to "actor", "kind" to "person")))
        assertNull(AllowList.misplaced(AcceptRow.TOPIC, mapOf("topic" to "t1")))
        assertNull(AllowList.misplaced(AcceptRow.CLASSIFICATION, mapOf("item" to "lo que sea")))
        assertEquals("kind", AllowList.misplaced(AcceptRow.PERSON, mapOf("item" to "actor", "kind" to "organization")))
        assertEquals("item", AllowList.misplaced(AcceptRow.PLACE, mapOf("item" to "date")))
    }
}
