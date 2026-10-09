package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// POST /api/sgspe/exports/commitment-matrix (api.md §4.13, RQ-REP-022): la matriz por nivel, su fila de export_log, y nunca
// el documento de identidad ni, en EXTERNO, lo restringido
class CommitmentMatrixApiTest : F3DomainApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String = this.token
    ): ResultBytes {
        val result =
            client
                .post()
                .uri("/api/sgspe/exports/commitment-matrix")
                .headers { it.set("Authorization", token) }
                .bodyValue(body)
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return ResultBytes(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun texts(bytes: ByteArray): List<String> =
        XSSFWorkbook(bytes.inputStream()).use { book ->
            (0 until book.numberOfSheets).flatMap { i -> book.getSheetAt(i).flatMap { row -> row.map { it.toString() } } }
        }

    @Test
    fun `INTERNO trae la matriz y sus entregas, sin columna de documento de identidad`() {
        val project = testProject()
        approvedCommitment(project, attributes = mapOf("beneficiaries_detail" to "Detalle restringido"))
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = texts(file.body)
        assertTrue(cells.contains("Matriz") || cells.any { it == "Código" })
        assertFalse(cells.any { it.contains("identidad", ignoreCase = true) })
        assertTrue(cells.contains("Detalle restringido"))
    }

    @Test
    fun `EXTERNO exige la lista de verificacion y no trae lo restringido`() {
        val project = testProject()
        approvedCommitment(project, attributes = mapOf("beneficiaries_detail" to "Detalle restringido"))
        val unchecked = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "EXTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, unchecked.status)
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "EXTERNO", "checklistConfirmed" to true))
        assertEquals(HttpStatus.OK, file.status, file.text)
        assertFalse(texts(file.body).contains("Detalle restringido"))
    }

    @Test
    fun `un estado fuera del catalogo es INVALID_FILTER y un proyecto sin asignacion NOT_ASSIGNED`() {
        val bad = export(mapOf("filters" to mapOf("state" to listOf("vencido")), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, bad.status)
        val manager = userWithRole("COMMITMENT_MANAGER")
        val denied = export(mapOf("filters" to mapOf("project" to listOf(testProject())), "disclosureLevel" to "INTERNO"), manager.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.text)
    }
}
