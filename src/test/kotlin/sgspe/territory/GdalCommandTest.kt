package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ValidationException
import java.nio.file.Path

// ASM-127 y especificación de F6 §3.4: GDAL con argumentos fijos más rutas del workspace, sin red y con entorno limpio; nada
// del cliente salvo el nombre de una capa que ya listó ogrinfo
class GdalCommandTest {
    private val ogrinfo = Path.of("/opt/gdal/bin/ogrinfo")
    private val ogr2ogr = Path.of("/opt/gdal/bin/ogr2ogr")
    private val workspace = Path.of("/tmp/sgspe-import-1")
    private val json = JsonMapper.builder().build()

    @Test
    fun `ogrinfo lista las capas con el driver de la lista blanca y la ruta del workspace`() {
        assertEquals(
            listOf("/opt/gdal/bin/ogrinfo", "-json", "-so", "-if", "GPKG", "/tmp/sgspe-import-1/upload-capas.gpkg"),
            GdalCommand.info(ogrinfo, "GPKG", workspace.resolve("upload-capas.gpkg"))
        )
    }

    @Test
    fun `ogr2ogr saca GeoJSON sin reproyectar, en 2D y con tope de elementos`() {
        val args =
            GdalCommand.toGeoJson(
                ogr2ogr,
                "ESRI Shapefile",
                workspace.resolve("archivo/predios.shp"),
                "predios",
                workspace.resolve("gdal-1.geojson"),
                10_001
            )
        assertEquals(
            listOf(
                "/opt/gdal/bin/ogr2ogr",
                "-f",
                "GeoJSON",
                "-if",
                "ESRI Shapefile",
                "-dim",
                "XY",
                "-limit",
                "10001",
                "-lco",
                "RFC7946=NO",
                "/tmp/sgspe-import-1/gdal-1.geojson",
                "/tmp/sgspe-import-1/archivo/predios.shp",
                "predios"
            ),
            args
        )
        assertFalse(args.any { it == "-t_srs" || it == "-s_srs" || it.startsWith("/vsi") || it.startsWith("http") })
    }

    @Test
    fun `una capa que GDAL leeria como opcion o vacia es 400 sobre layer`() {
        for (bad in listOf("-sql", "", "--config")) {
            val e =
                assertThrows<ValidationException> {
                    GdalCommand.toGeoJson(ogr2ogr, "GPKG", workspace.resolve("a.gpkg"), bad, workspace.resolve("b.geojson"), 10)
                }
            assertEquals("layer", e.violations.single().field, bad)
        }
    }

    @Test
    fun `una ruta relativa no llega a GDAL`() {
        assertThrows<IllegalArgumentException> { GdalCommand.info(ogrinfo, "GPKG", Path.of("relativa.gpkg")) }
    }

    @Test
    fun `la escritura reproyecta solo si se pide y con las opciones fijas del formato`() {
        val source = workspace.resolve("capa.geojson")
        assertEquals(
            listOf(
                "/opt/gdal/bin/ogr2ogr",
                "-f",
                "GPKG",
                "-if",
                "GeoJSON",
                "-nln",
                "land_parcel",
                "-t_srs",
                "EPSG:32717",
                "/tmp/sgspe-import-1/capa.gpkg",
                source.toString()
            ),
            GdalCommand.fromGeoJson(ogr2ogr, "GPKG", source, workspace.resolve("capa.gpkg"), "land_parcel", 32717, emptyList())
        )
        val shp = GdalCommand.fromGeoJson(ogr2ogr, "ESRI Shapefile", source, workspace.resolve("capa.shp"), "land_parcel", null, listOf("ENCODING=UTF-8"))
        assertTrue(shp.containsAll(listOf("-lco", "ENCODING=UTF-8")))
        assertFalse(shp.contains("-t_srs"))
    }

    @Test
    fun `el entorno empieza vacio, corta la red y solo deja pasar los datos de GDAL y PROJ`() {
        val server =
            mapOf(
                "HOME" to "/home/sgspe",
                "PATH" to "/usr/bin",
                "GDAL_HTTP_PROXY" to "http://proxy.sgspe.test",
                "AWS_SECRET_ACCESS_KEY" to "secreto",
                "GDAL_DATA" to "/usr/share/gdal",
                "PROJ_DATA" to "/usr/share/proj",
                "CPL_VSIL_CURL_ALLOWED_EXTENSIONS" to ".tif"
            )
        assertEquals(
            mapOf(
                "GDAL_DATA" to "/usr/share/gdal",
                "PROJ_DATA" to "/usr/share/proj",
                "CPL_VSIL_CURL_ALLOWED_EXTENSIONS" to "",
                "GDAL_DISABLE_READDIR_ON_OPEN" to "EMPTY_DIR",
                "GDAL_PAM_ENABLED" to "NO",
                "CPL_DEBUG" to "OFF"
            ),
            GdalCommand.environment(server)
        )
    }

