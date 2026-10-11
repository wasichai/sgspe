package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest

// especificación de F6 §3.4: sin GDAL, los formatos de GDAL responden 422 FORMAT_UNAVAILABLE e imports/formats lo dice; los
// de F2 siguen. el servidor de la prueba apunta GDAL a una carpeta que no existe
@TestPropertySource(properties = ["sgspe.territory.gdal.path=/sgspe-sin-gdal"])
class GdalMissingApiTest : DomainApiTest() {
    @Test
    fun `sin GDAL los formatos de F6 no estan disponibles y los de F2 si`() {
        val formats = tree(send("GET", "/api/sgspe/territory/imports/formats", null, HttpStatus.OK))["formats"].values().associateBy { it["format"].asString() }
        assertFalse(formats.getValue("shp")["available"].asBoolean())
        assertTrue(formats.getValue("csv")["available"].asBoolean())
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val gpkg = "capas.gpkg" to "no importa: GDAL no está".toByteArray()
        val response =
            multipart(
                "/api/sgspe/territory/imports",
                mapOf("file" to gpkg),
                mapOf("target" to "land_parcel", "project" to project, "sourceCrs" to "EPSG 32717"),
                analyst.token
            )
        assertEquals(HttpStatus.valueOf(422), response.status, response.text)
        assertEquals("FORMAT_UNAVAILABLE", tree(response.text)["errors"][0]["message"].asString())
        val csv = "pozos.csv" to "code;easting;northing\nTST-SIN-GDAL;812345;9123456".toByteArray()
        val ok =
            multipart(
                "/api/sgspe/territory/imports",
                mapOf("file" to csv),
                mapOf(
                    "target" to "project_component",
                    "project" to project,
                    "sourceCrs" to "EPSG 32717"
                ),
                analyst.token
            )
        assertEquals(HttpStatus.OK, ok.status, ok.text)
    }
}
