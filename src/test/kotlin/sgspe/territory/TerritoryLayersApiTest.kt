package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch
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

    private fun locality(vararg geometries: Pair<String, Map<String, Any?>>): String =
        createWith("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"), geometries.toMap())

    private fun area(
        project: String,
        type: String,
        version: Int,
        boundary: Map<String, Any?>? = null,
        vararg members: Pair<String, String>
    ): String {
        val area =
            createWith(
                "area_of_influence",
                mapOf("project" to project, "area_type" to type, "version_number" to version),
                if (boundary == null) emptyMap() else mapOf("boundary" to boundary)
            )
        members.forEach { (field, target) ->
            createRecord("area_of_influence_member", mapOf("area_of_influence" to area, field to target, "member_condition" to "INCLUIDO"))
        }
        return area
    }

    private fun approve(
        area: String,
        token: String
    ) = send(
        "POST",
        "/api/sgspe/areas-of-influence/$area/approve",
        null,
        HttpStatus.OK,
        token,
        mapOf(IfMatch.HEADER to "\"${record("area_of_influence", area)["updatedAt"].asString()}\"", REASON to reason("aprobación de prueba"))
    )

    private fun featuresOf(
        layer: JsonNode,
        objectName: String
    ): List<JsonNode> = layer["features"].values().filter { it["properties"]["__object"].asString() == objectName }

    @Test
    fun `LYR-13 trae el poligono y los miembros solo de las versiones aprobadas`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val withBoundary = locality("boundary" to square(-70.0, -10.0, 0.02), "location" to point(-69.99, -9.99))
        val onlyPoint = locality("location" to point(-60.0, -5.0))
        val noGeometry = locality()
        val community = createWith("community", mapOf("name" to "Comunidad Ejemplo"), mapOf("territory" to square(-65.0, -8.0, 0.05, "MultiPolygon")))
        val stale = locality("boundary" to square(-55.0, -4.0, 0.02))
        val draftOnly = locality("boundary" to square(-50.0, -3.0, 0.02))

        val aids =
            area(
                project,
                "AIDS",
                1,
                square(-70.0, -10.0, 1.0, "MultiPolygon"),
                "locality" to withBoundary,
                "locality" to onlyPoint,
                "locality" to noGeometry
            )
        approve(aids, manager.token)
        val oldAiis = area(project, "AIIS", 1, square(-71.0, -11.0, 1.0, "MultiPolygon"), "locality" to stale)
        approve(oldAiis, manager.token)
        val aiis = area(project, "AIIS", 2, null, "community" to community)
        approve(aiis, manager.token)
        val draft = area(project, "AID AMBIENTAL", 1, square(-72.0, -12.0, 1.0, "MultiPolygon"), "locality" to draftOnly)
        assertEquals("superseded", state("area_of_influence", oldAiis))
        assertEquals("draft", state("area_of_influence", draft))

        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-13?project=$project", null, HttpStatus.OK, manager.token))
        val members = featuresOf(layer, "area_of_influence_member").associateBy { it["properties"]["__targetId"].asString() }
        assertEquals(setOf(withBoundary, onlyPoint, community), members.keys, layer.toString())
        // el límite antes que la ubicación, la ubicación si no hay límite y el territorio de la comunidad
        assertEquals("Polygon", members.getValue(withBoundary)["geometry"]["type"].asString())
        assertEquals("Point", members.getValue(onlyPoint)["geometry"]["type"].asString())
        assertEquals("MultiPolygon", members.getValue(community)["geometry"]["type"].asString())
        val memberProperties = members.getValue(withBoundary)["properties"]
        assertEquals("${memberProperties["__id"].asString()}:boundary", members.getValue(withBoundary)["id"].asString())
        assertEquals("locality", memberProperties["__target"].asString())
        assertEquals("community", members.getValue(community)["properties"]["__target"].asString())
        assertEquals("LYR-13", memberProperties["__layer"].asString())
        assertEquals("INCLUIDO", memberProperties["member_condition"].asString())
        assertEquals(aids, memberProperties["area_of_influence"].asString())
        assertEquals(aiis, members.getValue(community)["properties"]["area_of_influence"].asString())
        // el polígono de la versión aprobada sale; el del borrador y el de la reemplazada no
        assertEquals(listOf(aids), featuresOf(layer, "area_of_influence").map { it["properties"]["__id"].asString() })
        assertFalse(layer.toString().contains(stale) || layer.toString().contains(draftOnly), layer.toString())

        // bbox acota los miembros por la geometría que se dibuja
        val boxed = tree(send("GET", "/api/sgspe/territory/layers/LYR-13?project=$project&bbox=-70.5,-10.5,-69.5,-9.5", null, HttpStatus.OK, manager.token))
        assertEquals(setOf(withBoundary), featuresOf(boxed, "area_of_influence_member").map { it["properties"]["__targetId"].asString() }.toSet())
    }

    @Test
    fun `LYR-13 acota los miembros por proyecto y quien solo lee los ve`() {
        val project = projectWithCrs()
        val other = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val mine = locality("location" to point(-61.0, -6.0))
        val theirs = locality("location" to point(-61.001, -6.001))
        approve(area(project, "AIDS", 1, null, "locality" to mine), manager.token)
        approve(area(other, "AIDS", 1, null, "locality" to theirs), member("SOCIAL_MANAGER", other).token)

        val readOnly = userWithRole("READ_ONLY")
        val own = tree(send("GET", "/api/sgspe/territory/layers/LYR-13?project=$project", null, HttpStatus.OK, readOnly.token))
        assertEquals(listOf(mine), featuresOf(own, "area_of_influence_member").map { it["properties"]["__targetId"].asString() })
        val both = tree(send("GET", "/api/sgspe/territory/layers/LYR-13?project=$project&project=$other", null, HttpStatus.OK, readOnly.token))
        assertEquals(setOf(mine, theirs), featuresOf(both, "area_of_influence_member").map { it["properties"]["__targetId"].asString() }.toSet())
    }

    @Test
    fun `LYR-07 muestra todas las concesiones y marca con ofProject las del proyecto`() {
        val project = projectWithCrs()
        val linked = createWith("mining_concession", sample("mining_concession"), mapOf("boundary" to square(-77.0, -9.0, 0.01, "MultiPolygon")))
        val free = createWith("mining_concession", sample("mining_concession"), mapOf("boundary" to square(-77.005, -9.005, 0.01, "MultiPolygon")))
        // vincular responde 204 sin cuerpo
        assertEquals(HttpStatus.NO_CONTENT, link("project", project, "project_concession", linked).status)
        val bbox = "bbox=-77.1,-9.1,-76.9,-8.9"
        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-07?project=$project&$bbox", null, HttpStatus.OK))
        val concessions = layer["features"].values().associate { it["properties"]["__id"].asString() to it["properties"]["ofProject"].asBoolean() }
        assertEquals(mapOf(linked to true, free to false), concessions.filterKeys { it in setOf(linked, free) })
        // sin project no hay marca
        val plain = tree(send("GET", "/api/sgspe/territory/layers/LYR-07?$bbox", null, HttpStatus.OK))
        assertTrue(plain["features"].values().none { it["properties"].has("ofProject") })
    }
}
