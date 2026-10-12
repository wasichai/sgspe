package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

// concesiones × territorios, elementos sensibles y conflictos cercanos (casos 12, 13 y 7 de gis.md §5.3). cada prueba en su
// banda al sur del origen, y las aserciones solo miran los ids que crea
class SpatialProximityApiTest : F6QueryApiTest() {
    private fun near(
        expected: Double,
        actual: JsonNode?
    ) = assertTrue(actual != null && abs(actual.asDouble() - expected) <= expected * 0.01, "esperado $expected ± 1 %, llegó $actual")

    @Test
    fun `las comunidades, los sectores y las localidades sobre las concesiones del proyecto`() {
        val y = -50_000.0
        val project = runBlocking { utmProject() }
        val concession =
            createWith(
                "mining_concession",
                mapOf("code" to uniqueName("CM-"), "name" to "Concesión Ejemplo"),
                mapOf("boundary" to rect(0.0, y, 2000.0, y + 1000, multi = true)),
                headers = withReason("alta de prueba")
            )
        assertTrue(link("project", project.toString(), "project_concession", concession).status.is2xxSuccessful)
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(1000.0, y, 3000.0, y + 1000, multi = true)) }
        val sector =
            createWith(
                "community_sector",
                mapOf("community" to community.toString(), "name" to "Sector Comunal Ejemplo"),
                mapOf("boundary" to rect(1200.0, y + 200, 1400.0, y + 400)),
                headers = withReason("alta de prueba")
            )
        val place = localityWith("Caserío Ejemplo", location = at(500.0, y + 500))
        val rows =
            get("/api/sgspe/territory/projects/$project/concession-territories")["rows"].values().filter {
                it["concession"]["id"].asString() ==
                    concession
            }
        val byId = rows.associateBy { it["id"].asString() }
        near(100.0, byId.getValue(community.toString())["areaHa"])
        assertEquals("community_sector", byId.getValue(sector)["object"].asString())
        near(4.0, byId.getValue(sector)["areaHa"])
        assertEquals("CASERIO", byId.getValue(place.toString())["localityType"].asString())
        assertTrue(absent(byId.getValue(place.toString())["areaHa"]))
    }

    @Test
    fun `los componentes a menos de la distancia de un elemento sensible, del mas cercano al mas lejano`() {
        val y = -60_000.0
        val project = runBlocking { utmProject() }
        val platform = runBlocking { component(project, uniqueName("PLT-"), "PLATAFORMA", rect(0.0, y, 100.0, y + 100)) }
        val water = runBlocking { environmentalFeature("Fuente de agua Ejemplo", "FUENTE DE AGUA", location = at(600.0, y + 50)) }
        val hill = runBlocking { environmentalFeature("Cerro Ejemplo", "CERRO", location = at(300.0, y + 50)) }
        val sacred = runBlocking { environmentalFeature("Sitio sagrado Ejemplo", "SITIO SAGRADO", location = at(5000.0, y)) }
        val rows =
            get("/api/sgspe/territory/projects/$project/sensitive-distances?distanceKm=1")["rows"].values().filter {
                it["component"]["id"].asString() ==
                    platform.toString()
            }
        val features = rows.map { it["feature"]["id"].asString() }
        assertTrue(water.toString() in features && hill.toString() !in features && sacred.toString() !in features, "$features")
        near(500.0, rows.first { it["feature"]["id"].asString() == water.toString() }["distanceM"])
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/$project/sensitive-distances?distanceKm=60", HttpStatus.BAD_REQUEST))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/$project/sensitive-distances?distanceKm=0.05", HttpStatus.BAD_REQUEST))
    }

    @Test
    fun `incidentes, senales y conflictos cercanos, ordenados por distancia`() {
        val y = -70_000.0
        val project = runBlocking { utmProject() }
        runBlocking { component(project, uniqueName("PLT-"), "PLATAFORMA", rect(0.0, y, 100.0, y + 100)) }
        val place = runBlocking { locality("Caserío Ejemplo", at(2000.0, y + 50)) }
        val close = incident(project.toString())
        assertEquals(
            HttpStatus.OK,
            edit(
                "incident",
                close,
                emptyMap(),
                headers = withReason("ubicación de prueba"),
                geometries =
                    mapOf("location" to at(1000.0, y + 50))
            ).status
        )
        val far = incident(project.toString())
        assertEquals(
            HttpStatus.OK,
            edit(
                "incident",
                far,
                emptyMap(),
                headers = withReason("ubicación de prueba"),
                geometries =
                    mapOf("location" to at(20000.0, y))
            ).status
        )
        val signal = warningSignal(project.toString(), attributes = mapOf("locality" to place.toString()))
        val conflict = fixture("local_conflict", mapOf("conflict_status" to "ACTIVO")).id
        assertTrue(linkWithReason("local_conflict", conflict, "local_conflict_locality", place.toString()).status.is2xxSuccessful)

        val items =
            get("/api/sgspe/territory/projects/$project/nearby-conflicts?distanceKm=5")["items"]
                .values()
                .filter { it["id"].asString() in setOf(close, far, signal, conflict) }
        assertEquals(close, items.first()["id"].asString())
        near(900.0, items.first()["distanceM"])
        assertEquals(setOf(signal, conflict), items.drop(1).map { it["id"].asString() }.toSet())
        assertTrue(items.drop(1).all { abs(it["distanceM"].asDouble() - 1900.0) <= 19.0 })
        assertTrue(far !in items.map { it["id"].asString() })
    }
}
