package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest

// security.md §14 «Importaciones» (RQ-SEC-020, ASM-050): un archivo demasiado grande es 400 FILE_TOO_LARGE sobre file y no
// escribe nada, y uno chico pasa con los mismos límites. el plazo es holgado en este contexto (30 s), así que ninguna
// respuesta depende de la latencia de la base; el plazo vencido se prueba en ImportTimeoutApiTest, con uno mínimo, y la
// bomba zip, sin plazo, en ImportFilesTest
@TestPropertySource(
    properties = ["sgspe.territory.import.max-bytes=65536", "sgspe.territory.import.timeout=30s", "sgspe.territory.import.max-concurrent=1"]
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
    fun `un archivo dentro de los limites se previsualiza`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val rows = "pozos.csv" to ("code;easting;northing\n" + (1..20).joinToString("\n") { "TST-$it;812345;9123456" }).toByteArray()
        val response = preview(project, rows, analyst.token)
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals(20, tree(response.text)["valid"].asInt())
    }
}
