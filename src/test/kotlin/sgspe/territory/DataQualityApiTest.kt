package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6ApiTest
import sgspe.SyntheticGeo
import tools.jackson.databind.JsonNode

// especificación de F6 §5 «DataQualityApiTest» (gis.md §8): las cinco revisiones con registros sintéticos. el tenant es
// compartido: cada prueba busca sus ids recorriendo las páginas (o filtra por proyecto)
class DataQualityApiTest : F6ApiTest() {
    private fun all(
        check: String,
        extra: String = "",
        token: String = this.token
    ): List<JsonNode> {
        val rows = mutableListOf<JsonNode>()
        var page = 0
        do {
            val body = tree(send("GET", "/api/sgspe/territory/data-quality?check=$check&size=200&page=$page$extra", null, HttpStatus.OK, token))
            rows += body["content"].values()
            page++
        } while (body["hasNext"].asBoolean())
        return rows
    }

    private fun row(
        rows: List<JsonNode>,
        id: String
    ): JsonNode? = rows.firstOrNull { it["id"].asString() == id }

    @Test
    fun `un predio con un lazo sale como INVALID con su motivo`() {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val bowtie =
            mapOf(
                "type" to "Polygon",
                "coordinates" to
                    listOf(listOf(listOf(lon, lat), listOf(lon + 0.01, lat + 0.01), listOf(lon + 0.01, lat), listOf(lon, lat + 0.01), listOf(lon, lat)))
            )
        val id = createWith("land_parcel", mapOf("code" to uniqueName("PRD-LAZO"), "tenure_type" to "PROPIEDAD"), mapOf("boundary" to bowtie))
        val found = row(all("INVALID", "&object=land_parcel"), id)!!
        assertEquals("land_parcel", found["object"].asString())
        assertEquals("boundary", found["field"].asString())
        assertEquals("autointersección", found["detail"].asString())
        // con project, los predios (del tenant, sin proyecto) se omiten
        val project = runBlocking { utmProject() }
        assertTrue(all("INVALID", "&object=land_parcel&project=$project").isEmpty())
    }

    @Test
    fun `una localidad fuera del Peru sale como OUT_OF_ZONE`() {
        val id = runBlocking { locality(uniqueName("Localidad lejana"), SyntheticGeo.point(10.0, 10.0)).toString() }
        val found = row(all("OUT_OF_ZONE", "&object=locality"), id)!!
        assertEquals("fuera de la región esperada (envolvente del Perú)", found["detail"].asString())
    }

    @Test
    fun `un area guardada distinta del recalculo sale como STALE_AREA`() {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val id = runBlocking { communityWithTerritory(uniqueName("Comunidad Ejemplo"), SyntheticGeo.multiSquare(lon, lat + 0.05, 300.0)).toString() }
        platformPatch("community", id, mapOf("calculated_area_ha" to 1.23))
        val found = row(all("STALE_AREA", "&object=community"), id)!!
        val detail = found["detail"].asString()
        assertTrue(detail.startsWith("guardada 1.23 ha; recalculada "), detail)
        // el cuadrado de SyntheticGeo es de 300 m con la aproximación equirectangular: 9 ha con menos de 1 % de error
        assertEquals(9.0, detail.substringAfter("recalculada ").substringBefore(" ha").toDouble(), 0.1)
    }

    @Test
    fun `una queja sin localidad, comunidad ni ubicacion sale como NO_TERRITORY y con proyecto se filtra`() {
        val projectId = runBlocking { utmProject() }
        val project = projectId.toString()
        val grievance = grievance(project)
        val rows = all("NO_TERRITORY", "&project=$project")
        assertEquals(setOf(grievance), rows.map { it["id"].asString() }.toSet())
        assertEquals("sin localidad, comunidad ni ubicación", rows.single()["detail"].asString())
    }

    @Test
    fun `localidades y comunidades sin geometria salen como NO_GEOMETRY y el chequeo se valida`() {
        val locality = runBlocking { locality(uniqueName("Localidad sin punto"), null).toString() }
        val community = runBlocking { communityWithTerritory(uniqueName("Comunidad sin territorio"), null).toString() }
        val rows = all("NO_GEOMETRY")
        assertTrue(row(rows, locality) != null && row(rows, community) != null)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/territory/data-quality?check=OTRO").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/territory/data-quality?check=INVALID&object=land_right").status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/data-quality?check=INVALID&orden=id").status)
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/territory/data-quality?check=INVALID&object=land_parcel", null, auditor.token).status)
    }
}
