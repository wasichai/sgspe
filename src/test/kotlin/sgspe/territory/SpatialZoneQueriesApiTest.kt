package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID
import kotlin.random.Random

// los registros de una zona y el punto pulsado (casos 2, 3, 5 y 16 de gis.md §5.3; especificación de F6 §3.1): la zona se lee
// como quien llama y los registros salen por Records con el criterio por zona (su relación territorial o su geometría), con
// los permisos y el alcance de la API genérica
class SpatialZoneQueriesApiTest : F6QueryApiTest() {
    private val owner by lazy { userWithRole("COMMITMENT_MANAGER") }
    private val checker by lazy { userWithRole("COMMITMENT_MANAGER") }

    // un compromiso en draft del proyecto, con atributos y, si viene, su ubicación (responsable y verificador distintos, D11)
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

    private fun ids(
        zoneObject: String,
        zone: UUID,
        query: String
    ): Set<String> = get("/api/sgspe/territory/zones/$zoneObject/$zone/records?$query")["content"].values().map { it["id"].asString() }.toSet()

    @Test
    fun `una localidad trae lo que la nombra, lo de sus sectores y lo que cae en su limite`() {
        val project = runBlocking { utmProject() }
        val zone = localityWith("Caserío Ejemplo", boundary = rect(0.0, 0.0, 1000.0, 1000.0))
        val sector = localityWith("Sector Ejemplo", location = at(100.0, 100.0), type = "SECTOR", attributes = mapOf("parent_locality" to zone.toString()))
        val named = commitmentAt(project, mapOf("locality" to zone.toString()))
        val inSector = commitmentAt(project, mapOf("locality" to sector.toString()))
        val placed = commitmentAt(project, location = at(500.0, 500.0))
        val outside = commitmentAt(project, location = at(5000.0, 500.0))
        val found = ids("locality", zone, "of=commitment")
        assertTrue(found.containsAll(setOf(named, inSector, placed)), "$found")
        assertTrue(outside !in found)
    }

    @Test
    fun `una comunidad trae lo que la nombra y lo que cae en su territorio`() {
        val project = runBlocking { utmProject() }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(2000.0, 0.0, 3000.0, 1000.0, multi = true)) }
        val named = commitmentAt(project, mapOf("community" to community.toString()))
        val placed = commitmentAt(project, location = at(2500.0, 500.0))
        assertEquals(setOf(named, placed), ids("community", community, "of=commitment").intersect(setOf(named, placed)))
    }

    @Test
    fun `una localidad sin limite trae lo que la nombra`() {
        val project = runBlocking { utmProject() }
        val zone = localityWith("Caserío Ejemplo", location = at(0.0, 0.0))
        val named = UUID.fromString(stakeholder(project.toString(), mapOf("locality" to zone.toString())))
        assertEquals(setOf(named.toString()), ids("locality", zone, "of=stakeholder"))
    }

    @Test
    fun `el periodo acota por la fecha del objeto y no aplica a los que no la tienen`() {
        val project = runBlocking { utmProject() }
        val zone = localityWith("Caserío Ejemplo", boundary = rect(0.0, 0.0, 1000.0, 1000.0))
        val named = commitmentAt(project, mapOf("locality" to zone.toString()))
        assertTrue(named in ids("locality", zone, "of=commitment&periodStart=2026-10-01&periodEnd=2026-10-31"))
        assertTrue(named !in ids("locality", zone, "of=commitment&periodStart=2026-11-01&periodEnd=2026-11-30"))
        assertEquals(
            "PERIOD_NOT_APPLICABLE",
            failing("/api/sgspe/territory/zones/locality/$zone/records?of=social_risk&periodStart=2026-10-01", HttpStatus.BAD_REQUEST)
        )
    }

    @Test
    fun `los errores dicen que falta y que no existe`() {
        val zone = localityWith("Caserío Ejemplo", boundary = rect(0.0, 0.0, 1000.0, 1000.0))
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/zones/locality/$zone/records", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/zones/locality/$zone/records?of=land_parcel", HttpStatus.BAD_REQUEST))
        assertEquals("ZONE_NOT_FOUND", failing("/api/sgspe/territory/zones/project/$zone/records?of=commitment", HttpStatus.NOT_FOUND))
        assertEquals("ZONE_NOT_FOUND", failing("/api/sgspe/territory/zones/locality/${UUID.randomUUID()}/records?of=commitment", HttpStatus.NOT_FOUND))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/zones/locality/$zone/records?of=commitment&radio=5").status)
    }

    @Test
    fun `locate propone la unidad mas fina, la localidad y la comunidad que contienen el punto`() {
        // una banda propia, de 100 a 900 km al sur del origen: otra prueba (o una corrida anterior en la misma base) no deja ahí
        // una unidad, una localidad ni una comunidad que contengan el punto
        val y = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)
        val district = unit("DISTRITO", rect(-500.0, y - 500, 1500.0, y + 1500, multi = true))
        unit("PROVINCIA", rect(-5000.0, y - 5000, 5000.0, y + 5000, multi = true))
        val place = localityWith("Caserío Ejemplo", boundary = rect(0.0, y, 1000.0, y + 1000))
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(0.0, y, 1000.0, y + 1000, multi = true)) }
        val point = at(500.0, y + 500)
        @Suppress("UNCHECKED_CAST")
            val (lon, lat) = point["coordinates"] as List<Double>
        val body = get("/api/sgspe/territory/locate?lon=$lon&lat=$lat")
        assertEquals(district.toString(), body["administrativeUnit"]["id"].asString())
        assertEquals("DISTRITO", body["administrativeUnit"]["level"].asString())
        assertEquals(place.toString(), body["locality"]["id"].asString())
        assertEquals(community.toString(), body["community"]["id"].asString())
        assertEquals(0, body["unavailable"].size())
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/locate?lon=-181&lat=$lat", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/locate?lon=$lon", HttpStatus.BAD_REQUEST))
    }
}
