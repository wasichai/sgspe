package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration

// RQ-SEC-016, RQ-SEC-001 y ADR-0020: el perfil de producción no tiene semilla ni secreto por defecto, acorta el token y
// escribe los logs en ECS. lee el yaml tal cual, sin levantar la app
class ProdProfileTest {
    private val prod = YamlPropertySourceLoader().load("prod", ClassPathResource("application-prod.yml")).single()

    private fun value(name: String): String? = prod.getProperty(name)?.toString()

    @Test
    fun `apaga la semilla de desarrollo`() {
        assertEquals("false", value("wasichai.seed.dev"))
    }

    @Test
    fun `exige el secreto JWT sin valor por defecto`() {
        assertEquals("\${WASICHAI_JWT_SECRET}", value("wasichai.security.jwt.secret"))
    }

    @Test
    fun `el token dura menos que las 8 horas por defecto`() {
        val ttl = Duration.parse(value("wasichai.security.jwt.ttl"))
        assertTrue(ttl.isPositive && ttl < Duration.ofHours(8), "$ttl")
    }

    @Test
    fun `escribe los logs en ECS`() {
        assertEquals("ecs", value("logging.structured.format.console"))
    }

    @Test
    fun `no existe un perfil llamado test`() {
        // un perfil llamado test apaga beans de algunas librerías sin avisar (wasichai docs/modules/testing.md)
        for (name in listOf("application-test.yml", "application-test.yaml", "application-test.properties")) {
            assertFalse(ClassPathResource(name).exists(), name)
        }
    }
}
