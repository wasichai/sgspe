package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// POST /api/sgspe/exports/attention-register (api.md §4.13, deliverables.md §10, ASM-101): las QCS por nivel, sin confidenciales
// ni texto libre en EXTERNO (también para el gestor de quejas, que las lee), PUBLICO con la marca reservado, y el permiso EXPORT
// sobre los tres objetos
class AttentionRegisterApiTest : F4ApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String = this.token
    ): ResultBytes {
        val result =
            client
                .post()
                .uri("/api/sgspe/exports/attention-register")
                .headers { it.set("Authorization", token) }
                .bodyValue(body)
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return ResultBytes(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun sheets(bytes: ByteArray): Map<String, List<String>> =
        XSSFWorkbook(bytes.inputStream()).use { book ->
            (0 until book.numberOfSheets).associate { i -> book.getSheetAt(i).sheetName to book.getSheetAt(i).flatMap { row -> row.map { it.toString() } } }
        }

    private fun body(level: String): Map<String, Any?> =
        mapOf("filters" to mapOf("project" to listOf(testProject())), "disclosureLevel" to level, "checklistConfirmed" to true)

    @Test
    fun `INTERNO trae las tres hojas y el resumen, con la confidencial que quien exporta lee`() {
        val manager = member("GRIEVANCE_MANAGER")
        val code =
            record(
                "grievance",
                grievance(testProject(), member("COMMUNITY_RELATIONS_OFFICER"), mapOf("confidential" to true))
            )["attributes"]["code"].asString()
        inquiry(testProject())
        val file = export(body("INTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Quejas", "Consultas", "Solicitudes", "Resumen"), sheets.keys.toList())
        assertTrue(code in sheets.getValue("Quejas"))
        assertTrue(sheets.getValue("Quejas").contains("Planteamiento de prueba sobre el canal de riego."))
    }

    @Test
    fun `EXTERNO no trae confidenciales ni texto libre, tampoco para el gestor de quejas`() {
        val manager = member("GRIEVANCE_MANAGER")
        val hidden =
            record(
                "grievance",
                grievance(testProject(), member("COMMUNITY_RELATIONS_OFFICER"), mapOf("confidential" to true))
            )["attributes"]["code"].asString()
        val open = record("grievance", grievance(testProject()))["attributes"]["code"].asString()
        val file = export(body("EXTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = sheets(file.body)
        val all = cells.values.flatten()
        assertTrue(open in all)
        assertFalse(hidden in all)
        assertFalse(all.any { it.contains("canal de riego") }, "sin texto libre")
        assertTrue("Notas" in cells.keys)
        assertFalse(cells.getValue("Quejas").contains("Persona"))
    }

    @Test
    fun `PUBLICO solo cuenta con la marca reservado y dice si incluye las confidenciales`() {
        grievance(testProject())
        val file = export(body("PUBLICO"), member("SOCIAL_MANAGER").token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = sheets(file.body)
        assertEquals(listOf("Por tipo y estado", "Por tipo y canal", "Por tipo y categoría", "Absueltas por tipo", "Notas"), cells.keys.toList())
        assertTrue(cells.values.flatten().contains(SmallCells.HIDDEN))
        assertFalse(cells.values.flatten().any { it.startsWith("QJ-") })
        assertTrue(cells.getValue("Notas").any { it.contains("confidenciales: no") })
    }

    @Test
    fun `sin EXPORT sobre los tres objetos es 403 y un filtro invalido INVALID_FILTER`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.FORBIDDEN, export(body("INTERNO"), officer.token).status)
        val bad = export(mapOf("filters" to mapOf("type" to listOf("commitment")), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, bad.status, bad.text)
        assertEquals("INVALID_FILTER", tree(bad.text)["errors"][0]["message"].asString())
        assertEquals("filters.type", tree(bad.text)["errors"][0]["field"].asString())
    }
}
