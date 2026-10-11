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

// componentes × predios y × territorio comunal (casos 10 y 11 de gis.md §5.3 y su prueba de §9): una plataforma que corta dos
// predios y un acceso que cruza tres dan los predios y las longitudes esperadas, medidas en el working_crs; los titulares
// salen por Records con permiso de campo. tres predios de 1 km de lado en fila a 40 km al este del origen, para no cruzarse
// con las geometrías de otras pruebas
class SpatialLandQueriesApiTest : F6QueryApiTest() {
    private val x = 40_000.0

    private class Land(
        val project: UUID,
        val p1: UUID,
        val p2: UUID,
        val p3: UUID,
        val platform: UUID,
        val access: UUID,
        val platformCode: String
    )

    private fun land(): Land =
        runBlocking {
            val project = utmProject()
            val p1 = parcel(uniqueName("PR-"), "PROPIEDAD", rect(x, 0.0, x + 1000, 1000.0))
            val p2 = parcel(uniqueName("PR-"), "POSESION", rect(x + 1000, 0.0, x + 2000, 1000.0))
            val p3 = parcel(uniqueName("PR-"), "COMUNAL", rect(x + 2000, 0.0, x + 3000, 1000.0))
            parcelOwner(p1, "Persona Ejemplo")
            val code = uniqueName("PLT-")
            // 200 m × 200 m sobre el lindero de P1 y P2: 2 ha en cada uno
            val platform = component(project, code, "PLATAFORMA", rect(x + 900, 400.0, x + 1100, 600.0))
            // de 250 m a 2 750 m: 750 m en P1, 1 000 m en P2 y 750 m en P3
            val access = component(project, uniqueName("ACC-"), "ACCESO", path(x + 250 to 500.0, x + 2750 to 500.0))
            Land(project, p1, p2, p3, platform, access, code)
        }

    private fun rows(
        project: UUID,
        endpoint: String,
        token: String = this.token
    ): List<JsonNode> = get("/api/sgspe/territory/projects/$project/$endpoint", token)["rows"].values().toList()

    private fun near(
        expected: Double,
        actual: JsonNode?
    ) = assertTrue(actual != null && abs(actual.asDouble() - expected) <= expected * 0.01, "esperado $expected ± 1 %, llegó $actual")

    @Test
    fun `una plataforma corta dos predios y un acceso cruza tres, con sus medidas y titulares`() {
        val l = land()
        val body = get("/api/sgspe/territory/projects/${l.project}/component-parcels")
        assertTrue(body["available"].asBoolean())
        assertEquals(false, body["truncated"].asBoolean())
        // solo los predios de esta prueba: otras (y corridas anteriores en la misma base) dejan predios del tenant en el mismo lugar
        val mine = setOf(l.p1, l.p2, l.p3).map { it.toString() }
        val byPair =
            body["rows"]
                .values()
                .filter { it["parcel"]["id"].asString() in mine }
                .associateBy { it["component"]["id"].asString() to it["parcel"]["id"].asString() }
        assertEquals(5, byPair.size)
        near(2.0, byPair.getValue(l.platform.toString() to l.p1.toString())["areaHa"])
        near(2.0, byPair.getValue(l.platform.toString() to l.p2.toString())["areaHa"])
        near(750.0, byPair.getValue(l.access.toString() to l.p1.toString())["lengthM"])
        near(1000.0, byPair.getValue(l.access.toString() to l.p2.toString())["lengthM"])
        near(750.0, byPair.getValue(l.access.toString() to l.p3.toString())["lengthM"])
        assertEquals("PROPIEDAD", byPair.getValue(l.platform.toString() to l.p1.toString())["parcel"]["tenureType"].asString())
        val holders = byPair.getValue(l.access.toString() to l.p1.toString())["holders"].values()
        assertEquals(listOf("person"), holders.map { it["object"].asString() })
        assertTrue(byPair.getValue(l.access.toString() to l.p2.toString())["holders"].isEmpty)
    }

    @Test
    fun `el acceso cruza el territorio comunal con su longitud`() {
        val l = land()
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(x + 1500, 0.0, x + 2500, 1000.0, multi = true)) }
        val crossing = rows(l.project, "component-communal-land").filter { it["community"]["id"].asString() == community.toString() }
        assertEquals(listOf(l.access.toString()), crossing.map { it["component"]["id"].asString() })
        near(1000.0, crossing.single()["lengthM"])
    }

    @Test
    fun `una plataforma movida deja de cruzar sus predios viejos`() {
        val l = land()
        val moved = runBlocking { component(l.project, l.platformCode, "PLATAFORMA", rect(x + 5000, 5000.0, x + 5200, 5200.0)) }
        val components = rows(l.project, "component-parcels").map { it["component"]["id"].asString() }.toSet()
        assertTrue(l.platform.toString() !in components && moved.toString() !in components, "$components")
        assertTrue(l.access.toString() in components)
    }

    @Test
    fun `un proyecto sin working_crs es 409 y uno ajeno o inexistente 404`() {
        val plain = project()
        assertEquals("WORKING_CRS_REQUIRED", failing("/api/sgspe/territory/projects/$plain/component-parcels", HttpStatus.CONFLICT))
        assertEquals("PROJECT_NOT_FOUND", failing("/api/sgspe/territory/projects/${UUID.randomUUID()}/component-parcels", HttpStatus.NOT_FOUND))
    }

    @Test
    fun `limit recorta con truncated y fuera de rango es 400`() {
        val l = land()
        val one = get("/api/sgspe/territory/projects/${l.project}/component-parcels?limit=1")
        assertEquals(1, one["rows"].size())
        assertTrue(one["truncated"].asBoolean())
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/${l.project}/component-parcels?limit=0", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/projects/${l.project}/component-communal-land?radio=1").status)
    }
}
