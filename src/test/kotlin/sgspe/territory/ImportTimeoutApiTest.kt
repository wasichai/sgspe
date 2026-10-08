package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest

// security.md §14 «Importaciones» (RQ-SEC-020, ASM-050): una lectura que pasa el plazo es 400 IMPORT_TIMEOUT sobre file y
// no escribe nada. el plazo de este contexto es mínimo (50 ms) y la hoja hace unas 11 000 consultas a PostGIS, así que el
// corte no depende de la latencia de la base en ningún sentido (ImportLimitsApiTest prueba, con un plazo holgado, que una
// hoja chica pasa). con una sola importación a la vez, la segunda vuelve a dar IMPORT_TIMEOUT y no IMPORT_BUSY: solo entra si
// la primera soltó su permiso, es decir, si la cancelación llegó hasta la lectura y el semáforo no perdió el permiso
@TestPropertySource(properties = ["sgspe.territory.import.timeout=50ms", "sgspe.territory.import.max-concurrent=1"])
class ImportTimeoutApiTest : DomainApiTest() {
    private fun codes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    @Test
    fun `una lectura que pasa el tiempo maximo es 400 sobre file y suelta su permiso`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val rows = "pozos.csv" to ("code;easting;northing\n" + (1..2800).joinToString("\n") { "T$it;812345;9123456" }).toByteArray()
        repeat(2) {
            val response =
                multipart(
                    "/api/sgspe/territory/imports",
                    mapOf("file" to rows),
                    mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717"),
                    analyst.token
                )
            assertEquals(HttpStatus.BAD_REQUEST, response.status, response.text)
            assertEquals(listOf("file"), problemFields(response.text))
            assertEquals(listOf("IMPORT_TIMEOUT"), codes(response.text))
            assertTrue(response.text.contains("tardó demasiado"), response.text)
        }
        assertEquals(0, tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt())
    }
}
