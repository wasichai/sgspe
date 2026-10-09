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

    private fun errorCode(result: ResultBytes): String = tree(result.text)["errors"][0]["message"].asString()

    // un compromiso aprobado con un solicitante persona cuyo documento de identidad es conocido
    private fun commitmentWithPerson(project: String): String {
        val id = approvedCommitment(project, attributes = mapOf("beneficiaries_detail" to "Detalle restringido"))
        val person = createRecord("person", mapOf("given_names" to "Persona Ejemplo", "sex" to "FEMENINO", "id_document_number" to ID_DOCUMENT))
        val requester = stakeholder(project, mapOf("person" to person))
        assertEquals(HttpStatus.NO_CONTENT, linkWithReason("commitment", id, "commitment_stakeholder", requester).status)
        return id
    }

    @Test
    fun `dos campanas distintas en el filtro son INVALID_FILTER y no ensanchan la exportacion`() {
        val project = testProject()

        fun campaign(n: Int) = "00000000-0000-4000-8000-00000000000$n"
        val body = { values: List<String> ->
            mapOf(
                "filters" to mapOf("project" to listOf(project), "explorationCampaign" to values),
                "disclosureLevel" to "INTERNO"
            )
        }
        val two = export(body(listOf(campaign(1), campaign(2))))
        assertEquals(HttpStatus.BAD_REQUEST, two.status, two.text)
        assertEquals("INVALID_FILTER", errorCode(two))
        assertEquals("INVALID_FILTER", errorCode(export(body(listOf(campaign(1), campaign(2), campaign(1))))))
    }

    @Test
    fun `el documento de identidad del solicitante no sale en ningun nivel`() {
        val project = testProject()
        commitmentWithPerson(project)
        val filters = mapOf("project" to listOf(project))
        for (level in listOf("INTERNO", "EXTERNO", "PUBLICO")) {
            val file = export(mapOf("filters" to filters, "disclosureLevel" to level, "checklistConfirmed" to true))
            assertEquals(HttpStatus.OK, file.status, file.text)
            assertFalse(texts(file.body).any { it.contains(ID_DOCUMENT) }, level)
        }
    }

    @Test
    fun `un INTERNO de quien no lee lo restringido sale sin detalle ni nombres de personas`() {
        val project = testProject()
        commitmentWithPerson(project)
        val analyst = member("ANALYST", project)
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "INTERNO"), analyst.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = texts(file.body)
        assertFalse(cells.contains("Detalle restringido"))
        assertFalse(cells.any { it.contains("Persona Ejemplo") })
        assertTrue(cells.contains(NOT_AVAILABLE))
    }

    @Test
    fun `PUBLICO solo trae conteos con la marca reservado`() {
        val project = testProject()
        approvedCommitment(project)
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "PUBLICO"))
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = texts(file.body)
        assertTrue(cells.contains(SmallCells.HIDDEN))
        assertFalse(cells.any { it.startsWith("COM-") })
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
        assertEquals("CHECKLIST_REQUIRED", errorCode(unchecked))
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "EXTERNO", "checklistConfirmed" to true))
        assertEquals(HttpStatus.OK, file.status, file.text)
        assertFalse(texts(file.body).contains("Detalle restringido"))
    }

    @Test
    fun `un estado fuera del catalogo es INVALID_FILTER y un proyecto sin asignacion NOT_ASSIGNED`() {
        val bad = export(mapOf("filters" to mapOf("state" to listOf("vencido")), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, bad.status)
        assertEquals("INVALID_FILTER", errorCode(bad))
        val manager = userWithRole("COMMITMENT_MANAGER")
        val denied = export(mapOf("filters" to mapOf("project" to listOf(testProject())), "disclosureLevel" to "INTERNO"), manager.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.text)
        assertEquals("NOT_ASSIGNED", errorCode(denied))
    }

    private companion object {
        const val ID_DOCUMENT = "78901234"
    }
}