    @Test
    fun `la version sale de las dos formas de ogrinfo --version y se compara con el minimo`() {
        assertEquals(3 to 8, GdalCommand.version("GDAL 3.8.4, released 2024/02/08"))
        assertEquals(3 to 13, GdalCommand.version("GDAL 3.13.3 \"Iowa City\", released 2026/08/13"))
        assertNull(GdalCommand.version("ogrinfo: command not found"))
        assertTrue(GdalCommand.atLeast(3 to 8, "3.8"))
        assertTrue(GdalCommand.atLeast(3 to 13, "3.8"))
        assertTrue(GdalCommand.atLeast(4 to 0, "3.8"))
        assertFalse(GdalCommand.atLeast(3 to 6, "3.8"))
    }

    @Test
    fun `los drivers vectoriales salen de ogrinfo --formats`() {
        val output =
            """
            Supported Formats: (ro:read-only, rw:read-write, +:write from scratch, u:update, v:virtual-I/O s:subdatasets)
              ESRI Shapefile -vector- (rw+uv): ESRI Shapefile (*.shp, *.dbf, *.shz, *.shp.zip)
              GPKG -raster,vector- (rw+uvs): GeoPackage (*.gpkg, *.gpkg.zip)
              LIBKML -vector- (rw+uv): Keyhole Markup Language (LIBKML) (*.kml, *.kmz)
              GTiff -raster- (rw+uvs): GeoTIFF (*.tif, *.tiff)
            """.trimIndent()
        assertEquals(setOf("ESRI Shapefile", "GPKG", "LIBKML"), GdalCommand.drivers(output))
    }

    @Test
    fun `las capas de ogrinfo -json con su tipo, su cantidad y el CRS detectado`() {
        val output =
            """{"description":"capas.gpkg","driverShortName":"GPKG","layers":[
              {"name":"comunidades","geometryFields":[{"name":"geom","type":"Polygon","coordinateSystem":
                {"wkt":"PROJCRS[\"WGS 84 / UTM zone 17S\",ID[\"EPSG\",32717]]","projjson":{"type":"ProjectedCRS","id":{"authority":"EPSG","code":32717}}}}],
               "featureCount":2,"fields":[]},
              {"name":"vacia","geometryFields":[{"name":"geom","type":"Point","coordinateSystem":{"wkt":""}}],"featureCount":0},
              {"name":"sin_geometria","featureCount":3}]}"""
        assertEquals(
            listOf(
                SourceLayer("comunidades", "Polygon", 2, "EPSG 32717"),
                SourceLayer("vacia", "Point", 0, null),
                SourceLayer("sin_geometria", null, 3, null)
            ),
            GdalInfo.layers(output)
        )
    }

    @Test
    fun `layers en el JSON solo con nombre, tipo, cantidad y CRS detectado`() {
        val layer = SourceLayer("comunidades", "Polygon", 2, "EPSG 32717", fieldCount = 7, fieldNameBytes = 120)
        val tree = json.readTree(json.writeValueAsString(layer))
        assertEquals(setOf("name", "geometryType", "featureCount", "detectedCrs"), tree.propertyNames().toSet())
    }

    @Test
    fun `el CRS detectado sale de projjson o del ID final del WKT, y fuera del catalogo es null`() {
        fun epsg(cs: String) = GdalInfo.epsg(json.readTree(cs))
        assertEquals(32718, epsg("""{"wkt":"PROJCRS[\"WGS 84 / UTM zone 18S\",BASEGEOGCRS[\"WGS 84\",ID[\"EPSG\",4326]],ID[\"EPSG\",32718]]"}"""))
        // WKT de ESRI, sin ID: no se adivina
        assertNull(epsg("""{"wkt":"PROJCS[\"WGS_1984_UTM_Zone_17S\",GEOGCS[\"GCS_WGS_1984\"]]"}"""))
        // solo el CRS base tiene ID: no es el del archivo
        assertNull(epsg("""{"wkt":"PROJCRS[\"x\",BASEGEOGCRS[\"WGS 84\",ID[\"EPSG\",4326]],CONVERSION[\"y\"]]"}"""))
        val webMercator =
            """{"layers":[{"name":"m","geometryFields":[{"type":"Point",""" +
                """"coordinateSystem":{"projjson":{"id":{"authority":"EPSG","code":3857}}}}]}]}"""
        assertNull(GdalInfo.layers(webMercator).single().detectedCrs)
        val psad = """{"layers":[{"name":"p","geometryFields":[{"type":"Point","coordinateSystem":{"projjson":{"id":{"authority":"EPSG","code":24877}}}}]}]}"""
        assertEquals("EPSG 24877", GdalInfo.layers(psad).single().detectedCrs)
    }
}
