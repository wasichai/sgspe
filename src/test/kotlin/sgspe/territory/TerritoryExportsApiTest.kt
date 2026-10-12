package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import sgspe.F6ApiTest
import sgspe.SyntheticGeo
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

// especificación de F6 §5 «TerritoryExportsApiTest» (RQ-GIS-018, RQ-REP-022, RQ-AUD-011): cada formato abre y trae los
// registros; predios en EXTERNO sin partida; sondajes en EXTERNO 403; sin EXPORT 403; la fila de export_log. los predios son
// del tenant y la base es compartida: cada prueba busca sus códigos dentro del archivo
class TerritoryExportsApiTest : F6ApiTest() {
    @TempDir
    lateinit var dir: File

    private class Download(
        val status: HttpStatus,
        val body: ByteArray,
        val headers: HttpHeaders
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    private fun export(
        body: Map<String, Any?>,
        token: String
    ): Download {
        val result =
            client
                .post()
                .uri("/api/sgspe/territory/exports")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(body)
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return Download(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun code(download: Download) = tree(download.text)["errors"][0]["message"].asString()

    private fun saved(
        download: Download,
        name: String
    ): File = File(dir, name).apply { writeBytes(download.body) }

    // dos predios sintéticos (uno PROPIEDAD con partida, otro de la empresa) y un SOCIAL_MANAGER asignado al proyecto
    private class Setup(
        val projectId: UUID,
        val project: String,
        val manager: Account,
        val owned: String,
        val company: String
    )

    private fun setup(): Setup =
        runBlocking {
            val projectId = utmProject()
            val project = projectId.toString()
            val manager = member("SOCIAL_MANAGER", project)
            val (lon, lat) = SyntheticGeo.ORIGIN
            val owned = uniqueName("PRD-EXP")
            val company = uniqueName("PRD-EMP")
            // con partida registral (ficticia), por la API genérica: la ayuda parcel de F6ApiTest no la escribe
            createWith(
                "land_parcel",
                mapOf("code" to owned, "tenure_type" to "PROPIEDAD", "registry_record" to "P-0001-EJEMPLO"),
                mapOf("boundary" to SyntheticGeo.square(lon, lat, 100.0))
            )
            parcel(company, "PROPIEDAD DE LA EMPRESA", SyntheticGeo.square(lon + 0.01, lat, 100.0))
            Setup(projectId, project, manager, owned, company)
        }

    private fun body(
        objectName: String,
        format: String,
        level: String,
        filters: Map<String, Any?> = emptyMap(),
        coordinates: String = "LATLON"
    ) = mapOf(
        "object" to objectName,
        "format" to format,
        "coordinates" to coordinates,
        "filters" to filters,
        "disclosureLevel" to level,
        "checklistConfirmed" to (level == "EXTERNO")
    )

    @Test
    fun `GeoJSON INTERNO trae los predios con su partida y CSV EXTERNO sin partida ni predios de la empresa`() {
        val s = setup()
        val internal = export(body("land_parcel", "geojson", "INTERNO"), s.manager.token)
        assertEquals(HttpStatus.OK, internal.status, internal.text)
        assertTrue(
            internal.headers.contentDisposition.filename!!
                .endsWith(".geojson")
        )
        val features = tree(internal.text)["features"].values().associateBy { it["properties"]["code"].asString() }
        assertEquals("P-0001-EJEMPLO", features.getValue(s.owned)["properties"]["registry_record"].asString())
        assertTrue(s.company in features)

        val external = export(body("land_parcel", "csv", "EXTERNO"), s.manager.token)
        assertEquals(HttpStatus.OK, external.status, external.text)
        val lines = external.text.removePrefix("\uFEFF").lines()
        val header = lines.first().split(",")
        assertFalse("registry_record" in header)
        assertTrue(header.containsAll(listOf("latitude", "longitude", "utm_zone", "easting", "northing", "wkt")))
        val mine = lines.first { it.contains(s.owned) }
        assertTrue(mine.contains("EPSG 32717"), mine)
        assertTrue(mine.contains("POLYGON"), mine)
        assertFalse(lines.any { it.contains(s.company) })
    }

    @Test
    fun `XLSX, KML, GeoPackage y Shapefile abren y traen los registros`() {
        val s = setup()
        val filters = mapOf("project" to s.project)
        val xlsx = export(body("land_parcel", "xlsx", "INTERNO", filters, coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.OK, xlsx.status, xlsx.text)
        XSSFWorkbook(ByteArrayInputStream(xlsx.body)).use { book ->
            val sheet = book.getSheetAt(0)
            val cells =
                (0..sheet.lastRowNum).flatMap { r ->
                    sheet.getRow(r)?.let { row -> (0 until row.lastCellNum).mapNotNull { row.getCell(it)?.toString() } }.orEmpty()
                }
            assertTrue(s.owned in cells)
            assertTrue("easting" in cells && "wkt" in cells)
        }
        val kml = export(body("land_parcel", "kml", "INTERNO"), s.manager.token)
        assertEquals(HttpStatus.OK, kml.status, kml.text)
        assertTrue(GdalFixtures.info(saved(kml, "predios.kml"), "KML").single().featureCount!! >= 2)
        val gpkg = export(body("land_parcel", "gpkg", "INTERNO", filters, coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.OK, gpkg.status, gpkg.text)
        assertEquals("EPSG 32717", GdalFixtures.info(saved(gpkg, "predios.gpkg"), "GPKG").single().detectedCrs)
        val shp = export(body("land_parcel", "shp", "INTERNO", filters, coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.OK, shp.status, shp.text)
        val names =
            ZipFile(saved(shp, "predios.zip")).use { zip ->
                zip
                    .entries()
                    .toList()
                    .map { it.name }
                    .toSet()
            }
        assertTrue(names.containsAll(setOf("land_parcel.shp", "land_parcel.dbf", "land_parcel.cpg", "land_parcel.prj")), names.toString())
    }

    @Test
    fun `componentes en EXTERNO sin filtro solo accesos, y sondajes 403`() {
        val s = setup()
        val (lon, lat) = SyntheticGeo.ORIGIN
        val access = uniqueName("ACC")
        val drill = uniqueName("DDH")
        runBlocking {
            component(s.projectId, access, "ACCESO", SyntheticGeo.line(lon to lat, lon + 0.002 to lat))
            // un sondaje con traza (una perforación inclinada dibujada como línea): sale por la misma geometría que el acceso,
            // así que solo el filtro fijo de EXTERNO lo deja fuera
            component(s.projectId, drill, "SONDAJE", SyntheticGeo.line(lon to lat + 0.001, lon + 0.002 to lat + 0.001))
        }
        val filters = mapOf("project" to s.project)
        val external = export(body("project_component", "geojson", "EXTERNO", filters) + ("geometry" to "trace"), s.manager.token)
        assertEquals(HttpStatus.OK, external.status, external.text)
        val codes = tree(external.text)["features"].values().map { it["properties"]["code"].asString() }
        assertEquals(listOf(access), codes)
        val drills =
            export(body("project_component", "geojson", "EXTERNO", filters + ("componentType" to listOf("SONDAJE"))) + ("geometry" to "trace"), s.manager.token)
        val internal = export(body("project_component", "geojson", "INTERNO", filters) + ("geometry" to "trace"), s.manager.token)
        assertEquals(setOf(access, drill), tree(internal.text)["features"].values().map { it["properties"]["code"].asString() }.toSet())
        assertEquals(HttpStatus.FORBIDDEN, drills.status)
        assertEquals("DISCLOSURE_NOT_ALLOWED", code(drills))
    }

    @Test
    fun `sin EXPORT es 403, un objeto fuera de las capas es 400 y PUBLICO de predios es 403`() {
        val s = setup()
        // STAKEHOLDER_MANAGER lee predios y crea export_log, pero no tiene EXPORT sobre land_parcel (roles.json de 6.1)
        val reader = member("STAKEHOLDER_MANAGER", s.project)
        assertEquals(HttpStatus.FORBIDDEN, export(body("land_parcel", "geojson", "INTERNO"), reader.token).status)
        val rights = export(body("land_right", "geojson", "INTERNO"), s.manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, rights.status)
        assertEquals("OBJECT_NOT_EXPORTABLE", code(rights))
        assertEquals("DISCLOSURE_NOT_ALLOWED", code(export(body("land_parcel", "geojson", "PUBLICO"), s.manager.token)))
        val unchecked = export(body("land_parcel", "geojson", "EXTERNO") + ("checklistConfirmed" to false), s.manager.token)
        assertEquals("CHECKLIST_REQUIRED", code(unchecked))
        val utmWithout = export(body("land_parcel", "gpkg", "INTERNO", coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, utmWithout.status)
        assertEquals(listOf("filters.project"), problemFields(utmWithout.text))
    }

    @Test
    fun `cada exportacion deja su fila en export_log`() {
        val s = setup()
        assertEquals(HttpStatus.OK, export(body("land_parcel", "kml", "INTERNO", mapOf("project" to s.project)), s.manager.token).status)
        val logs =
            tree(send("GET", "/api/objects/export_log/records?exporter_user_email=${s.manager.email}&object_name=land_parcel", null, HttpStatus.OK))["content"]
        val row = logs.values().single()["attributes"]
        assertEquals("KML", row["file_format"].asString())
        assertEquals("INTERNO", row["disclosure_level"].asString())
        assertTrue(row["row_count"].asInt() >= 2)
        assertTrue(row["filters_description"].asString().contains("geometría=boundary"), row.toString())
    }
}
