package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest

// security.md §14 «Importaciones» (RQ-SEC-020, ASM-050): un archivo demasiado grande y una lectura que pasa el plazo son
// 400 sobre file, cada uno con su código, y no escriben nada. límites bajos solo en este contexto, pero realistas: el
// plazo de 1 s deja pasar una hoja chica y corta una de miles de filas, que hace varias consultas a PostGIS por fila; con
// una sola importación a la vez, la siguiente solo entra si la cortada soltó su permiso, es decir, si la cancelación llegó
// hasta la lectura. la bomba zip se prueba sin plazo en ImportFilesTest
@TestPropertySource(
    properties = ["sgspe.territory.import.max-bytes=65536", "sgspe.territory.import.timeout=1s", "sgspe.territory.import.max-concurrent=1"]
)
class ImportLimitsApiTest : DomainApiTest() {
    private fun preview(
        project: String,
        file: Pair<String, ByteArray>,
        token: String
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to file),
        mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to """{"component_type": "=SONDAJE"}"""),
        token
    )

    private fun codes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    private fun components(project: String): Int =
        tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt()

    @Test
    fun `un archivo mayor que el maximo es 400 sobre file`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val big = "grande.csv" to ("code;easting;northing\n" + (1..5000).joinToString("\n") { "TST-$it;812345;9123456" }).toByteArray()
        assertTrue(big.second.size > 65536)
        val response = preview(project, big, analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("file"), problemFields(response.text))
        assertEquals(listOf("FILE_TOO_LARGE"), codes(response.text))
        assertTrue(response.text.contains("demasiado grande"))
        assertEquals(0, components(project))
    }

    @Test
    fun `una lectura que pasa el tiempo maximo es 400 sobre file y suelta su permiso`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        // 2 800 filas válidas en menos de 64 KB: unas 11 000 consultas a PostGIS, muy por encima de 1 s
        val rows = "pozos.csv" to ("code;easting;northing\n" + (1..2800).joinToString("\n") { "T$it;812345;9123456" }).toByteArray()
        assertTrue(rows.second.size < 65536)
        val response = preview(project, rows, analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.text)
        assertEquals(listOf("file"), problemFields(response.text))
        assertEquals(listOf("IMPORT_TIMEOUT"), codes(response.text))
        assertTrue(response.text.contains("tardó demasiado"), response.text)
        assertEquals(0, components(project))
        // la cortada ya no ocupa el único permiso, y una hoja chica cabe en el plazo: el límite no rechaza todo
        val small = preview(project, "pozo.csv" to "code;easting;northing\nTST-1;812345;9123456".toByteArray(), analyst.token)
        assertEquals(HttpStatus.OK, small.status, small.text)
        assertEquals(1, tree(small.text)["valid"].asInt())
    }
}
