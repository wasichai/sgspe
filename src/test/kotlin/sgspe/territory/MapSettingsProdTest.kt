package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

// RQ-GIS-013 y ASM-048: producción nunca usa las teselas públicas de OpenStreetMap; el estilo lo nombra el despliegue
class MapSettingsProdTest {
    private val prod = YamlPropertySourceLoader().load("prod", ClassPathResource("application-prod.yml")).single()

    @Test
    fun `produccion no nombra tile openstreetmap org`() {
        val text = ClassPathResource("application-prod.yml").inputStream.use { String(it.readAllBytes()) }
        assertFalse(text.contains("tile.openstreetmap.org"), "application-prod.yml no debe nombrar las teselas de OpenStreetMap")
    }

    @Test
    fun `el estilo de produccion viene del despliegue y no tiene valor por defecto`() {
        assertEquals("\${SGSPE_MAP_BASE_STYLE_URL:}", prod.getProperty("sgspe.map.base-style-url")?.toString())
    }
}
