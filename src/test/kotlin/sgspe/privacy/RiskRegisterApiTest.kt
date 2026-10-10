package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// POST /api/sgspe/exports/risk-register (api.md §4.13, deliverables.md §10, ASM-101): la matriz de riesgos por nivel, sin texto
// libre ni correos en EXTERNO, PUBLICO con la marca reservado, sin los cerrados salvo includeClosed, el permiso EXPORT sobre
// social_risk y su fila de export_log
class RiskRegisterApiTest : F4ApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String = this.token
    ): ResultBytes {
        val result =
            client
                .post()
                .uri("/api/sgspe/exports/risk-register")
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

    private fun body(
        level: String,
        filters: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> = mapOf("filters" to mapOf("project" to listOf(testProject())) + filters, "disclosureLevel" to level, "checklistConfirmed" to true)

    // un riesgo revisado, con una medida y un impacto, y su código
    private fun assessed(manager: Account): Pair<String, String> {
        val risk = socialRisk(testProject(), attributes = mapOf("risk_source" to "Fuente reservada del riesgo"))
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-10-05", "probability" to 3, "impact" to 4, "notes" to "Nota de la revisión")),
                manager.token,
                withReason("revisión")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        mitigationMeasure(risk, manager)
        createRecord(
            "potential_impact",
            mapOf(
                "project" to testProject(),
                "social_risk" to risk,
                "description" to "Impacto reservado",
                "impact_sign" to "NEGATIVO"
            )
        )
        return risk to record("social_risk", risk)["attributes"]["code"].asString()
    }

    @Test
    fun `INTERNO trae la matriz, las revisiones, las medidas y los impactos`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val (_, code) = assessed(manager)
        val file = export(body("INTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Matriz", "Revisiones", "Medidas", "Impactos"), sheets.keys.toList())
        assertTrue(code in sheets.getValue("Matriz"))
        assertTrue("Fuente reservada del riesgo" in sheets.getValue("Matriz"))
        assertTrue("Nota de la revisión" in sheets.getValue("Revisiones"))
        assertTrue(manager.email in sheets.getValue("Revisiones"))
    }

    @Test
    fun `EXTERNO no trae texto libre ni correos y cuenta las medidas en la matriz`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val (_, code) = assessed(manager)
        val file = export(body("EXTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Matriz", "Revisiones", "Impactos"), sheets.keys.toList())
        val all = sheets.values.flatten()
        assertTrue(code in all)
        assertFalse(all.any { it.contains("reservad") || it.contains("Nota de la revisión") })
        assertFalse(all.any { it.contains("@sgspe.test") })
        assertTrue("Medidas completadas" in sheets.getValue("Matriz"))
    }

    @Test
    fun `PUBLICO cuenta los riesgos activos con la marca reservado`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        assessed(manager)
        val file = export(body("PUBLICO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Por nivel y categoría", "Por tratamiento y nivel"), sheets.keys.toList())
        assertTrue(SmallCells.HIDDEN in sheets.getValue("Por nivel y categoría"))
    }

    @Test
    fun `sin includeClosed no salen los cerrados, y un filtro invalido es INVALID_FILTER`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val closed = socialRisk(testProject())
        move("social_risk", closed, "close_identified", manager.token, "el riesgo dejó de existir")
        val code = record("social_risk", closed)["attributes"]["code"].asString()
        assertFalse(code in sheets(export(body("INTERNO"), manager.token).body).getValue("Matriz"))
        assertTrue(code in sheets(export(body("INTERNO", mapOf("includeClosed" to true)), manager.token).body).getValue("Matriz"))
        val wrong = export(body("INTERNO", mapOf("riskLevel" to listOf("ENORME"))), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.text)
        assertEquals("INVALID_FILTER", tree(wrong.text)["errors"][0]["message"].asString())
    }

    @Test
    fun `quien no tiene EXPORT sobre social_risk recibe 403 y la exportacion deja su fila en export_log`() {
        assertEquals(HttpStatus.FORBIDDEN, export(body("INTERNO"), member("GRIEVANCE_MANAGER").token).status)
        val manager = member("SOCIAL_RISK_MANAGER")
        assertEquals(HttpStatus.OK, export(body("INTERNO"), manager.token).status)
        val logs = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${manager.id}", null, HttpStatus.OK))["content"].values()
        assertTrue(logs.any { it["attributes"]["object_name"].asString() == "social_risk" })
    }
}
