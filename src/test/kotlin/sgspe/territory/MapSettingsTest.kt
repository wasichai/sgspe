package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

// el mapa base por organización (gis.md §6.4, RQ-GIS-013, especificación de F6 §3.3): el de la organización, si no el
// general, y si no ninguno; nunca las teselas de OpenStreetMap
class MapSettingsTest {
    private val properties =
        MapProperties(
            baseStyleUrl = "https://estilos.sgspe.test/general.json",
            baseStyleUrlByOrganization = mapOf("demo" to "https://estilos.sgspe.test/demo.json"),
            attribution = mapOf("https://estilos.sgspe.test/demo.json" to "Mapa de prueba")
        )

    @Test
    fun `la organizacion con estilo propio lo recibe con su atribucion`() {
        val settings = MapSettings.resolve(properties, "demo")
        assertEquals(Basemap("style", "https://estilos.sgspe.test/demo.json"), settings.basemap)
        assertEquals("Mapa de prueba", settings.attribution)
    }

    @Test
    fun `otra organizacion recibe el general, sin atribucion configurada`() {
        val settings = MapSettings.resolve(properties, "otra")
        assertEquals(Basemap("style", "https://estilos.sgspe.test/general.json"), settings.basemap)
        assertNull(settings.attribution)
    }

    @Test
    fun `sin estilo general ni propio es none`() {
        val settings = MapSettings.resolve(MapProperties(baseStyleUrl = " "), "demo")
        assertEquals(Basemap("none"), settings.basemap)
        assertNull(settings.attribution)
    }

    // la clave de attribution es una URL: con corchetes conserva los puntos y las barras (así se escribe en el yaml)
    @Test
    fun `el yaml liga la atribucion por URL entre corchetes`() {
        val source =
            MapConfigurationPropertySource(
                mapOf(
                    "sgspe.map.base-style-url" to "https://estilos.sgspe.test/general.json",
                    "sgspe.map.base-style-url-by-organization.demo" to "https://estilos.sgspe.test/demo.json",
                    "sgspe.map.attribution[https://estilos.sgspe.test/demo.json]" to "Mapa de prueba"
                )
            )
        val bound = Binder(source).bind("sgspe.map", Bindable.of(MapProperties::class.java)).get()
        assertEquals("Mapa de prueba", bound.attribution["https://estilos.sgspe.test/demo.json"])
        assertEquals("https://estilos.sgspe.test/demo.json", bound.baseStyleUrlByOrganization["demo"])
    }
}
