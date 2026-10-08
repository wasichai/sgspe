package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Salidas» y deliverables.md §13.2 (RQ-REP-014, RQ-REP-022, RQ-AUD-011): el registro de grupos de
// interés en XLSX con su nivel de divulgación y su fila de export_log; EXTERNO sin nombres de personas; nunca el documento
// de identidad; PUBLICO solo agregados; sin EXPORT, 403. el nombre de una persona solo sale a quien puede leerlo
// (security.md §7.9): ANALYST exporta, pero no lee given_names ni stance, y READ_ONLY ni siquiera tiene EXPORT
class StakeholderRegisterApiTest : DomainApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String
    ) = client
        .post()
        .uri("/api/sgspe/exports/stakeholder-register")
        .header("Authorization", token)
        .bodyValue(body)
        .exchange()
        .expectBody(ByteArray::class.java)
        .returnResult()

    private fun request(
        project: String,
        level: String,
        extra: Map<String, Any?> = emptyMap()
    ) = mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to level) + extra

    // la primera hoja como filas de texto
    private fun table(bytes: ByteArray): List<List<String>> =
        XSSFWorkbook(bytes.inputStream()).use { workbook -> workbook.getSheetAt(0).map { row -> row.map { it.toString() } } }

    private fun cells(bytes: ByteArray): List<String> =
        XSSFWorkbook(bytes.inputStream()).use { workbook ->
            workbook.flatMap { sheet ->
                sheet.flatMap { row ->
                    row.map {
                        it.toString()
                    }
                }
            }
        }

    private fun logOf(accountId: String) =
        tree(send("GET", "/api/objects/export_log/records?exporter_user_id=$accountId&sort=exported_at&dir=desc", null, HttpStatus.OK))["content"]

    @Test
    fun `INTERNO nombra a las personas, EXTERNO las cita por codigo y ninguno trae el documento`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val person = createRecord("person", mapOf("given_names" to "Persona Ejemplo Uno", "id_document_number" to "00000001"))
        stakeholder(project, mapOf("person" to person))
        val internal = export(request(project, "INTERNO"), manager.token)
        assertEquals(200, internal.status.value())
        assertTrue(
            internal.responseHeaders.contentDisposition.filename!!
                .endsWith(".xlsx")
        )
        val internalCells = cells(internal.responseBody!!)
        assertTrue("Persona Ejemplo Uno" in internalCells)
        assertFalse(internalCells.any { "00000001" in it })
        // STAKEHOLDER_MANAGER lee la postura: la columna no es «no disponible»
        val rows = table(internal.responseBody!!)
        assertFalse(rows[1][rows[0].indexOf("Postura")] == NOT_AVAILABLE)
        val missing = exchange("POST", "/api/sgspe/exports/stakeholder-register", request(project, "EXTERNO"), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, missing.status)
        assertEquals(listOf("checklistConfirmed"), problemFields(missing.body))
        assertEquals("CHECKLIST_REQUIRED", tree(missing.body)["errors"][0]["message"].asString())
        val external = export(request(project, "EXTERNO", mapOf("checklistConfirmed" to true)), manager.token)
        val externalCells = cells(external.responseBody!!)
        assertFalse(externalCells.any { "Persona Ejemplo" in it })
        assertFalse(externalCells.any { "00000001" in it })
        assertTrue(externalCells.any { it.startsWith("GI-") })
        // lo que falló no escribió fila: solo las dos exportaciones
        val log = logOf(manager.id)
        assertEquals(2, log.size())
        assertEquals("EXTERNO", log[0]["attributes"]["disclosure_level"].asString())
        assertEquals("EXPORTACION", log[0]["attributes"]["export_kind"].asString())
        assertEquals(1, log[0]["attributes"]["row_count"].asInt())
        assertTrue(log[0]["attributes"]["filters_description"].asString().contains("lista de verificación"))
    }

    @Test
    fun `quien no lee la postura ni los nombres los ve no disponibles, PUBLICO trae solo agregados, y sin EXPORT 403`() {
        val project = projectWithCrs()
        stakeholder(project)
        stakeholder(project, mapOf("person" to createRecord("person", mapOf("given_names" to "Persona Ejemplo Dos", "id_document_number" to "00000002"))))
        val analyst = member("ANALYST", project)
        val analystBytes = export(request(project, "INTERNO"), analyst.token).responseBody!!
        val analystCells = cells(analystBytes)
        assertTrue(NOT_AVAILABLE in analystCells)
        // ANALYST no lee person.given_names ni stakeholder.stance (security.md §7.9): ni el nombre por la relación
        // stakeholder → person, ni la postura
        assertFalse(analystCells.any { "Persona Ejemplo" in it || "00000002" in it })
        val rows = table(analystBytes)
        assertTrue(rows.drop(1).all { it[rows[0].indexOf("Postura")] == NOT_AVAILABLE })
        val public = cells(export(request(project, "PUBLICO"), analyst.token).responseBody!!)
        assertTrue("Grupos de interés" in public)
        assertFalse("Asociación Ejemplo" in public)
        assertFalse(public.any { "Persona Ejemplo" in it || it.startsWith("GI-") })
        // sin EXPORT, 403: PROJECT_MANAGER lee los nombres, pero no exporta; READ_ONLY lee el registro y tampoco
        for (role in listOf("PROJECT_MANAGER", "READ_ONLY")) {
            val denied = exchange("POST", "/api/sgspe/exports/stakeholder-register", request(project, "INTERNO"), member(role, project).token)
            assertEquals(HttpStatus.FORBIDDEN, denied.status, role)
        }
        assertEquals(HttpStatus.NOT_FOUND, exchange("POST", "/api/sgspe/exports/commitment-matrix", mapOf("disclosureLevel" to "INTERNO")).status)
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("POST", "/api/objects/export_log/records", mapOf("attributes" to mapOf("export_kind" to "EXPORTACION"))).status
        )
    }

    @Test
    fun `un nombre que empieza con signo de formula sale como texto`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val person = createRecord("person", mapOf("given_names" to "=HYPERLINK(\"http://ejemplo.invalid\")"))
        stakeholder(project, mapOf("person" to person))
        fixture("position", mapOf("person" to person, "position_title" to "@cargo", "position_status" to "VIGENTE"))
        stakeholder(project, mapOf("organization" to createRecord("organization", mapOf("name" to "+Asociación Ejemplo"))))
        val cells = cells(export(request(project, "INTERNO"), manager.token).responseBody!!)
        assertTrue(cells.none { it.startsWith("=") || it.startsWith("+") || it.startsWith("@") }, cells.toString())
        assertTrue("'=HYPERLINK(\"http://ejemplo.invalid\")" in cells)
        assertTrue("'+Asociación Ejemplo" in cells)
        assertTrue(cells.any { it.startsWith("'@cargo") })
    }

    @Test
    fun `exportar un solo proyecto deja el proyecto en export_log y exige asignacion a el`() {
        val project = projectWithCrs()
        stakeholder(project)
        val outsider = userWithRole("ANALYST")
        val denied = exchange("POST", "/api/sgspe/exports/stakeholder-register", request(project, "INTERNO"), outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals("NOT_ASSIGNED", tree(denied.body)["errors"][0]["message"].asString())
        assign(outsider, project, "ANALYST")
        assertEquals(200, export(request(project, "INTERNO"), outsider.token).status.value())
        val log = logOf(outsider.id)
        assertEquals(1, log.size())
        assertEquals(project, log[0]["attributes"]["project"].asString())
    }

    @Test
    fun `una lista de proyectos vacia es mis proyectos y el ADMIN exporta todos`() {
        val mine = projectWithCrs()
        stakeholder(mine)
        val other = projectWithCrs()
        stakeholder(other, mapOf("organization" to createRecord("organization", mapOf("name" to "Organización Ajena Ejemplo"))))
        val analyst = member("ANALYST", mine)
        val empty = mapOf("filters" to mapOf("project" to emptyList<String>()), "disclosureLevel" to "INTERNO")
        assertEquals(200, export(empty, analyst.token).status.value())
        val log = logOf(analyst.id)[0]
        assertEquals(1, log["attributes"]["row_count"].asInt())
        assertEquals(mine, log["attributes"]["project"].asString())
        val outsider = userWithRole("ANALYST")
        assertEquals(200, export(empty, outsider.token).status.value())
        assertEquals(0, logOf(outsider.id)[0]["attributes"]["row_count"].asInt())
        // el ADMIN, sin proyectos, recibe los de todos
        assertTrue("Organización Ajena Ejemplo" in cells(export(empty, token).responseBody!!))
    }

    @Test
    fun `el cuerpo y la consulta son estrictos y los errores llevan su codigo`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val path = "/api/sgspe/exports/stakeholder-register"
        val unknown = exchange("POST", path, request(project, "INTERNO", mapOf("extra" to 1)), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status)
        assertEquals(listOf("extra"), problemFields(unknown.body))
        val filter = exchange("POST", path, mapOf("filters" to mapOf("proyecto" to "x"), "disclosureLevel" to "INTERNO"), manager.token)
        assertEquals(listOf("filters.proyecto"), problemFields(filter.body))
        val level = exchange("POST", path, request(project, "SECRETO"), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, level.status)
        assertEquals("INVALID_DISCLOSURE_LEVEL", tree(level.body)["errors"][0]["message"].asString())
        val notUuid = exchange("POST", path, mapOf("filters" to mapOf("project" to listOf("no-es-uuid")), "disclosureLevel" to "INTERNO"), manager.token)
        assertEquals("INVALID_FILTER", tree(notUuid.body)["errors"][0]["message"].asString())
        val query = exchange("POST", "$path?formato=csv", request(project, "INTERNO"), manager.token)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, query.status)
        assertEquals(listOf("formato"), problemFields(query.body))
        val kind = exchange("POST", "/api/sgspe/exports/commitment-matrix", mapOf("disclosureLevel" to "INTERNO"))
        assertEquals("EXPORT_NOT_FOUND", tree(kind.body)["errors"][0]["message"].asString())
    }
}
