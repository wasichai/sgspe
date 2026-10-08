package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest

// el tope de elementos por capa (api.md §4.2), bajado a 250 para no crear 5000 registros. 250 pasa de una página de 200, así
// que prueba que la segunda página no repite filas (wasichai desplaza page * size) y que `truncated` dice la verdad:
// verdadero con 260 en el bbox, falso con exactamente 250
@TestPropertySource(properties = ["sgspe.territory.layers.max-features=250"])
class TerritoryLayersCapApiTest : DomainApiTest() {
    @Test
    fun `el tope corta sin repetir elementos y truncated solo es verdadero si habia mas`() {
        // 260 vías en una fila de longitud, lejos de los datos de las demás pruebas; la i-ésima en -60 + i * 0.001
        repeat(260) { i ->
            val lon = -60.0 + i * 0.001
            val trace = mapOf("type" to "LineString", "coordinates" to listOf(listOf(lon, -5.0), listOf(lon + 0.0001, -5.0001)))
            createWith("road_access", sample("road_access"), mapOf("trace" to trace))
        }
        val all = tree(send("GET", "/api/sgspe/territory/layers/LYR-04?bbox=-60.1,-5.1,-59.0,-4.9", null, HttpStatus.OK))
        val ids = all["features"].values().map { it["id"].asString() }
        assertEquals(250, ids.size)
        assertEquals(250, ids.toSet().size, "ids repetidos entre páginas")
        assertEquals(true, all["truncated"].asBoolean())

        // las primeras 250 vías (i = 0..249) caben justo en el tope
        val exact = tree(send("GET", "/api/sgspe/territory/layers/LYR-04?bbox=-60.1,-5.1,-59.7505,-4.9", null, HttpStatus.OK))
        val exactIds = exact["features"].values().map { it["id"].asString() }
        assertEquals(250, exactIds.size)
        assertEquals(250, exactIds.toSet().size)
        assertEquals(false, exact["truncated"].asBoolean())
    }
}
