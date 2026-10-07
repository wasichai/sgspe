package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import wasichai.test.WasichaiIntegrationTest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// RQ-SEC-001 y RQ-SEC-017: contraseña errada, usuario deshabilitado y token alterado o vencido responden 401
class SecurityApiTest : SgspeApiTest() {
    private fun login(
        email: String,
        password: String
    ) = exchange("POST", "/api/auth/login", mapOf("email" to email, "password" to password), token = null)

    @Test
    fun `una contrasena errada responde 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, login(WasichaiIntegrationTest.ADMIN_EMAIL, "errada-de-prueba").status)
    }

    @Test
    fun `un usuario deshabilitado no entra`() {
        val user = userWithRole("READ_ONLY")
        send("PUT", "/api/users/${user.id}", mapOf("enabled" to false), HttpStatus.OK)
        assertEquals(HttpStatus.UNAUTHORIZED, login(user.email, PASSWORD).status)
    }

    @Test
    fun `un token emitido antes de deshabilitar al usuario sigue entrando hasta que vence`() {
        // PIN DEL COMPORTAMIENTO ACTUAL de wasichai 0.3.2, no un requisito: el JWT es sin estado y no hay revocación
        // (UPS-09, wasichai#55; architecture.md, riesgo 9). deshabilitar a alguien le cierra el login, no su sesión viva.
        // SGSPE lo mitiga con TTL corto y límite de tasa en el proxy. cuando wasichai revoque, esta prueba falla: pasar a
        // esperar 401 (RQ-SEC-001) y quitar la mitigación que ya no haga falta
        val user = userWithRole("READ_ONLY")
        assertEquals(HttpStatus.OK, exchange("GET", "/api/auth/me", token = user.token).status)
        send("PUT", "/api/users/${user.id}", mapOf("enabled" to false), HttpStatus.OK)
        assertEquals(HttpStatus.UNAUTHORIZED, login(user.email, PASSWORD).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/auth/me", token = user.token).status)
    }

    @Test
    fun `un token valido devuelve a quien llama`() {
        val yo = me()
        assertEquals(WasichaiIntegrationTest.ADMIN_EMAIL, yo["email"].asString())
        assertTrue(yo["organizationId"].asString().isNotBlank())
    }

    @Test
    fun `sin token, con un token alterado, con basura o con uno vencido la api responde 401`() {
        val tampered = token.dropLast(2) + if (token.endsWith("AA")) "BB" else "AA"
        for (bearer in listOf(null, tampered, "Bearer no-es-un-jwt", signed(expiresIn = -3600))) {
            assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/auth/me", token = bearer).status, "$bearer")
        }
    }

    @Test
    fun `el mismo token firmado igual pero vigente entra`() {
        // control de la prueba anterior: el vencido se rechaza por su fecha, no por su forma
        assertEquals(HttpStatus.OK, exchange("GET", "/api/auth/me", token = signed(expiresIn = 3600)).status)
    }

    @Test
    fun `las sondas de salud no piden token`() {
        for (path in listOf("/actuator/health/liveness", "/actuator/health/readiness")) {
            val response = exchange("GET", path, token = null)
            assertEquals(HttpStatus.OK, response.status, path)
            assertEquals("UP", tree(response.body)["status"].asString(), path)
        }
    }

    // un JWT HS256 con la forma del de wasichai (JwtService: sub, iss, iat, exp, org, email y roles), firmado con el secreto
    // fijo de WasichaiIntegrationTest. solo JDK: ninguna clase interna de wasichai
    private fun signed(expiresIn: Long): String {
        val yo = me()
        val now = Instant.now().epochSecond
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString(JSON.writeValueAsBytes(mapOf("alg" to "HS256", "typ" to "JWT")))
        val claims =
            encoder.encodeToString(
                JSON.writeValueAsBytes(
                    mapOf(
                        "sub" to yo["userId"].asString(),
                        "iss" to "wasichai",
                        "iat" to now - 7200,
                        "exp" to now + expiresIn,
                        "org" to yo["organizationId"].asString(),
                        "email" to yo["email"].asString(),
                        "roles" to yo["roles"].values().map { it.asString() }
                    )
                )
            )
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(WasichaiIntegrationTest.TEST_JWT_SECRET.toByteArray(), "HmacSHA256"))
        val signature = encoder.encodeToString(mac.doFinal("$header.$claims".toByteArray()))
        return "Bearer $header.$claims.$signature"
    }
}
