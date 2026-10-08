package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode

// GET /api/sgspe/territory/layers/{layer} en F2 (RQ-GIS-001, RQ-GIS-012): las capas básicas como GeoJSON con los permisos
// de quien mira, acotadas por proyecto y bbox; la versión vigente de los componentes; 404 una capa de F6; 422 un parámetro
// desconocido; 403 sin READ sobre el objeto de la capa; y ningún campo restringido en las propiedades de un elemento
class TerritoryLayersApiTest : DomainApiTest() {
    @Test
    fun `LYR-10 trae los sondajes vigentes del proyecto y bbox acota`() {
        val project = projectWithCrs()
        val other = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        assign(analyst, other, "GIS_ANALYST")

        fun drill(
            project: String,
            code: String,
            location: Map<String, Any?>
        ) = send(
            "POST",
            "/api/sgspe/project-components",
            mapOf("attributes" to mapOf("project" to project, "code" to code, "component_type" to "SONDAJE"), "geometries" to mapOf("location" to location)),
            HttpStatus.CREATED,
            analyst.token
        )
        drill(project, "TST-DDH1", point(-78.2, -7.9))
        drill(project, "TST-DDH1", point(-78.2, -7.899))
        drill(project, "TST-DDH2", point(-75.0, -12.0))
        drill(other, "TST-DDH9", point(-78.2, -7.9))
        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-10?project=$project", null, HttpStatus.OK, analyst.token))
        assertEquals("FeatureCollection", layer["type"].asString())
        val codes = layer["features"].values().map { it["properties"]["code"].asString() to it["properties"]["version_number"].asInt() }
        assertEquals(setOf("TST-DDH1" to 2, "TST-DDH2" to 1), codes.toSet())
        val boxed = tree(send("GET", "/api/sgspe/territory/layers/LYR-10?project=$project&bbox=-78.3,-8.0,-78.1,-7.8", null, HttpStatus.OK, analyst.token))
        assertEquals(listOf("TST-DDH1"), boxed["features"].values().map { it["properties"]["code"].asString() })
    }

    @Test
    fun `una capa de F6 es 404, un parametro desconocido 422 y sin READ 403`() {
        val project = projectWithCrs()
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/territory/layers/LYR-17").status)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/territory/layers/LYR-01?proyecto=$project").status)
        val projectAdmin = member("PROJECT_ADMIN", project)
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/territory/layers/LYR-16", token = projectAdmin.token).status)
    }

    @Test
    fun `quien no lee la partida registral ni a los titulares no los recibe en las propiedades del elemento`() {
        val code = uniqueName("TST-PRD-")
        val parcel =
            createWith(
                "land_parcel",
                mapOf("code" to code, "tenure_type" to "PROPIEDAD", "registry_record" to "TST-PARTIDA-001"),
                mapOf("boundary" to square(-71.5, -14.5, 0.01))
            )
        createRecord("land_right", mapOf("land_parcel" to parcel, "holder_person" to person("Persona Ejemplo")))
        val path = "/api/sgspe/territory/layers/LYR-14?bbox=-71.6,-14.6,-71.4,-14.4"

        fun feature(token: String): Pair<String, JsonNode> {
            val body = send("GET", path, null, HttpStatus.OK, token)
            val found = tree(body)["features"].values().filter { it["properties"]["__id"].asString() == parcel }
            assertEquals(1, found.size, body)
            return body to found.single()["properties"]
        }

        val (_, admin) = feature(token)
        assertEquals("TST-PARTIDA-001", admin["registry_record"].asString())
        assertEquals("land_parcel", admin["__object"].asString())
        assertEquals("LYR-14", admin["__layer"].asString())
        assertEquals("TST-PARTIDA-001", feature(userWithRole("GIS_ANALYST").token).second["registry_record"].asString())
        for (role in listOf("ANALYST", "READ_ONLY")) {
            val (body, properties) = feature(userWithRole(role).token)
            assertEquals(code, properties["code"].asString(), role)
            assertFalse(properties.has("registry_record"), "$role: $body")
            assertFalse(properties.has("holder_person"), "$role: $body")
            assertFalse(body.contains("TST-PARTIDA-001") || body.contains("Persona Ejemplo"), "$role: $body")
        }
    }

    @Test
    fun `periodStart y periodEnd se aceptan, un bbox o un proyecto mal escritos son 400 y LYR-16 junta escuelas y establecimientos`() {
        val ok = exchange("GET", "/api/sgspe/territory/layers/LYR-01?periodStart=2026-01-01&periodEnd=2026-12-31")
        assertEquals(HttpStatus.OK, ok.status, ok.body)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/territory/layers/LYR-01?bbox=1,2").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/territory/layers/LYR-01?project=no-es-un-uuid").status)

        val school = createWith("school", sample("school"), mapOf("location" to point(-70.0, -10.0)))
        val clinic = createWith("health_facility", sample("health_facility"), mapOf("location" to point(-70.001, -10.001)))
        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-16?bbox=-70.1,-10.1,-69.9,-9.9", null, HttpStatus.OK))
        val features = layer["features"].values().associateBy { it["id"].asString() }
        assertTrue(features.keys.containsAll(listOf("$school:location", "$clinic:location")), features.keys.toString())
        assertEquals("school", features.getValue("$school:location")["properties"]["__object"].asString())
        assertEquals("health_facility", features.getValue("$clinic:location")["properties"]["__object"].asString())
        assertEquals("LYR-16", features.getValue("$clinic:location")["properties"]["__layer"].asString())
        assertEquals("Point", features.getValue("$school:location")["geometry"]["type"].asString())

        // un mapa alejado manda longitudes sin envolver (P26): el servidor las recorta y no es un 400
        val wide = tree(send("GET", "/api/sgspe/territory/layers/LYR-16?bbox=-425,-20,275,10", null, HttpStatus.OK))
        assertTrue(wide["features"].values().any { it["id"].asString() == "$school:location" })
    }
}
