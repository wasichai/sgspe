package sgspe.territory

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture

// gis.md §9 «Importación tipo DOC-03», «CRS de origen», «Confirmación» y «MULTI*» (RQ-GIS-003, RQ-GIS-007, RQ-GIS-008,
// RQ-GIS-010, RQ-GIS-020), con códigos y coordenadas ficticios en UTM 17S (RQ-SEC-018)
class TerritoryImportApiTest : DomainApiTest() {
    // como las manda el portal (Fase 2.3): solo las columnas del archivo; el tipo de componente queda en SONDAJE
    private val columns = """{"code": "Codigo", "easting": "Este", "northing": "Norte", "elevation": "Cota"}"""

    private fun feature(
        properties: String,
        geometry: String
    ) = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":$properties,"geometry":$geometry}]}""".toByteArray()

    private fun csv(vararg rows: String) = ("Codigo;Este;Norte;Cota\n" + rows.joinToString("\n")).toByteArray()

    private fun preview(
        project: String,
        file: Pair<String, ByteArray>,
        token: String,
        target: String = "project_component",
        sourceCrs: String? = "EPSG 32717",
        columns: String? = this.columns
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to file),
        listOfNotNull("target" to target, "project" to project, sourceCrs?.let { "sourceCrs" to it }, columns?.let { "columns" to it }).toMap(),
        token
    )

    private fun confirm(
        preview: JsonNode,
        project: String,
        file: Pair<String, ByteArray>,
        token: String,
        decisions: String = "[]",
        target: String = "project_component",
        sha256: String = preview["sha256"].asString()
    ) = multipart(
        "/api/sgspe/territory/imports/${preview["importBatchId"].asString()}/confirm",
        mapOf("file" to file),
        mapOf("target" to target, "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns, "sha256" to sha256, "decisions" to decisions),
        token
    )

    // la confirmación de un GeoJSON en EPSG 4326, sin mapeo de columnas
    private fun confirmGeojson(
        preview: JsonNode,
        project: String,
        file: Pair<String, ByteArray>,
        target: String,
        token: String
    ) = multipart(
        "/api/sgspe/territory/imports/${preview["importBatchId"].asString()}/confirm",
        mapOf("file" to file),
        mapOf("target" to target, "project" to project, "sourceCrs" to "EPSG 4326", "sha256" to preview["sha256"].asString()),
        token
    )

    // los códigos de las guardas y los comandos van en errors[].message (api.md §3.3, P15)
    private fun problemCodes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    @Test
    fun `una hoja tipo DOC-03 se previsualiza, se confirma y una segunda version se concilia`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val first =
            "pozos.csv" to
                csv(
                    "TST-DDH001;812345;9123456;3100",
                    "TST-DDH002;812400;9123500;3105",
                    "TST-DDH003;812450;9123550;3110",
                    "TST-DDH003;812451;9123551;3110",
                    "TST-DDH004;9123456;812345;3100"
                )
        val response = preview(project, first, analyst.token)
        assertEquals(HttpStatus.OK, response.status, response.text)
        val body = tree(response.text)
        assertEquals(5, body["rows"].asInt())
        assertEquals(3, body["valid"].asInt())
        assertEquals(setOf(5, 6), body["invalid"].values().map { it["row"].asInt() }.toSet())
        assertEquals(listOf("CREATE", "CREATE", "CREATE"), body["differences"].values().map { it["outcome"].asString() })
        assertTrue(body["features"] == null)
        val done = tree(confirm(body, project, first, analyst.token).text)
        assertEquals(3, done["created"].asInt())
        assertEquals(2, done["rejected"].asInt())
        val component = tree(send("GET", "/api/objects/project_component/records?project=$project&code=TST-DDH001", null, HttpStatus.OK))["content"][0]
        assertEquals("EPSG 32717", component["attributes"]["source_crs"].asString())
        assertEquals(body["importBatchId"].asString(), component["attributes"]["import_batch_id"].asString())
        assertEquals(812345.0, component["attributes"]["source_easting"].asDouble())
        assertEquals("SONDAJE", component["attributes"]["component_type"].asString())

        val second = "pozos-v2.csv" to csv("TST-DDH001;812345;9123456;3100", "TST-DDH002;812400;9123716;3105", "TST-DDH003;812450.004;9123550;3110")
        val diff = tree(preview(project, second, analyst.token).text)
        val outcomes = diff["differences"].values().associate { it["code"].asString() to it }
        assertEquals("UNCHANGED", outcomes.getValue("TST-DDH001")["outcome"].asString())
        assertEquals("NEW_VERSION", outcomes.getValue("TST-DDH002")["outcome"].asString())
        assertEquals(216.0, outcomes.getValue("TST-DDH002")["distanceM"].asDouble(), 0.01)
        assertEquals("UNCHANGED", outcomes.getValue("TST-DDH003")["outcome"].asString())
        val applied = tree(confirm(diff, project, second, analyst.token).text)
        assertEquals(1, applied["newVersions"].asInt())
        assertEquals(2, applied["unchanged"].asInt())
        val versions =
            tree(
                send("GET", "/api/objects/project_component/records?project=$project&code=TST-DDH002&sort=version_number", null, HttpStatus.OK)
            )["content"]
        assertEquals(listOf(1, 2), versions.values().map { it["attributes"]["version_number"].asInt() })
        assertTrue(versions[0]["attributes"]["superseded"].asBoolean())
        assertEquals(versions[0]["id"].asString(), versions[1]["attributes"]["previous_version"].asString())
    }

    @Test
    fun `sin CRS de origen es 400 y otro archivo con el SHA-256 de la vista previa se rechaza`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-DDH001;812345;9123456;3100")
        val missing = preview(project, file, analyst.token, sourceCrs = null)
        assertEquals(HttpStatus.BAD_REQUEST, missing.status)
        assertEquals(listOf("sourceCrs"), problemFields(missing.text))
        val body = tree(preview(project, file, analyst.token).text)
        val other = "pozos.csv" to csv("TST-DDH001;812999;9123999;3100")
        val rejected = confirm(body, project, other, analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, rejected.status)
        assertEquals(listOf("sha256"), problemFields(rejected.text))
        assertEquals(listOf("SHA256_MISMATCH"), problemCodes(rejected.text))
        assertEquals(0, tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `una fila fuera de la zona del CRS declarado solo entra si se acepta explicitamente`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-DDH010;812345;9123456;3100", "TST-DDH011;960000;9123456;3100")
        val body = tree(preview(project, file, analyst.token).text)
        assertEquals(listOf(3), body["outOfZone"].values().map { it["row"].asInt() })
        assertEquals(1, tree(confirm(body, project, file, analyst.token).text)["created"].asInt())
        val again = tree(preview(project, file, analyst.token).text)
        assertEquals(1, tree(confirm(again, project, file, analyst.token, """[{"row": 3, "action": "APPLY"}]""").text)["created"].asInt())
    }

    @Test
    fun `GeoJSON MULTI de una parte entra a un predio como Polygon y una concesion se vincula al proyecto`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val ring = "[[[-78.60,-7.90],[-78.59,-7.90],[-78.59,-7.89],[-78.60,-7.89],[-78.60,-7.90]]]"
        val parcel = "predios.geojson" to feature("""{"code":"TST-PRD-1","tenure_type":"POSESION"}""", """{"type":"MultiPolygon","coordinates":[$ring]}""")
        val parcelPreview = tree(preview(project, parcel, analyst.token, target = "land_parcel", sourceCrs = "EPSG 4326", columns = null).text)
        val parcelDone =
            multipart(
                "/api/sgspe/territory/imports/${parcelPreview["importBatchId"].asString()}/confirm",
                mapOf("file" to parcel),
                mapOf("target" to "land_parcel", "project" to project, "sourceCrs" to "EPSG 4326", "sha256" to parcelPreview["sha256"].asString()),
                analyst.token
            )
        assertEquals(1, tree(parcelDone.text)["created"].asInt(), parcelDone.text)
        val stored = tree(send("GET", "/api/objects/land_parcel/records?code=TST-PRD-1", null, HttpStatus.OK))["content"][0]
        assertEquals("Polygon", stored["geometries"]["boundary"]["type"].asString())
        assertTrue(stored["attributes"]["calculated_area_ha"].asDouble() > 100)
        val concession = "concesiones.geojson" to feature("""{"code":"TST-CON-1","name":"Concesión Ejemplo"}""", """{"type":"Polygon","coordinates":$ring}""")
        val concessionPreview = tree(preview(project, concession, analyst.token, target = "mining_concession", sourceCrs = "EPSG 4326", columns = null).text)
        multipart(
            "/api/sgspe/territory/imports/${concessionPreview["importBatchId"].asString()}/confirm",
            mapOf("file" to concession),
            mapOf("target" to "mining_concession", "project" to project, "sourceCrs" to "EPSG 4326", "sha256" to concessionPreview["sha256"].asString()),
            analyst.token
        )
        val linked = tree(send("GET", "/api/objects/project/records/$project/related/project_concession", null, HttpStatus.OK))
        assertEquals("TST-CON-1", linked["content"][0]["attributes"]["code"].asString())
        assertEquals("MultiPolygon", linked["content"][0]["geometries"]["boundary"]["type"].asString())
    }

    @Test
    fun `una hoja de puntos para predios y un rol sin permiso sobre el destino se rechazan`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val sheet = "pozos.csv" to csv("TST-DDH001;812345;9123456;3100")
        val format = preview(project, sheet, analyst.token, target = "land_parcel")
        assertEquals(listOf("file"), problemFields(format.text))
        assertEquals(listOf("FORMAT_NOT_SUPPORTED"), problemCodes(format.text))
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        assertEquals(HttpStatus.FORBIDDEN, preview(project, sheet, officer.token).status)
        assertEquals(
            HttpStatus.BAD_REQUEST,
            multipart(
                "/api/sgspe/territory/imports",
                mapOf("file" to sheet),
                mapOf(
                    "target" to "project_component",
                    "otro" to "x"
                ),
                analyst.token
            ).status
        )
    }

    // gis.md §4.3 «Validez»: lo que PostGIS no puede transformar ni validar es una fila rechazada con su motivo, nunca un 500
    @Test
    fun `una coordenada absurda y una geometria vacia son filas invalidas y no un 500`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val features =
            listOf(
                """{"type":"Feature","properties":{"code":"TST-ABS-1"},"geometry":{"type":"Point","coordinates":[1e12,9123456]}}""",
                """{"type":"Feature","properties":{"code":"TST-ABS-2"},"geometry":{"type":"Point","coordinates":[]}}""",
                """{"type":"Feature","properties":{"code":"TST-ABS-3"},"geometry":{"type":"Point","coordinates":[812345,9123456]}}"""
            )
        val geojson = "pozos.geojson" to """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}""".toByteArray()
        val response = preview(project, geojson, analyst.token, columns = null)
        assertEquals(HttpStatus.OK, response.status, response.text)
        val body = tree(response.text)
        assertEquals(1, body["valid"].asInt())
        val invalid = body["invalid"].values().associate { it["row"].asInt() to it["message"].asString() }
        assertEquals(setOf(1, 2), invalid.keys, invalid.toString())
        assertTrue(invalid.values.all { it.startsWith("geometría inválida: ") }, invalid.toString())

        val sheet = "pozos.csv" to csv("TST-ABS-4;1000000000000;9123456;3100", "TST-ABS-5;812345;9123456;3100")
        val rows = tree(preview(project, sheet, analyst.token).text)
        assertEquals(listOf(2), rows["invalid"].values().map { it["row"].asInt() })
        assertTrue(rows["invalid"][0]["message"].asString().startsWith("geometría inválida: "), rows.toString())
        val done = confirm(rows, project, sheet, analyst.token)
        assertEquals(HttpStatus.OK, done.status, done.text)
        assertEquals(1, tree(done.text)["created"].asInt())
        assertEquals(1, tree(done.text)["rejected"].asInt())
    }

    // gis.md §4.4: el reporte de diferencias se descarga como XLSX; security.md §11.4: un código de la hoja que empieza con
    // «=», «+», «-» o «@» sale como texto. api.md §3.4: un parámetro de consulta desconocido es 422
    @Test
    fun `el reporte de diferencias en XLSX neutraliza las formulas, un parametro desconocido es 422 y demasiadas partes 400`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("=1+2;812345;9123456;3100", "-TST-9;812345;;3100")
        val response =
            multipart(
                "/api/sgspe/territory/imports",
                mapOf("file" to file),
                mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns),
                analyst.token,
                mapOf("Accept" to ImportController.XLSX)
            )
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertTrue(
            response.headers.contentType
                .toString()
                .startsWith(ImportController.XLSX),
            response.headers.toString()
        )
        XSSFWorkbook(ByteArrayInputStream(response.body)).use { book ->
            assertEquals(
                "'=1+2",
                book
                    .getSheet("Diferencias")
                    .getRow(1)
                    .getCell(1)
                    .stringCellValue
            )
            assertEquals(
                "CREATE",
                book
                    .getSheet("Diferencias")
                    .getRow(1)
                    .getCell(2)
                    .stringCellValue
            )
            assertEquals(
                3.0,
                book
                    .getSheet("Rechazadas")
                    .getRow(1)
                    .getCell(0)
                    .numericCellValue
            )
        }
        val unknown =
            multipart(
                "/api/sgspe/territory/imports?dryRun=true",
                mapOf("file" to file),
                mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns),
                analyst.token
            )
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, unknown.status, unknown.text)
        assertEquals(listOf("dryRun"), problemFields(unknown.text))
        // más partes que spring.webflux.multipart.max-parts (8): un 400 con código, no un 500
        val crowded =
            multipart(
                "/api/sgspe/territory/imports",
                mapOf("file" to file),
                (1..9).associate { "extra$it" to "x" },
                analyst.token
            )
        assertEquals(HttpStatus.BAD_REQUEST, crowded.status, crowded.text)
        assertEquals(listOf("INVALID_MULTIPART"), problemCodes(crowded.text))
    }

    // api.md §4.2: decisions es [{row, action}] con APPLY o DISCARD sobre filas válidas del archivo
    @Test
    fun `una decision invalida es 400 y DISCARD deja la version vigente`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val first = "pozos.csv" to csv("TST-DDH020;812345;9123456;3100")
        assertEquals(1, tree(confirm(tree(preview(project, first, analyst.token).text), project, first, analyst.token).text)["created"].asInt())
        val moved = "pozos-v2.csv" to csv("TST-DDH020;812345;9123556;3100")
        val diff = tree(preview(project, moved, analyst.token).text)
        assertEquals("NEW_VERSION", diff["differences"][0]["outcome"].asString())
        for (decisions in listOf("""[{"row": 9, "action": "APPLY"}]""", """[{"row": 2, "action": "KEEP"}]""", """{"row": 2}""")) {
            val response = confirm(diff, project, moved, analyst.token, decisions)
            assertEquals(HttpStatus.BAD_REQUEST, response.status, decisions)
            assertEquals(listOf("decisions"), problemFields(response.text), decisions)
        }
        val discarded = tree(confirm(diff, project, moved, analyst.token, """[{"row": 2, "action": "DISCARD"}]""").text)
        assertEquals(0, discarded["newVersions"].asInt())
        assertEquals(1, discarded["rejected"].asInt())
        val versions = tree(send("GET", "/api/objects/project_component/records?project=$project&code=TST-DDH020", null, HttpStatus.OK))
        assertEquals(1, versions["totalElements"].asInt())
    }

    // security.md §9.2: la confirmación escribe como quien importa, y ProjectScopeGuard acota cada alta a sus proyectos
    @Test
    fun `quien no esta asignado al proyecto no confirma y no se escribe nada`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val outsider = member("GIS_ANALYST", projectWithCrs())
        val file = "pozos.csv" to csv("TST-DDH030;812345;9123456;3100", "TST-DDH031;812400;9123500;3105")
        val body = tree(preview(project, file, analyst.token).text)
        val response = confirm(body, project, file, outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.text)
        assertEquals(listOf("NOT_ASSIGNED"), problemCodes(response.text))
        assertEquals(0, tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt())
    }

    // api.md §4.2: la vista previa de los componentes comprueba la asignación antes de leer el archivo, como project-components;
    // los predios y las concesiones son del tenant y su vista previa no la exige
    @Test
    fun `la vista previa de componentes sin asignacion al proyecto es 403 NOT_ASSIGNED antes de leer el archivo`() {
        val project = projectWithCrs()
        val outsider = member("GIS_ANALYST", projectWithCrs())
        // un archivo que no se podría leer: si se lo leyera, el error sería otro y no el 403
        val unreadable = "pozos.csv" to "no es\u0000un archivo de coordenadas".toByteArray()
        val response = preview(project, unreadable, outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.text)
        assertEquals(listOf("NOT_ASSIGNED"), problemCodes(response.text))
        assertEquals("project", tree(response.text)["errors"][0]["field"].asString())
        val ring = "[[[-78.60,-7.90],[-78.59,-7.90],[-78.59,-7.89],[-78.60,-7.89],[-78.60,-7.90]]]"
        val parcel = "predios.geojson" to feature("""{"code":"TST-PRD-9","tenure_type":"POSESION"}""", """{"type":"Polygon","coordinates":$ring}""")
        val tenantLevel = preview(project, parcel, outsider.token, target = "land_parcel", sourceCrs = "EPSG 4326", columns = null)
        assertEquals(HttpStatus.OK, tenantLevel.status, tenantLevel.text)
        // una concesión se vincula al proyecto al confirmar: su vista previa también exige la asignación
        val concession =
            "concesiones.geojson" to feature("""{"code":"TST-CON-9","name":"Concesión Ejemplo Nueve"}""", """{"type":"Polygon","coordinates":$ring}""")
        val linked = preview(project, concession, outsider.token, target = "mining_concession", sourceCrs = "EPSG 4326", columns = null)
        assertEquals(HttpStatus.FORBIDDEN, linked.status, linked.text)
        assertEquals(listOf("NOT_ASSIGNED"), problemCodes(linked.text))
    }

    // una concesión no es de un solo proyecto (database-model): la misma ya registrada se vincula al otro, sin duplicarse
    @Test
    fun `una concesion ya registrada se vincula a un segundo proyecto sin duplicarse`() {
        val first = projectWithCrs()
        val second = projectWithCrs()
        val analyst = member("GIS_ANALYST", first)
        assign(analyst, second, "GIS_ANALYST")
        val ring = "[[[-78.50,-7.90],[-78.49,-7.90],[-78.49,-7.89],[-78.50,-7.89],[-78.50,-7.90]]]"
        val file = "concesiones.geojson" to feature("""{"code":"TST-CON-2","name":"Concesión Ejemplo Dos"}""", """{"type":"Polygon","coordinates":$ring}""")
        for (project in listOf(first, second)) {
            val preview = tree(preview(project, file, analyst.token, target = "mining_concession", sourceCrs = "EPSG 4326", columns = null).text)
            val expected = if (project == first) "CREATE" else "UPDATE"
            assertEquals(expected, preview["differences"][0]["outcome"].asString(), preview.toString())
            val done = confirmGeojson(preview, project, file, "mining_concession", analyst.token)
            assertEquals(HttpStatus.OK, done.status, done.text)
        }
        assertEquals(1, tree(send("GET", "/api/objects/mining_concession/records?code=TST-CON-2", null, HttpStatus.OK))["totalElements"].asInt())
        for (project in listOf(first, second)) {
            val linked = tree(send("GET", "/api/objects/project/records/$project/related/project_concession", null, HttpStatus.OK))
            assertEquals(listOf("TST-CON-2"), linked["content"].values().map { it["attributes"]["code"].asString() })
        }
        // una tercera vez ya no cambia nada
        val again = tree(preview(first, file, analyst.token, target = "mining_concession", sourceCrs = "EPSG 4326", columns = null).text)
        assertEquals("UNCHANGED", again["differences"][0]["outcome"].asString())
    }

    // preflight I5: la confirmación escribe con candado por destino; dos confirmaciones del mismo archivo a la vez no
    // duplican el predio: una crea y la otra ve lo creado (409 IMPORT_CONFLICT si ya había analizado, o sin cambio)
    @Test
    fun `dos confirmaciones simultaneas del mismo archivo no duplican el predio`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val ring = "[[[-78.40,-7.90],[-78.39,-7.90],[-78.39,-7.89],[-78.40,-7.89],[-78.40,-7.90]]]"
        val parcel = "predios.geojson" to feature("""{"code":"TST-PRD-20","tenure_type":"COMUNAL"}""", """{"type":"Polygon","coordinates":$ring}""")
        val body = tree(preview(project, parcel, analyst.token, target = "land_parcel", sourceCrs = "EPSG 4326", columns = null).text)
        val responses =
            (1..2)
                .map { CompletableFuture.supplyAsync { confirmGeojson(body, project, parcel, "land_parcel", analyst.token) } }
                .map { it.join() }
        assertTrue(responses.all { it.status == HttpStatus.OK || it.status == HttpStatus.CONFLICT }, responses.map { it.status to it.text }.toString())
        val created = responses.filter { it.status == HttpStatus.OK }.sumOf { tree(it.text)["created"].asInt() }
        assertEquals(1, created, responses.map { it.text }.toString())
        responses.filter { it.status == HttpStatus.CONFLICT }.forEach { assertEquals(listOf("IMPORT_CONFLICT"), problemCodes(it.text)) }
        assertEquals(1, tree(send("GET", "/api/objects/land_parcel/records?code=TST-PRD-20", null, HttpStatus.OK))["totalElements"].asInt())
    }
}
