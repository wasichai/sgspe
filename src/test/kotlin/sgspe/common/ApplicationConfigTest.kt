package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

// plan corto de wasichai 1.1.0 (decisión 3, security.md §9.7): wasichai guarda las definiciones 30 s, la ventana que el
// alcance de lectura ya aceptaba, y sgspe no tiene caché propia de definiciones. lee el yaml tal cual, sin levantar la app
class ApplicationConfigTest {
    private val config = YamlPropertySourceLoader().load("application", ClassPathResource("application.yml")).single()

    @Test
    fun `wasichai guarda las definiciones 30 s y sgspe no tiene una cache propia`() {
        assertEquals("30s", config.getProperty("wasichai.metadata.cache.ttl")?.toString())
        assertNull(config.getProperty("sgspe.metadata.definition-ttl"))
        assertNull(config.getProperty("sgspe.metadata.definition-max-entries"))
    }
}
