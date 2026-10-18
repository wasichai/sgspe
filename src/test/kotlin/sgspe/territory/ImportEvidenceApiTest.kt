package sgspe.territory

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode

// ASM-060 y RQ-GIS-008 (api.md §4.2, gis.md §4.5): la confirmación guarda el archivo original como evidence, creada como quien
// confirma, con el import_batch_id del lote, en la misma transacción que los registros. la vista previa no crea ninguna; una
// confirmación repetida no crea otra; un rol sin CREATE sobre evidence no importa. códigos y coordenadas ficticios en UTM 17S
class ImportEvidenceApiTest : DomainApiTest() {
    private val columns = """{"code": "Codigo", "easting": "Este", "northing": "Norte", "elevation": "Cota"}"""

    private fun csv(vararg rows: String) = ("Codigo;Este;Norte;Cota\n" + rows.joinToString("\n")).toByteArray()

    private fun preview(
        project: String,
        file: Pair<String, ByteArray>,
        token: String
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to file),
        mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns),
        token
    )

    private fun confirm(
        batch: String,
        sha256: String,
        project: String,
        file: Pair<String, ByteArray>,
        token: String
    ) = multipart(
        "/api/sgspe/territory/imports/$batch/confirm",
        mapOf("file" to file),
        mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns, "sha256" to sha256),
        token
    )

    private fun confirm(
        preview: JsonNode,
        project: String,
        file: Pair<String, ByteArray>,
        token: String
    ) = confirm(preview["importBatchId"].asString(), preview["sha256"].asString(), project, file, token)

    private fun evidences(batch: String): List<JsonNode> =
        tree(send("GET", "/api/objects/evidence/records?import_batch_id=$batch", null, HttpStatus.OK))["content"].values().toList()

    private fun components(project: String): Int =
        tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt()

    private fun codes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    private fun download(id: String): ByteArray =
        client
            .get()
            .uri("/api/sgspe/evidence/$id/content")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(ByteArray::class.java)
            .returnResult()
            .responseBody!!

    @Test
    fun `la confirmacion guarda el original como evidence del lote y la vista previa no`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-EVI-1;812345;9123456;3100", "TST-EVI-2;812400;9123500;3105")
        val body = tree(preview(project, file, analyst.token).text)
        val batch = body["importBatchId"].asString()
        assertEquals(emptyList<JsonNode>(), evidences(batch))

        val done = confirm(body, project, file, analyst.token)
        assertEquals(HttpStatus.OK, done.status, done.text)
        val result = tree(done.text)
        assertEquals(2, result["created"].asInt())
        val id = result["evidenceId"].asString()
        val stored = evidences(batch)
        assertEquals(listOf(id), stored.map { it["id"].asString() })
        val evidence = record("evidence", id)["attributes"]
        assertEquals(batch, evidence["import_batch_id"].asString())
        assertEquals("MAPA", evidence["evidence_type"].asString())
        assertEquals("INTERNA", evidence["classification"].asString())
        assertEquals(project, evidence["project"].asString())
        assertTrue(evidence["title"].asString().startsWith("Importación de componentes del "), evidence.toString())
        assertFalse(evidence["title"].asString().contains("pozos"), evidence.toString())
        assertTrue(evidence["document_date"].asString().isNotBlank())
        assertEquals(body["sha256"].asString(), evidence["file"]["sha256"].asString())
        assertEquals("text/csv", evidence["file"]["contentType"].asString())
        assertEquals("pozos.csv", evidence["file"]["name"].asString())
        assertArrayEquals(file.second, download(id))
        // los registros y la evidencia comparten el lote
        val component = tree(send("GET", "/api/objects/project_component/records?project=$project&code=TST-EVI-1", null, HttpStatus.OK))["content"][0]
        assertEquals(batch, component["attributes"]["import_batch_id"].asString())
    }

    @Test
    fun `una confirmacion repetida devuelve la misma evidencia y no crea otra`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-EVI-10;812345;9123456;3100")
        val body = tree(preview(project, file, analyst.token).text)
        val first = tree(confirm(body, project, file, analyst.token).text)
        val again = confirm(body, project, file, analyst.token)
        assertEquals(HttpStatus.OK, again.status, again.text)
        val second = tree(again.text)
        assertEquals(0, second["created"].asInt())
        assertEquals(1, second["unchanged"].asInt())
        assertEquals(first["evidenceId"].asString(), second["evidenceId"].asString())
        assertEquals(1, evidences(body["importBatchId"].asString()).size)
    }

    @Test
    fun `un lote ya confirmado no escribe registros nuevos con otro archivo`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-EVI-20;812345;9123456;3100")
        val body = tree(preview(project, file, analyst.token).text)
        assertEquals(1, tree(confirm(body, project, file, analyst.token).text)["created"].asInt())
        val other = "pozos-2.csv" to csv("TST-EVI-21;812400;9123500;3105")
        val otherPreview = tree(preview(project, other, analyst.token).text)
        val reused = confirm(body["importBatchId"].asString(), otherPreview["sha256"].asString(), project, other, analyst.token)
        assertEquals(HttpStatus.CONFLICT, reused.status, reused.text)
        assertEquals(listOf("IMPORT_BATCH_USED"), codes(reused.text))
        assertEquals(1, components(project))
        assertEquals(1, evidences(body["importBatchId"].asString()).size)
    }

    @Test
    fun `sin nada que escribir no hay evidencia y un predio del tenant la deja sin proyecto`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-EVI-30;812345;9123456;3100")
        val body = tree(preview(project, file, analyst.token).text)
        val discarded =
            multipart(
                "/api/sgspe/territory/imports/${body["importBatchId"].asString()}/confirm",
                mapOf("file" to file),
                mapOf(
                    "target" to "project_component",
                    "project" to project,
                    "sourceCrs" to "EPSG 32717",
                    "columns" to columns,
                    "sha256" to body["sha256"].asString(),
                    "decisions" to """[{"row": 2, "action": "DISCARD"}]"""
                ),
                analyst.token
            )
        assertEquals(HttpStatus.OK, discarded.status, discarded.text)
        assertTrue(tree(discarded.text)["evidenceId"].isNull, discarded.text)
        assertEquals(emptyList<JsonNode>(), evidences(body["importBatchId"].asString()))

        val ring = "[[[-78.30,-7.90],[-78.29,-7.90],[-78.29,-7.89],[-78.30,-7.89],[-78.30,-7.90]]]"
        val parcel =
            "predios.geojson" to
                """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"code":"TST-EVI-PRD","tenure_type":"POSESION"},"geometry":{"type":"Polygon","coordinates":$ring}}]}"""
                    .toByteArray()
        val fields = mapOf("target" to "land_parcel", "project" to project, "sourceCrs" to "EPSG 4326")
        val parcelPreview = tree(multipart("/api/sgspe/territory/imports", mapOf("file" to parcel), fields, analyst.token).text)
        val done =
            multipart(
                "/api/sgspe/territory/imports/${parcelPreview["importBatchId"].asString()}/confirm",
                mapOf("file" to parcel),
                fields + ("sha256" to parcelPreview["sha256"].asString()),
                analyst.token
            )
        assertEquals(HttpStatus.OK, done.status, done.text)
        val evidence = record("evidence", tree(done.text)["evidenceId"].asString())["attributes"]
        assertTrue(evidence["project"] == null || evidence["project"].isNull, evidence.toString())
        assertEquals("text/plain", evidence["file"]["contentType"].asString())
        assertEquals(parcelPreview["sha256"].asString(), evidence["file"]["sha256"].asString())
        assertTrue(evidence["title"].asString().startsWith("Importación de predios del "), evidence.toString())
        // el original de un padrón de predios puede traer titulares y DNI: DATOS PERSONALES. un agente de campo lee las
        // evidencias pero no tiene DOWNLOAD_RESTRICTED: ve el descriptor sin nombre ni huella y no descarga (RF-DOC-003)
        assertEquals("DATOS PERSONALES", evidence["classification"].asString())
        val id = tree(done.text)["evidenceId"].asString()
        val agent = userWithRole("FIELD_AGENT")
        val seen = record("evidence", id, agent.token)["attributes"]["file"]
        assertTrue(seen["name"] == null && seen["sha256"] == null, seen.toString())
        val denied =
            client
                .get()
                .uri("/api/sgspe/evidence/$id/content")
                .header(HttpHeaders.AUTHORIZATION, agent.token)
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
        assertEquals(HttpStatus.FORBIDDEN.value(), denied.status.value(), denied.responseBody)
        assertEquals(listOf("ACTION_REQUIRED"), codes(denied.responseBody.orEmpty()))
    }

    // un rol de prueba creado por la API (todo rol de roles.json que importa tiene CREATE sobre evidence): importa componentes
    // pero no crea evidencias; la vista previa y la confirmación son 403 antes de leer el archivo y nada se escribe
    @Test
    fun `un rol sin CREATE sobre evidence no importa`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val name = "PRUEBA_" + uniqueName("").uppercase().filter { it.isLetterOrDigit() }
        send("POST", "/api/roles", mapOf("name" to name, "label" to "Rol de prueba"), HttpStatus.CREATED)
        val grants =
            listOf("project" to "READ", "project_component" to "READ", "project_component" to "CREATE", "project_component" to "UPDATE", "evidence" to "READ")
        send(
            "PUT",
            "/api/roles/$name/permissions",
            mapOf("permissions" to grants.map { (objectName, action) -> mapOf("objectName" to objectName, "action" to action) }),
            HttpStatus.OK
        )
        val importer = userWithRole(name).also { assign(it, project, "READ_ONLY") }
        val file = "pozos.csv" to csv("TST-EVI-40;812345;9123456;3100")
        val refused = preview(project, file, importer.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.text)
        assertTrue(refused.text.contains("evidence"), refused.text)
        val body = tree(preview(project, file, analyst.token).text)
        val confirmed = confirm(body, project, file, importer.token)
        assertEquals(HttpStatus.FORBIDDEN, confirmed.status, confirmed.text)
        assertEquals(0, components(project))
        assertEquals(emptyList<JsonNode>(), evidences(body["importBatchId"].asString()))
    }
}
