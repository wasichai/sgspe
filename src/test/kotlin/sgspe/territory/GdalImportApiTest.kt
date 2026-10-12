package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode
import java.io.File

// gis.md §9 y especificación de F6 §5 «GdalImportApiTest»: un fixture sintético por formato, generado con ogr2ogr en la prueba
// (GdalFixtures), en UTM 17S cerca de SyntheticGeo.ORIGIN; elección de capa, CRS detectado como propuesta y ZIP con ../.
// necesita GDAL en el servidor de la prueba (la CI lo instala)
class GdalImportApiTest : DomainApiTest() {
    @TempDir
    lateinit var dir: File

    private fun bytes(file: File) = file.name to file.readBytes()

    private fun preview(
        project: String,
        file: File,
        target: String,
        token: String,
        sourceCrs: String? = null,
        layer: String? = null,
        columns: String? = null
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to bytes(file)),
        listOfNotNull(
            "target" to target,
            "project" to project,
            sourceCrs?.let { "sourceCrs" to it },
            layer?.let { "layer" to it },
            columns?.let {
                "columns" to
                    it
            }
        ).toMap(),
        token
    )

    private fun confirm(
        body: JsonNode,
        project: String,
        file: File,
        target: String,
        token: String,
        sourceCrs: String? = null,
        layer: String? = null,
        columns: String? = null
    ) = multipart(
        "/api/sgspe/territory/imports/${body["importBatchId"].asString()}/confirm",
        mapOf("file" to bytes(file)),
        listOfNotNull(
            "target" to target,
            "project" to project,
            "sha256" to body["sha256"].asString(),
            sourceCrs?.let { "sourceCrs" to it },
            layer?.let { "layer" to it },
            columns?.let { "columns" to it }
        ).toMap(),
        token
    )

    private fun codes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    // dos predios de 100 m en UTM 17S, con códigos únicos de esta prueba
    private fun parcels(prefix: String): String =
        GdalFixtures.collection(
            listOf(
                mapOf("codigo" to "$prefix-1", "tenencia" to "PROPIEDAD") to GdalFixtures.utmSquare(776_000.0, 9_126_000.0, 100.0),
                mapOf("codigo" to "$prefix-2", "tenencia" to "COMUNAL") to GdalFixtures.utmSquare(776_200.0, 9_126_000.0, 100.0)
            )
        )

    private val parcelColumns = """{"code": "codigo", "tenureType": "tenencia"}"""

    @Test
    fun `los formatos del servidor incluyen los de GDAL disponibles`() {
        GdalFixtures.requireGdal()
        val formats = tree(send("GET", "/api/sgspe/territory/imports/formats", null, HttpStatus.OK))["formats"].values().associateBy { it["format"].asString() }
        assertEquals(setOf("xlsx", "csv", "geojson", "shp", "kml", "kmz", "gpkg", "gdb", "gpx"), formats.keys)
        assertTrue(formats.values.all { it["available"].asBoolean() })
        assertTrue(formats.getValue("gpkg")["multiLayer"].asBoolean())
        assertTrue(formats.getValue("shp")["crsFromFile"].asBoolean())
        assertEquals(false, formats.getValue("kml")["crsFromFile"].asBoolean())
    }

    @Test
    fun `un Shapefile con prj UTM 17S propone su CRS, lo exige y se confirma`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val prefix = uniqueName("SHP")
        val zip = GdalFixtures.shapefileZip(dir, "predios", parcels(prefix), 32717)
        val missing = preview(project, zip, "land_parcel", analyst.token, columns = parcelColumns)
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.text)
        assertEquals(listOf("sourceCrs"), problemFields(missing.text))
        assertEquals("EPSG 32717", tree(missing.text)["layers"][0]["detectedCrs"].asString())
        val response = preview(project, zip, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = parcelColumns)
        assertEquals(HttpStatus.OK, response.status, response.text)
        val body = tree(response.text)
        assertEquals("shp", body["format"].asString())
        assertEquals("EPSG 32717", body["detectedCrs"].asString())
        assertEquals(2, body["valid"].asInt())
        val done = confirm(body, project, zip, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = parcelColumns)
        assertEquals(HttpStatus.OK, done.status, done.text)
        assertEquals(2, tree(done.text)["created"].asInt())
        val parcel = tree(send("GET", "/api/objects/land_parcel/records?code=$prefix-1", null, HttpStatus.OK))["content"][0]["attributes"]
        assertEquals("EPSG 32717", parcel["source_crs"].asString())
        assertTrue(parcel["source_reference"].asString().contains("capa predios"), parcel.toString())
        assertEquals(1.0, parcel["calculated_area_ha"].asDouble(), 0.01)
    }

    @Test
    fun `un GeoPackage con dos capas pide elegir con LAYER_REQUIRED`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val gpkg =
            GdalFixtures.geopackage(
                dir,
                "capas",
                linkedMapOf("predios_a" to parcels(uniqueName("GPA")), "predios_b" to parcels(uniqueName("GPB"))),
                32717
            )
        val response = preview(project, gpkg, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = parcelColumns)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.text)
        assertEquals(listOf("LAYER_REQUIRED"), codes(response.text))
        assertEquals(setOf("predios_a", "predios_b"), tree(response.text)["layers"].values().map { it["name"].asString() }.toSet())
        val chosen = preview(project, gpkg, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", layer = "predios_b", columns = parcelColumns)
        assertEquals(HttpStatus.OK, chosen.status, chosen.text)
        assertEquals("predios_b", tree(chosen.text)["layer"].asString())
    }

    @Test
    fun `KMZ y GPX fijan 4326 y otro CRS es 400`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val prefix = uniqueName("KMZ")
        val kml = GdalFixtures.collection(listOf(mapOf("Name" to "$prefix-1", "Description" to "PROPIEDAD") to GdalFixtures.lonLatSquare(-78.50, -7.90, 0.001)))
        val kmz = GdalFixtures.kmz(dir, "predios", kml)
        val columns = """{"code": "Name", "tenureType": "Description"}"""
        val response = preview(project, kmz, "land_parcel", analyst.token, columns = columns)
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals("EPSG 4326", tree(response.text)["sourceCrs"].asString())
        val wrong = preview(project, kmz, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = columns)
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status)
        assertEquals(listOf("sourceCrs"), problemFields(wrong.text))
        val points =
            GdalFixtures.collection(
                listOf(
                    mapOf("name" to uniqueName("DDH")) to GdalFixtures.point(-78.50, -7.90),
                    mapOf("name" to uniqueName("DDH")) to GdalFixtures.point(-78.499, -7.90)
                )
            )
        val gpx = preview(project, GdalFixtures.gpx(dir, "sondajes", points), "project_component", analyst.token, columns = """{"code": "name"}""")
        assertEquals(HttpStatus.OK, gpx.status, gpx.text)
        assertEquals(2, tree(gpx.text)["valid"].asInt())
    }

    @Test
    fun `una FileGDB con concesiones MULTIPOLYGON se previsualiza`() {
        val project = projectWithCrs()
        val features =
            GdalFixtures.collection(
                listOf(mapOf("codigo" to uniqueName("CON"), "nombre" to "Concesión Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_128_000.0, 500.0))
            )
        val zip = GdalFixtures.fileGdbZip(dir, "concesiones", features, 32717)
        val response = preview(project, zip, "mining_concession", token, sourceCrs = "EPSG 32717", columns = """{"code": "codigo", "name": "nombre"}""")
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals("gdb", tree(response.text)["format"].asString())
        assertEquals(1, tree(response.text)["valid"].asInt())
    }

    @Test
    fun `un ZIP con una entrada fuera de la carpeta es 400 sobre file`() {
        GdalFixtures.requireGdal()
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val zip =
            GdalFixtures.zip(
                File(dir, "malo.zip"),
                mapOf(
                    "predios.shp" to byteArrayOf(1),
                    "../../fuera.shx" to byteArrayOf(2),
                    "predios.dbf" to byteArrayOf(3)
                )
            )
        val response = preview(project, zip, "land_parcel", analyst.token, sourceCrs = "EPSG 32717")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.text)
        assertEquals(listOf("file"), problemFields(response.text))
    }

    // especificación de F6 §3.4: comunidades por código desde un GeoPackage (alta, actualización con cambio de área y distancia
    // de Hausdorff, fila sin código rechazada) y desde un KMZ
    @Test
    fun `comunidades por codigo desde un GeoPackage y un KMZ`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val code = uniqueName("COM")
        val first =
            GdalFixtures.collection(
                listOf(
                    mapOf("codigo" to code, "nombre" to "Comunidad Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_126_500.0, 300.0),
                    mapOf("codigo" to null, "nombre" to "Comunidad sin código") to GdalFixtures.utmSquare(777_000.0, 9_126_500.0, 300.0)
                )
            )
        val columns = """{"code": "codigo", "name": "nombre"}"""
        val gpkg = GdalFixtures.geopackage(dir, "comunidades-1", linkedMapOf("comunidades" to first), 32717)
        val body = tree(preview(project, gpkg, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)
        assertEquals(1, body["valid"].asInt())
        assertEquals("code", body["invalid"][0]["field"].asString())
        val created = confirm(body, project, gpkg, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns)
        assertEquals(1, tree(created.text)["created"].asInt(), created.text)
        val community = tree(send("GET", "/api/objects/community/records?code=$code", null, HttpStatus.OK))["content"][0]["attributes"]
        assertEquals(9.0, community["calculated_area_ha"].asDouble(), 0.01)
        assertEquals("EPSG 32717", community["source_crs"].asString())

        val moved =
            GdalFixtures.collection(
                listOf(mapOf("codigo" to code, "nombre" to "Comunidad Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_126_500.0, 400.0))
            )
        val second = GdalFixtures.geopackage(dir, "comunidades-2", linkedMapOf("comunidades" to moved), 32717)
        val diff = tree(preview(project, second, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)
        val update = diff["differences"][0]
        assertEquals("UPDATE", update["outcome"].asString())
        assertEquals(9.0, update["areaHaBefore"].asDouble(), 0.01)
        assertEquals(16.0, update["areaHaAfter"].asDouble(), 0.01)
        assertTrue(update["distanceM"].asDouble() > 100, update.toString())
        assertEquals(1, tree(confirm(diff, project, second, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)["updated"].asInt())

        val kmzCode = uniqueName("COMKMZ")
        val kml =
            GdalFixtures.collection(
                listOf(mapOf("Name" to kmzCode, "Description" to "Comunidad Ejemplo") to GdalFixtures.lonLatSquare(-78.48, -7.88, 0.002))
            )
        val kmz = GdalFixtures.kmz(dir, "comunidades", kml)
        val kmzColumns = """{"code": "Name", "name": "Description"}"""
        val kmzBody = tree(preview(project, kmz, "community", analyst.token, columns = kmzColumns).text)
        assertEquals(1, tree(confirm(kmzBody, project, kmz, "community", analyst.token, columns = kmzColumns).text)["created"].asInt())
    }

    // un elemento ambiental exige su tipo: desde una FileGDB con un valor fijo del formulario; sin tipo, la fila se rechaza
    @Test
    fun `elementos ambientales desde una FileGDB con el tipo fijo`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val code = uniqueName("AMB")
        val features =
            GdalFixtures.collection(
                listOf(mapOf("codigo" to code, "nombre" to "Fuente de agua Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_127_000.0, 200.0))
            )
        val zip = GdalFixtures.fileGdbZip(dir, "elementos", features, 32717)
        val withoutType =
            tree(
                preview(
                    project,
                    zip,
                    "environmental_feature",
                    analyst.token,
                    sourceCrs = "EPSG 32717",
                    columns = """{"code": "codigo", "name": "nombre"}"""
                ).text
            )
        assertEquals("feature_type", withoutType["invalid"][0]["field"].asString())
        val columns = """{"code": "codigo", "name": "nombre", "featureType": "=FUENTE DE AGUA"}"""
        val body = tree(preview(project, zip, "environmental_feature", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)
        assertEquals(1, body["valid"].asInt())
        assertEquals(
            1,
            tree(confirm(body, project, zip, "environmental_feature", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)["created"].asInt()
        )
        val feature = tree(send("GET", "/api/objects/environmental_feature/records?code=$code", null, HttpStatus.OK))["content"][0]
        assertEquals("FUENTE DE AGUA", feature["attributes"]["feature_type"].asString())
        assertEquals("MultiPolygon", feature["geometries"]["boundary"]["type"].asString())
    }
}
