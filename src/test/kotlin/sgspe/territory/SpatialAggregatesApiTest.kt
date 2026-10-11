package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID

// la concentración de eventos para el mapa de calor (casos 4 y 6 de gis.md §5.3) y el resumen del área (caso 15). las
// quejas de attentionAttributes se reciben el 2026-10-05
class SpatialAggregatesApiTest : F6QueryApiTest() {
    private val october = "periodStart=2026-10-01&periodEnd=2026-10-31"

    @Test
    fun `las QCS cuentan en su localidad o, sin ella, en su comunidad, y sin territorio no cuentan`() {
        val y = -80_000.0
        val project = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(1000.0, y, 2000.0, y + 1000, multi = true)) }
        repeat(3) { grievance(project.toString(), attributes = mapOf("locality" to place.toString())) }
        grievance(project.toString(), attributes = mapOf("community" to community.toString()))
        grievance(project.toString())
        val body = get("/api/sgspe/territory/concentration?of=grievance&project=$project&$october")
        assertTrue(body["available"].asBoolean())
        val cells = body["cells"].values().associateBy { it["id"].asString() }
        assertEquals(2, cells.size)
        assertEquals(3, cells.getValue(place.toString())["count"].asInt())
        assertEquals("locality", cells.getValue(place.toString())["object"].asString())
        assertEquals("Point", cells.getValue(place.toString())["point"]["type"].asString())
        assertEquals(1, cells.getValue(community.toString())["count"].asInt())
        assertEquals("community", cells.getValue(community.toString())["object"].asString())
        assertEquals(0, get("/api/sgspe/territory/concentration?of=grievance&project=$project&periodStart=2026-11-01&periodEnd=2026-11-30")["cells"].size())
    }

    @Test
    fun `los incidentes tambien se concentran y los errores dicen que falta`() {
        val project = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, -81_000.0)) }
        incident(project.toString(), attributes = mapOf("locality" to place.toString()))
        val cells = get("/api/sgspe/territory/concentration?of=incident&project=$project&$october")["cells"].values()
        assertEquals(listOf(place.toString() to 1), cells.map { it["id"].asString() to it["count"].asInt() })
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/concentration?of=commitment", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/concentration", HttpStatus.BAD_REQUEST))
        assertEquals(
            "OUT_OF_RANGE",
            failing("/api/sgspe/territory/concentration?of=grievance&periodStart=2020-01-01&periodEnd=2026-10-31", HttpStatus.BAD_REQUEST)
        )
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/concentration?of=grievance&project=no-es-uuid", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/concentration?of=grievance&radio=1").status)
    }

    @Test
    fun `el resumen del area da su superficie, sus localidades por tipo y los componentes sobre tierra comunal o privada`() {
        val y = -90_000.0
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        listOf(
            localityWith("Caserío Ejemplo A", location = at(100.0, y + 100)),
            localityWith("Caserío Ejemplo B", location = at(200.0, y + 100)),
            localityWith("Centro Poblado Ejemplo", location = at(300.0, y + 100), type = "CENTRO POBLADO")
        ).forEach { areaMember(area, "locality", it) }
        runBlocking {
            parcel(uniqueName("PR-"), "PROPIEDAD", rect(2000.0, y, 3000.0, y + 1000))
            communityWithTerritory("Comunidad Ejemplo", rect(4000.0, y, 5000.0, y + 1000, multi = true))
            component(project, uniqueName("PLT-"), "PLATAFORMA", rect(2400.0, y + 400, 2600.0, y + 600))
            component(project, uniqueName("ACC-"), "ACCESO", path(3900.0 to y + 500, 4500.0 to y + 500))
        }
        val body = get("/api/sgspe/areas-of-influence/$area/summary")
        assertTrue(body["available"].asBoolean())
        assertEquals(record("area_of_influence", area.toString())["attributes"]["calculated_area_ha"].asDouble(), body["areaHa"].asDouble())
        assertEquals(
            listOf("CASERIO" to 2, "CENTRO POBLADO" to 1),
            body["localitiesByType"].values().map { it["localityType"].asString() to it["count"].asInt() }
        )
        assertEquals(1, body["componentsOnPrivateLand"].asInt())
        assertEquals(1, body["componentsOnCommunalLand"].asInt())
        assertEquals("AREA_NOT_FOUND", failing("/api/sgspe/areas-of-influence/${UUID.randomUUID()}/summary", HttpStatus.NOT_FOUND))
    }
}
