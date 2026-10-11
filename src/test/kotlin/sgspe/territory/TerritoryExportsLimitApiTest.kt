package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F6ApiTest
import sgspe.SyntheticGeo

// el tope de filas de una exportación (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS sobre filters.project y no
// escribe export_log. con componentes de un proyecto propio, para no depender de lo que otras pruebas dejaron en el tenant
@TestPropertySource(properties = ["sgspe.exports.max-rows=3"])
class TerritoryExportsLimitApiTest : F6ApiTest() {
    @Test
    fun `mas filas que el tope es 400 TOO_MANY_RECORDS`() {
        val projectId = runBlocking { utmProject() }
        val project = projectId.toString()
        val manager = member("SOCIAL_MANAGER", project)
        val (lon, lat) = SyntheticGeo.ORIGIN
        runBlocking { repeat(4) { component(projectId, uniqueName("DDH"), "SONDAJE", SyntheticGeo.point(lon + it * 0.001, lat)) } }
        val body =
            mapOf(
                "object" to "project_component",
                "geometry" to "location",
                "format" to "geojson",
                "coordinates" to "LATLON",
                "filters" to mapOf("project" to project),
                "disclosureLevel" to "INTERNO"
            )
        val response = exchange("POST", "/api/sgspe/territory/exports", body, manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("TOO_MANY_RECORDS", tree(response.body)["errors"][0]["message"].asString())
        assertEquals("filters.project", tree(response.body)["errors"][0]["field"].asString())
        val logs = tree(send("GET", "/api/objects/export_log/records?exporter_user_email=${manager.email}", null, HttpStatus.OK))["totalElements"].asInt()
        assertEquals(0, logs)
    }
}
