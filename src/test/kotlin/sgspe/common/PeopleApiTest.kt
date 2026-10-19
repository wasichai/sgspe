package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest

// GET /api/sgspe/people (api.md §4.1, ASM-142): el nombre visible de las personas de la organización por su correo, para que
// el portal diga «Ana Pérez» donde hoy dice el correo. solo las de la organización de quien llama; un correo desconocido o de
// otro tenant no sale
class PeopleApiTest : SgspeApiTest() {
    private fun people(
        emails: List<String>,
        token: String
        // el cliente de prueba codifica la URL: el correo va tal cual (un %40 llegaría como %2540)
    ) = tree(send("GET", "/api/sgspe/people?" + emails.joinToString("&") { "email=$it" }, null, HttpStatus.OK, token))

    @Test
    fun `quien no administra lee el nombre de las personas de su organizacion por su correo`() {
        val lectora = userWithRole("READ_ONLY")
        val responsable = userWithRole("COMMITMENT_MANAGER")
        val otroTenant = provisionTenant()
        val body = people(listOf(responsable.email, lectora.email.uppercase(), otroTenant.email, "nadie@sgspe.test"), lectora.token)
        val porCorreo = body.values().associate { it["email"].asString() to it["displayName"].asString() }
        assertEquals(mapOf(responsable.email to "Usuario de prueba", lectora.email to "Usuario de prueba"), porCorreo)
    }

    @Test
    fun `sin correos responde una lista vacia y con mas de cien o un parametro desconocido 422`() {
        val lectora = userWithRole("READ_ONLY")
        assertEquals(0, tree(send("GET", "/api/sgspe/people", null, HttpStatus.OK, lectora.token)).size())
        val muchos = (1..101).joinToString("&") { "email=p$it@sgspe.test" }
        send("GET", "/api/sgspe/people?$muchos", null, HttpStatus.valueOf(422), lectora.token)
        send("GET", "/api/sgspe/people?nombre=x", null, HttpStatus.valueOf(422), lectora.token)
    }

    @Test
    fun `sin token responde 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/sgspe/people?email=a@sgspe.test", null, null, emptyMap()).status)
    }
}
