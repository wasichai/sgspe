package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import sgspe.SyntheticGeo
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.random.Random

// las capas de gestión de registros (gis.md §2.2 y §2.4, plan 6.2c): cada registro en su geometría o en el punto de su
// localidad, comunidad o área; el periodo por la fecha de cada fuente; el alcance de quien mira (QCS confidenciales, proyectos
// no asignados) y sus permisos de campo. todas las consultas van con `project`, así que los datos de otras pruebas no cuentan
class ManagementLayersApiTest : F6QueryApiTest() {
    private val owner by lazy { userWithRole("COMMITMENT_MANAGER") }
    private val checker by lazy { userWithRole("COMMITMENT_MANAGER") }

    // una banda propia de 100 a 900 km al sur del origen, para que el recuadro no traiga datos de otras pruebas
    private fun band(): Double = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)

    private fun bbox(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double
    ): String {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val (minX, minY) = SyntheticGeo.offset(lon, lat, x0, y0)
        val (maxX, maxY) = SyntheticGeo.offset(lon, lat, x1, y1)
        return "$minX,$minY,$maxX,$maxY"
    }

    private fun commitmentAt(
        project: UUID,
        attributes: Map<String, Any?> = emptyMap(),
        location: Map<String, Any?>? = null
    ): String =
        createWith(
            "commitment",
            commitmentAttributes(project.toString(), owner, checker) + attributes,
            if (location == null) emptyMap() else mapOf("location" to location),
            headers = withReason("alta de prueba")
        )

    private fun layer(
        id: String,
        query: String,
        token: String = this.token
    ): JsonNode = get("/api/sgspe/territory/layers/$id?$query", token)

    private fun byId(layer: JsonNode): Map<String, JsonNode> = layer["features"].values().associateBy { it["properties"]["__id"].asString() }

    @Test
    fun `LYR-19 dibuja cada compromiso en su ubicacion y, sin ella, en su localidad o su comunidad`() {
        val y = band()
        val project = runBlocking { utmProject() }
        val withPoint = localityWith("Caserío Ejemplo", location = at(0.0, y))
        val withBoundary = localityWith("Sector Ejemplo", boundary = rect(1000.0, y, 2000.0, y + 1000))
        val bare = localityWith("Anexo Ejemplo")
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(3000.0, y, 4000.0, y + 1000, multi = true)) }
        val own = commitmentAt(project, location = at(500.0, y + 500))
        val byPoint = commitmentAt(project, mapOf("locality" to withPoint.toString()))
        val byBoundary = commitmentAt(project, mapOf("locality" to withBoundary.toString()))
        val fallsThrough = commitmentAt(project, mapOf("locality" to bare.toString(), "community" to community.toString()))
        val stranded = commitmentAt(project, mapOf("locality" to bare.toString()))
        val nowhere = commitmentAt(project)

        val body = layer("LYR-19", "project=$project")
        assertTrue(body["available"].asBoolean(), body.toString())
        assertFalse(body["truncated"].asBoolean())
        val features = byId(body)
        assertEquals(setOf(own, byPoint, byBoundary, fallsThrough), features.keys, "sin territorio con geometría no se dibuja: $stranded, $nowhere")
        val mine = features.getValue(own)
        assertEquals("$own:location", mine["id"].asString())
        assertTrue(absent(mine["properties"]["__target"]))
        assertEquals("LYR-19", mine["properties"]["__layer"].asString())
        assertEquals("commitment", mine["properties"]["__object"].asString())
        assertEquals("draft", mine["properties"]["__state"].asString())
        assertEquals("Mejora del canal de riego", mine["properties"]["title"].asString())
        assertEquals("$byPoint:locality", features.getValue(byPoint)["id"].asString())
        assertEquals("locality", features.getValue(byPoint)["properties"]["__target"].asString())
        assertEquals(withPoint.toString(), features.getValue(byPoint)["properties"]["__targetId"].asString())
        // una localidad con solo su límite da un punto sobre él
        assertEquals("Point", features.getValue(byBoundary)["geometry"]["type"].asString())
        // una localidad sin geometría deja caer el registro a su comunidad
        assertEquals("community", features.getValue(fallsThrough)["properties"]["__target"].asString())
        assertEquals(community.toString(), features.getValue(fallsThrough)["properties"]["__targetId"].asString())
        // el recuadro se aplica a lo que se dibuja: solo el punto sobre la comunidad
        val boxed = layer("LYR-19", "project=$project&bbox=${bbox(2900.0, y - 100, 4100.0, y + 1100)}")
        assertEquals(setOf(fallsThrough), byId(boxed).keys)
    }

    @Test
    fun `LYR-20 muestra una queja confidencial solo a quien la lee y la persona solo a quien la lee`() {
        val project = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, band())) }
        val hidden = runBlocking { confidentialGrievance(project, place) }.toString()
        val open = grievance(project.toString(), attributes = mapOf("locality" to place.toString(), "person" to person("Persona Ejemplo")))
        val question = inquiry(project.toString(), attributes = mapOf("locality" to place.toString()))
        val request = stakeholderRequest(project.toString(), attributes = mapOf("locality" to place.toString()))

        fun ids(token: String): Set<String> = byId(layer("LYR-20", "project=$project", token)).keys
        val everything = setOf(hidden, open, question, request)
        assertEquals(everything, ids(token))
        assertEquals(everything, ids(member("GRIEVANCE_MANAGER", project.toString()).token))
        for (role in listOf("COMMUNITY_RELATIONS_OFFICER", "ANALYST")) {
            assertEquals(setOf(open, question, request), ids(member(role, project.toString()).token), role)
        }
        // sin asignación al proyecto no ve nada (ProjectReadScope)
        assertEquals(emptySet<String>(), ids(userWithRole("ANALYST").token))
        // la persona que presentó la queja es un campo restringido: la lee un CRO, no un ANALYST
        val forAnalyst = byId(layer("LYR-20", "project=$project", member("ANALYST", project.toString()).token)).getValue(open)["properties"]
        assertFalse(forAnalyst.has("person"), forAnalyst.toString())
        val forOfficer = byId(layer("LYR-20", "project=$project", member("COMMUNITY_RELATIONS_OFFICER", project.toString()).token)).getValue(open)["properties"]
        assertTrue(forOfficer.has("person"), forOfficer.toString())
    }

    @Test
    fun `LYR-18 acota cada fuente por su fecha y las observaciones por el proyecto de su visita`() {
        val y = band()
        val project = runBlocking { utmProject() }
        val other = runBlocking { utmProject() }
        val meeting =
            createWith(
                "engagement",
                mapOf("project" to project.toString(), "engagement_type" to "REUNION", "title" to "Reunión de prueba", "held_at" to "2026-10-05T15:00:00Z"),
                mapOf("location" to at(0.0, y))
            )
        val visit =
            createWith(
                "field_visit",
                mapOf("project" to project.toString(), "title" to "Visita de prueba", "actual_start_date" to "2026-10-03", "actual_end_date" to "2026-10-04"),
                mapOf("route" to path(0.0 to y, 500.0 to y))
            )
        val observation =
            createWith(
                "field_observation",
                mapOf("field_visit" to visit, "observation" to "Camino en mal estado", "observed_at" to "2026-10-03T16:00:00Z"),
                mapOf("location" to at(100.0, y))
            )
        val elsewhere = createRecord("field_visit", mapOf("project" to other.toString(), "title" to "Visita de otro proyecto"))
        val foreign =
            createWith(
                "field_observation",
                mapOf("field_visit" to elsewhere, "observation" to "Puente caído", "observed_at" to "2026-10-03T16:00:00Z"),
                mapOf("location" to at(200.0, y))
            )

        fun ids(query: String): Set<String> = layer("LYR-18", "project=$project$query")["features"].values().map { it["id"].asString() }.toSet()
        val all = setOf("$meeting:location", "$visit:route", "$observation:location")
        assertEquals(all, ids("&periodStart=2026-10-01&periodEnd=2026-10-31"))
        assertEquals(emptySet<String>(), ids("&periodStart=2026-11-01&periodEnd=2026-11-30"))
        assertEquals(all, ids(""), "la observación $foreign es de otro proyecto")
        val route = layer("LYR-18", "project=$project")["features"].values().single { it["id"].asString() == "$visit:route" }
        assertEquals("LineString", route["geometry"]["type"].asString())
        assertTrue(absent(layer("LYR-18", "project=$project&periodStart=2026-10-01&periodEnd=2026-10-31")["periodNotApplied"]))
    }

    @Test
    fun `LYR-21 no tiene fecha, el periodo no la vacia y lo dice, y un riesgo sin territorio cae en su area de influencia`() {
        val y = band()
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        val risk = socialRisk(project.toString(), attributes = mapOf("area_of_influence" to area.toString()))
        val loose = socialRisk(project.toString())
        val body = layer("LYR-21", "project=$project&periodStart=2026-10-01&periodEnd=2026-10-31")
        assertEquals(listOf("social_risk"), body["periodNotApplied"].values().map { it.asString() })
        val features = byId(body)
        assertEquals(setOf(risk), features.keys, "sin territorio no se dibuja: $loose")
        assertEquals("area_of_influence", features.getValue(risk)["properties"]["__target"].asString())
        assertEquals("$risk:area_of_influence", features.getValue(risk)["id"].asString())
        assertEquals("Point", features.getValue(risk)["geometry"]["type"].asString())
    }

    @Test
    fun `LYR-22 junta incidentes, senales y alertas, cada uno en su ubicacion o en su localidad o comunidad`() {
        val y = band()
        val project = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(1000.0, y, 2000.0, y + 1000, multi = true)) }
        val event = incident(project.toString(), attributes = mapOf("locality" to place.toString()))
        val signal = warningSignal(project.toString(), attributes = mapOf("community" to community.toString()))
        val warning = earlyWarning(project.toString(), attributes = mapOf("locality" to place.toString()))
        val features = byId(layer("LYR-22", "project=$project"))
        assertEquals(
            mapOf(event to "incident", signal to "early_warning_signal", warning to "early_warning"),
            features.mapValues { it.value["properties"]["__object"].asString() }
        )
        assertEquals("community", features.getValue(signal)["properties"]["__target"].asString())
    }

    @Test
    fun `sin READ sobre un objeto de la capa es 403, un periodo mal escrito 400 y una capa fuera del catalogo 404`() {
        val project = runBlocking { utmProject() }
        val agent = member("FIELD_AGENT", project.toString())
        for (id in listOf("LYR-19", "LYR-21", "LYR-22")) {
            assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/territory/layers/$id?project=$project", token = agent.token).status, id)
        }
        assertEquals(HttpStatus.OK, exchange("GET", "/api/sgspe/territory/layers/LYR-18?project=$project", token = agent.token).status)
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/layers/LYR-19?periodStart=2026-13-01", HttpStatus.BAD_REQUEST))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/layers/LYR-19?periodStart=2020-01-01&periodEnd=2026-10-31", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_BBOX", failing("/api/sgspe/territory/layers/LYR-19?bbox=1,2", HttpStatus.BAD_REQUEST))
        assertEquals("LAYER_NOT_FOUND", failing("/api/sgspe/territory/layers/LYR-25", HttpStatus.NOT_FOUND))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/layers/LYR-19?periodo=2026").status)
    }

    @Test
    fun `LYR-20 dice confidentialHidden a quien no lee las QCS confidenciales y una capa sin QCS no lo lleva`() {
        val project = runBlocking { utmProject() }
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project.toString())
        assertTrue(layer("LYR-20", "project=$project", officer.token)["confidentialHidden"].asBoolean())
        assertTrue(absent(layer("LYR-20", "project=$project", member("GRIEVANCE_MANAGER", project.toString()).token)["confidentialHidden"]))
        assertTrue(absent(layer("LYR-19", "project=$project", officer.token)["confidentialHidden"]))
    }
}
