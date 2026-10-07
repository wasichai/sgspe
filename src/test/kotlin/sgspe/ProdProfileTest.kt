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

    // ningún dato de conexión tiene valor por defecto: sin la variable la app no arranca, y nunca cae en el sgspe/sgspe de
    // desarrollo de application.yml. el puerto no es secreto: lleva el estándar de PostgreSQL, no el 5435 del túnel
    @Test
    fun `la base de datos no tiene valores por defecto`() {
        assertEquals("\${WASICHAI_DB_HOST}", value("wasichai.database.host"))
        assertEquals("\${WASICHAI_DB_NAME}", value("wasichai.database.name"))
        assertEquals("\${WASICHAI_DB_USERNAME}", value("wasichai.database.username"))
        assertEquals("\${WASICHAI_DB_PASSWORD}", value("wasichai.database.password"))
        assertEquals("\${WASICHAI_DB_PORT:5432}", value("wasichai.database.port"))
        for (name in listOf("host", "name", "username", "password")) {
            assertFalse(value("wasichai.database.$name")!!.contains(":"), name)
        }
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
