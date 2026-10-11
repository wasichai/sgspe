package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// gis.md §9 «Polígono inválido» y especificación de F6 §5 «GeometryChecksApiTest» (RQ-GIS-011, RQ-GIS-020): lazo inválido con
// su motivo y la reparación con su cambio de área; MultiPolygon de una parte normalizado; fuera de zona y de región; no
// escribe. geometrías sintéticas cerca de (-78.50, -7.90), UTM 17S
class GeometryChecksApiTest : DomainApiTest() {
    private fun check(
        body: Map<String, Any?>,
        token: String = this.token
    ) = exchange("POST", "/api/sgspe/territory/geometry-checks", body, token)

    private val bowtie =
        mapOf(
            "type" to "Polygon",
            "coordinates" to listOf(listOf(listOf(-78.50, -7.90), listOf(-78.49, -7.89), listOf(-78.49, -7.90), listOf(-78.50, -7.89), listOf(-78.50, -7.90)))
        )

    @Test
    fun `un lazo es invalido con su motivo y la reparacion cambia el area`() {
        val project = projectWithCrs()
        val response = check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary", "project" to project))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertFalse(body["valid"].asBoolean())
        assertEquals("autointersección", body["reason"].asString())
        assertEquals("MultiPolygon", body["repaired"]["type"].asString())
        assertTrue(body["areaHaAfter"].asDouble() > body["areaHaBefore"].asDouble() + 10, body.toString())
        assertEquals(32717, body["zone"]["srid"].asInt())
        assertFalse(body["zone"]["outOfZone"].asBoolean())
        val parcels = tree(send("GET", "/api/objects/land_parcel/records?size=1", null, HttpStatus.OK))["totalElements"].asLong()
        check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary"))
        assertEquals(parcels, tree(send("GET", "/api/objects/land_parcel/records?size=1", null, HttpStatus.OK))["totalElements"].asLong())
    }

    @Test
    fun `un MultiPolygon de una parte se normaliza y un punto no es del tipo`() {
        val square = listOf(listOf(listOf(-78.50, -7.90), listOf(-78.49, -7.90), listOf(-78.49, -7.89), listOf(-78.50, -7.89), listOf(-78.50, -7.90)))
        val one =
            tree(
                check(
                    mapOf(
                        "geometry" to mapOf("type" to "MultiPolygon", "coordinates" to listOf(square)),
                        "object" to "land_parcel",
                        "field" to "boundary"
                    )
                ).body
            )
        assertTrue(one["valid"].asBoolean())
        assertTrue(one["typeMatches"].asBoolean())
        assertEquals("Polygon", one["normalized"]["type"].asString())
        assertTrue(one["repaired"].isNull)
        val point =
            tree(
                check(
                    mapOf(
                        "geometry" to mapOf("type" to "Point", "coordinates" to listOf(-78.5, -7.9)),
                        "object" to "land_parcel",
                        "field" to "boundary"
                    )
                ).body
            )
        assertFalse(point["typeMatches"].asBoolean())
    }

    @Test
    fun `fuera de la zona del proyecto y fuera de la region`() {
        val project = projectWithCrs()
        val zone18 = mapOf("type" to "Point", "coordinates" to listOf(-75.50, -9.00))
        val outside = tree(check(mapOf("geometry" to zone18, "object" to "locality", "field" to "location", "project" to project)).body)
        assertTrue(outside["zone"]["outOfZone"].asBoolean())
        assertFalse(outside["zone"]["outOfRegion"].asBoolean())
        val far =
            tree(check(mapOf("geometry" to mapOf("type" to "Point", "coordinates" to listOf(10.0, 10.0)), "object" to "locality", "field" to "location")).body)
        assertTrue(far["zone"]["outOfRegion"].asBoolean())
    }

    @Test
    fun `sin READ es 403, un campo que no es geometria es 400 y una propiedad desconocida tambien`() {
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.FORBIDDEN, check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary"), auditor.token).status)
        val code = check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "code"))
        assertEquals(HttpStatus.BAD_REQUEST, code.status)
        assertEquals(listOf("field"), problemFields(code.body))
        assertEquals(HttpStatus.BAD_REQUEST, check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary", "repair" to true)).status)
    }
}
