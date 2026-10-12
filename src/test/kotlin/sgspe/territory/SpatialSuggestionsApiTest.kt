package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// las sugerencias de miembros de un área de influencia (casos 1 y 9 de gis.md §5.3, RN-TER-004) y las localidades en el
// buffer de los componentes (caso 14): solo sugieren, nunca escriben; la persona acepta una en S10 y el miembro nace con
// spatially_suggested
class SpatialSuggestionsApiTest : F6QueryApiTest() {
    private fun suggestions(
        area: UUID,
        query: String = ""
    ): Map<String, JsonNode> =
        get("/api/sgspe/areas-of-influence/$area/member-suggestions$query")["suggestions"].values().associateBy { it["recordId"].asString() }

    private fun memberCount(area: UUID): Long = get("/api/objects/area_of_influence_member/records?area_of_influence=$area&size=1")["totalElements"].asLong()

    @Test
    fun `sugiere lo que cruza el area, con el buffer lo cercano, y no escribe nada`() {
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, 0.0, 1000.0, 1000.0, multi = true))
        val inside = localityWith("Caserío Ejemplo", location = at(500.0, 500.0))
        val near = localityWith("Caserío Ejemplo Cercano", location = at(1200.0, 500.0))
        val far = localityWith("Caserío Ejemplo Lejano", location = at(9000.0, 500.0))
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(800.0, 0.0, 1800.0, 1000.0, multi = true)) }
        val district = unit("DISTRITO", rect(-500.0, -500.0, 1500.0, 1500.0, multi = true))
        areaMember(area, "locality", inside)
        val before = memberCount(area)

        val plain = suggestions(area)
        assertEquals("INTERSECTS", plain.getValue(inside.toString())["relation"].asString())
        assertTrue(plain.getValue(inside.toString())["alreadyMember"].asBoolean())
        assertEquals("community", plain.getValue(community.toString())["object"].asString())
        assertEquals(false, plain.getValue(community.toString())["alreadyMember"].asBoolean())
        assertEquals("administrative_unit", plain.getValue(district.toString())["object"].asString())
        assertTrue(near.toString() !in plain && far.toString() !in plain)

        val buffered = suggestions(area, "?bufferM=300")
        assertEquals("WITHIN_BUFFER", buffered.getValue(near.toString())["relation"].asString())
        assertEquals("INTERSECTS", buffered.getValue(inside.toString())["relation"].asString())
        assertTrue(far.toString() !in buffered)
        assertEquals(before, memberCount(area))
    }

    @Test
    fun `un area sin poligono sugiere por los componentes del proyecto`() {
        val project = runBlocking { utmProject() }
        runBlocking { component(project, uniqueName("PLT-"), "PLATAFORMA", rect(20000.0, 0.0, 20200.0, 200.0)) }
        val area = area(project)
        val under = localityWith("Caserío Ejemplo Bajo", location = at(20100.0, 100.0))
        assertEquals("INTERSECTS", suggestions(area).getValue(under.toString())["relation"].asString())
    }

    @Test
    fun `los errores de las sugerencias`() {
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, 0.0, 1000.0, 1000.0, multi = true))
        assertEquals("AREA_NOT_FOUND", failing("/api/sgspe/areas-of-influence/${UUID.randomUUID()}/member-suggestions", HttpStatus.NOT_FOUND))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/areas-of-influence/$area/member-suggestions?bufferM=0", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/areas-of-influence/$area/member-suggestions?radio=5").status)
    }

    @Test
    fun `buffer-localities trae las localidades a menos del buffer de los componentes`() {
        val project = runBlocking { utmProject() }
        runBlocking { component(project, uniqueName("ACC-"), "ACCESO", path(30000.0 to 0.0, 31000.0 to 0.0)) }
        val near = localityWith("Caserío Ejemplo Junto", location = at(30500.0, 300.0))
        val far = localityWith("Caserío Ejemplo Aparte", location = at(30500.0, 2000.0))

        fun found(bufferM: Int): Set<String> =
            get("/api/sgspe/territory/projects/$project/buffer-localities?bufferM=$bufferM")["localities"].values().map { it["id"].asString() }.toSet()
        assertTrue(near.toString() in found(500))
        assertTrue(far.toString() !in found(500))
        assertTrue(near.toString() !in found(100))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/$project/buffer-localities?bufferM=20001", HttpStatus.BAD_REQUEST))
    }

    @Test
    fun `el buffer de un proyecto sin working_crs es 409`() {
        val project = project()
        assertEquals("WORKING_CRS_REQUIRED", failing("/api/sgspe/territory/projects/$project/buffer-localities?bufferM=500", HttpStatus.CONFLICT))
        val area = area(UUID.fromString(project), boundary = rect(0.0, 0.0, 1000.0, 1000.0, multi = true))
        assertEquals("WORKING_CRS_REQUIRED", failing("/api/sgspe/areas-of-influence/$area/member-suggestions?bufferM=500", HttpStatus.CONFLICT))
    }
}
