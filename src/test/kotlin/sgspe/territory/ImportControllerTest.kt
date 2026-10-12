package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import wasichai.core.common.GlobalExceptionHandler

// lo que ImportController agrega en F6 y no necesita base: las partes del multipart, imports/formats y el problema con las capas
class ImportControllerTest {
    private val problems = GlobalExceptionHandler("https://sgspe.test/problems")

    private fun controller(path: String) =
        ImportController(mock(TerritoryImports::class.java), ImportProperties(), GdalTools(GdalProperties(path = path)), problems)

    private fun exchange(query: String = "") = MockServerWebExchange.from(MockServerHttpRequest.get("/api/sgspe/territory/imports/formats$query"))

    @Test
    fun `la parte layer se admite en la vista previa y en la confirmacion`() {
        assertTrue("layer" in ImportController.PREVIEW_PARTS)
        assertTrue("layer" in ImportController.CONFIRM_PARTS)
        // la confirmación llega a 8 partes y spring.webflux.multipart.max-parts es 8
        assertEquals(8, ImportController.CONFIRM_PARTS.size)
    }

    @Test
    fun `sin GDAL imports formats dice que solo los formatos de F2 estan disponibles`() {
        val formats = runBlocking { controller("/sgspe-sin-gdal").formats(exchange()) }["formats"] as List<*>
        val byKey = formats.map { it as Map<*, *> }.associateBy { it["format"] }
        assertEquals(ImportFormat.entries.map { it.key }.toSet(), byKey.keys)
        assertTrue(byKey.getValue("csv")["available"] as Boolean)
        assertFalse(byKey.getValue("shp")["available"] as Boolean)
        assertTrue(byKey.getValue("gpkg")["multiLayer"] as Boolean)
        assertTrue(byKey.getValue("shp")["crsFromFile"] as Boolean)
        assertFalse(byKey.getValue("kml")["crsFromFile"] as Boolean)
    }

    @Test
    fun `LAYER_REQUIRED sale como 400 con las capas y su CRS detectado`() {
        val layers = listOf(SourceLayer("a", "Polygon", 2, "EPSG 32717"), SourceLayer("b", "Point", 1, null))
        val problem = controller("").choiceRequired(ImportChoiceRequired.layer(layers))
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals(layers, problem.properties?.get("layers"))
    }
}
