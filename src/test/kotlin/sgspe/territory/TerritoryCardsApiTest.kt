package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID

// la parte territorial de la ficha contextual (api.md §4, RQ-GIS-005): identificación, geometría y relaciones de una
// localidad o una comunidad, leídas como quien llama. las cifras no salen de aquí: son KPI-TER-01 (sgspe.dashboard)
class TerritoryCardsApiTest : F6QueryApiTest() {
    private val y = -100_000.0

    private fun ids(
        node: tools.jackson.databind.JsonNode,
        key: String
    ): Set<String> = node[key].values().map { it["id"].asString() }.toSet()

    @Test
    fun `la ficha de una localidad trae su unidad, las comunidades que la contienen y sus areas aprobadas`() {
        val (project, other) = runBlocking { utmProject() to utmProject() }
        val district = unit("DISTRITO", rect(-500.0, y - 500, 1500.0, y + 1500, multi = true))
        val place =
            localityWith(
                "Caserío Ejemplo",
                boundary = rect(0.0, y, 500.0, y + 500),
                location = at(250.0, y + 250),
                attributes = mapOf("administrative_unit" to district.toString())
            )
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(-100.0, y - 100, 1000.0, y + 1000, multi = true)) }
        val mine = area(project).also { areaMember(it, "locality", place) }
        approveArea(mine, project)
        val theirs = area(other).also { areaMember(it, "locality", place) }
        approveArea(theirs, other)
        area(project, type = "AIIS").also { areaMember(it, "locality", place) }

        val card = get("/api/sgspe/territory/localities/$place/card")
        assertEquals(place.toString(), card["locality"]["id"].asString())
        assertEquals("Polygon", card["geometry"]["boundary"]["type"].asString())
        assertEquals("Point", card["geometry"]["location"]["type"].asString())
        assertEquals(district.toString(), card["administrativeUnit"]["id"].asString())
        assertTrue(community.toString() in ids(card, "communities"))
        assertEquals(setOf(mine.toString(), theirs.toString()), ids(card, "areasOfInfluence"))
        assertEquals(0, card["unavailable"].size())
        assertEquals(setOf(mine.toString()), ids(get("/api/sgspe/territory/localities/$place/card?project=$project"), "areasOfInfluence"))
    }

    @Test
    fun `la ficha de una comunidad trae su territorio, sus sectores y sus areas`() {
        val project = runBlocking { utmProject() }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(0.0, y - 5000, 1000.0, y - 4000, multi = true)) }
        val sector =
            createWith(
                "community_sector",
                mapOf("community" to community.toString(), "name" to "Sector Comunal Ejemplo"),
                headers = withReason("alta de prueba")
            )
        val mine = area(project).also { areaMember(it, "community", community) }
        approveArea(mine, project)
        val card = get("/api/sgspe/territory/communities/$community/card")
        assertEquals(community.toString(), card["community"]["id"].asString())
        assertEquals("MultiPolygon", card["geometry"]["territory"]["type"].asString())
        assertEquals(setOf(sector), ids(card, "sectors"))
        assertEquals(setOf(mine.toString()), ids(card, "areasOfInfluence"))
    }

    @Test
    fun `los errores de la ficha`() {
        val place = localityWith("Caserío Ejemplo", location = at(0.0, y))
        assertEquals("LOCALITY_NOT_FOUND", failing("/api/sgspe/territory/localities/${UUID.randomUUID()}/card", HttpStatus.NOT_FOUND))
        assertEquals("COMMUNITY_NOT_FOUND", failing("/api/sgspe/territory/communities/${UUID.randomUUID()}/card", HttpStatus.NOT_FOUND))
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/localities/$place/card?project=no-es-uuid", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/localities/$place/card?radio=1").status)
    }
}
