package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// gis.md §3.1 y §9 (las pruebas que no dependen de F6 ni del código del plan 2.2): las geometrías de model.json viven en
// SRID 4326 y 2D, la API habla GeoJSON en EPSG:4326, un MULTIPOLYGON entra por la API genérica (RQ-GIS-020) y el filtro
// bbox de wasichai-gis encuentra el registro. Geometrías sintéticas (RQ-GIS-021)
class GeometryApiTest : F2ApiTest() {
    // un cuadrado de unos 100 m de lado cerca de Otuzco (La Libertad), sintético
    private val square = listOf(listOf(-78.57, -7.90), listOf(-78.569, -7.90), listOf(-78.569, -7.899), listOf(-78.57, -7.899), listOf(-78.57, -7.90))

    @Test
    fun `cada geometria de la metadata esta en 4326 y 2D con el tipo de model json`() {
        // wasichai-gis informa la forma de un campo GEOMETRY en su propiedad `geometry`: type, srid y dimension
        for ((name, obj) in Model.objects) {
            @Suppress("UNCHECKED_CAST")
            val wanted =
                (obj["fields"] as List<Map<String, Any?>>)
                    .filter { it["type"] == "GEOMETRY" }
                    .associate { it["name"] as String to it["geometryType"] as String }
            val fields = tree(send("GET", "/api/metadata/objects/$name/fields", null, HttpStatus.OK)).values()
            val got =
                fields
                    .filter { it["type"].asString() == "GEOMETRY" }
                    .associate { it["name"].asString() to it["geometry"] }
            assertEquals(wanted.keys, got.keys, name)
            for ((field, geometry) in got) {
                assertEquals(wanted[field], geometry["type"].asString(), "$name.$field")
                assertEquals(4326, geometry["srid"].asInt(), "$name.$field")
                assertEquals(2, geometry["dimension"].asInt(), "$name.$field")
            }
        }
    }

    @Test
    fun `un territorio comunal MULTIPOLYGON entra por la API generica y vuelve en GeoJSON 4326`() {
        val body =
            mapOf(
                "attributes" to mapOf("name" to "Comunidad de prueba"),
                "geometries" to mapOf("territory" to mapOf("type" to "MultiPolygon", "coordinates" to listOf(listOf(square))))
            )
        val id = tree(send("POST", "/api/objects/community/records", body, HttpStatus.CREATED))["id"].asString()
        val stored = tree(send("GET", "/api/objects/community/records/$id", null, HttpStatus.OK))["geometries"]["territory"]
        assertEquals("MultiPolygon", stored["type"].asString())
        assertEquals(-78.57, stored["coordinates"][0][0][0][0].asDouble(), 1e-9)
    }

    @Test
    fun `bbox encuentra la localidad por su ubicacion`() {
        val name = uniqueName("Caserío ")
        val body =
            mapOf(
                "attributes" to mapOf("name" to name, "locality_type" to "CASERIO"),
                "geometries" to mapOf("location" to mapOf("type" to "Point", "coordinates" to listOf(-78.5695, -7.8995)))
            )
        val id = tree(send("POST", "/api/objects/locality/records", body, HttpStatus.CREATED))["id"].asString()
        val inside = tree(send("GET", "/api/objects/locality/records?bbox=-78.571,-7.901,-78.568,-7.898&geometry=location&size=200", null, HttpStatus.OK))
        assertTrue(inside["content"].values().any { it["id"].asString() == id })
        val outside = tree(send("GET", "/api/objects/locality/records?bbox=-70.0,-10.0,-69.9,-9.9&geometry=location&size=200", null, HttpStatus.OK))
        assertTrue(outside["content"].values().none { it["id"].asString() == id })
    }
}
