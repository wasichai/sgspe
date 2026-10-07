package sgspe

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

// RQ-SEC-001: con el perfil prod y sin WASICHAI_JWT_SECRET la app no arranca. levanta la app de verdad (SgspeApplication),
// pero sin alcanzar ninguna base: el puerto de la base es uno cerrado, así que la prueba solo pasa si el contexto cae por
// el secreto y no por la conexión (se comprueba el mensaje de la causa). por eso es unitaria y corre siempre
class ProdStartupTest {
    @Test
    fun `sin WASICHAI_JWT_SECRET el perfil prod no arranca`() {
        assumeTrue(System.getenv("WASICHAI_JWT_SECRET") == null, "la variable está definida en este entorno")
        val failure =
            runCatching {
                SpringApplicationBuilder(SgspeApplication::class.java)
                    .profiles("prod")
                    .web(WebApplicationType.REACTIVE)
                    .properties(
                        "server.port=0",
                        "WASICHAI_DB_HOST=localhost",
                        "WASICHAI_DB_PORT=1",
                        "WASICHAI_DB_NAME=prueba",
                        "WASICHAI_DB_USERNAME=prueba",
                        "WASICHAI_DB_PASSWORD=prueba"
                    ).run()
                    .close()
            }.exceptionOrNull()
        assertTrue(failure != null, "el contexto arrancó sin secreto")
        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { it.contains("wasichai.security.jwt.secret") }, messages.toString())
    }
}
