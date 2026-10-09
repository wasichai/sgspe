package sgspe

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import sgspe.prod.ProdGuard

// RQ-SEC-001 y RQ-SEC-016: el perfil prod no arranca con el secreto ausente, con el secreto de desarrollo, con la semilla
// encendida, con un token de 8 horas o más ni sin el bucket de las evidencias (ADR-0014). levanta la app de verdad (SgspeApplication), pero sin alcanzar ninguna base:
// el puerto de la base es uno cerrado, así que cada caso solo pasa si el contexto cae por su causa y no por la conexión.
// ProdGuard corre como EnvironmentPostProcessor, antes de crear el contexto y por tanto antes de Flyway (la semilla de
// wasichai 0.3.2 es una migración): el mensaje de la causa es el de la guarda, no uno de Flyway ni de la conexión. es
// unitaria y corre siempre
class ProdStartupTest {
    private val validSecret = "un-secreto-de-produccion-de-mas-de-32-bytes"

    // los argumentos de línea de comandos mandan sobre application-prod.yml; las properties() del builder, no
    private fun start(
        vararg args: String,
        secret: String? = validSecret
    ): List<String> {
        val failure =
            runCatching {
                SpringApplicationBuilder(SgspeApplication::class.java)
                    .profiles("prod")
                    .web(WebApplicationType.REACTIVE)
                    .properties(
                        *listOfNotNull<String>(
                            "server.port=0",
                            "WASICHAI_DB_HOST=localhost",
                            "WASICHAI_DB_PORT=1",
                            "WASICHAI_DB_NAME=prueba",
                            "WASICHAI_DB_USERNAME=prueba",
                            "WASICHAI_DB_PASSWORD=prueba",
                            // el bucket de las evidencias (ADR-0014): sin él, ProdGuard sumaría su causa a la de cada caso
                            "WASICHAI_FILES_S3_BUCKET=prueba",
                            secret?.let { "WASICHAI_JWT_SECRET=$it" }
                        ).toTypedArray()
                    ).run(*args)
                    .close()
            }.exceptionOrNull()
        assertTrue(failure != null, "el contexto arrancó")
        return generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
    }

    private fun assertOnlyCause(
        messages: List<String>,
        cause: String,
        others: List<String>
    ) {
        assertTrue(messages.any { it.contains(cause) }, messages.toString())
        for (other in others) assertFalse(messages.any { it.contains(other) }, "$other en $messages")
        // la guarda corta antes de Flyway y de la base: ninguna causa es de conexión ni de migración
        assertFalse(messages.any { it.contains("Connection refused", ignoreCase = true) || it.contains("flyway", ignoreCase = true) }, messages.toString())
    }

    @Test
    fun `sin WASICHAI_JWT_SECRET el perfil prod no arranca`() {
        val messages = start(secret = null)
        assertOnlyCause(messages, "WASICHAI_JWT_SECRET", listOf("WASICHAI_SEED_DEV", "wasichai.security.jwt.ttl"))
        assertTrue(messages.any { it.contains("wasichai.security.jwt.secret") }, messages.toString())
    }

    @Test
    fun `con la semilla de desarrollo encendida el perfil prod no arranca`() {
        val messages = start("--wasichai.seed.dev=true")
        assertOnlyCause(messages, "WASICHAI_SEED_DEV", listOf("WASICHAI_JWT_SECRET", "wasichai.security.jwt.ttl"))
        assertTrue(messages.any { it.contains("wasichai.seed.dev") }, messages.toString())
    }

    @Test
    fun `con el secreto de desarrollo el perfil prod no arranca`() {
        val messages = start(secret = ProdGuard.DEV_SECRET)
        assertOnlyCause(messages, "secreto de desarrollo", listOf("WASICHAI_SEED_DEV", "wasichai.security.jwt.ttl"))
        assertTrue(messages.any { it.contains("wasichai.security.jwt.secret") && it.contains("WASICHAI_JWT_SECRET") }, messages.toString())
    }

    @Test
    fun `sin el bucket de las evidencias el perfil prod no arranca`() {
        val messages = start("--WASICHAI_FILES_S3_BUCKET=")
        assertOnlyCause(messages, "WASICHAI_FILES_S3_BUCKET", listOf("WASICHAI_SEED_DEV", "secreto de desarrollo", "wasichai.security.jwt.ttl"))
    }

    @Test
    fun `con un token de 8 horas el perfil prod no arranca`() {
        val messages = start("--wasichai.security.jwt.ttl=PT8H")
        assertOnlyCause(messages, "wasichai.security.jwt.ttl", listOf("WASICHAI_SEED_DEV", "secreto de desarrollo", "falta"))
        assertTrue(messages.any { it.contains("WASICHAI_SECURITY_JWT_TTL") }, messages.toString())
    }
}
