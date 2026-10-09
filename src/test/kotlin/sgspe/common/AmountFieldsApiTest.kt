package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// AmountFieldsGuard por la API genérica (ruling P58): un monto negativo o con más de cuatro decimales es un 400 con el código
// de Amounts sobre el campo (P15), en el alta y en el cambio; antes, DECIMAL lo aceptaba y la campaña quedaba con un tope que
// SupportPolicyGuard no puede leer
class AmountFieldsApiTest : F3DomainApiTest() {
    private fun campaign(cap: Any?) =
        exchange(
            "POST",
            "/api/objects/exploration_campaign/records",
            mapOf("attributes" to mapOf("project" to testProject(), "name" to "Campaña de prueba", "social_support_cap" to cap, "currency" to "PEN"))
        )

    @Test
    fun `un tope de apoyo social negativo no se guarda`() {
        val refused = campaign(-1)
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("social_support_cap"), problemFields(refused.body))
    }

    @Test
    fun `un monto de compromiso con cinco decimales no se guarda`() {
        val owner = userWithRole("COMMITMENT_MANAGER")
        val verifier = userWithRole("COMMITMENT_MANAGER")
        val refused =
            exchange(
                "POST",
                "/api/objects/commitment/records",
                mapOf("attributes" to commitmentAttributes(testProject(), owner, verifier) + mapOf("amount" to 1000.12345)),
                token,
                withReason("alta de prueba")
            )
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("amount"), problemFields(refused.body))
    }

    @Test
    fun `cambiar el tope a cinco decimales se rechaza y editar otro campo con un tope valido pasa`() {
        val created = campaign("1500.50")
        assertEquals(HttpStatus.CREATED, created.status, created.body)
        val id = tree(created.body)["id"].asString()
        val refused = edit("exploration_campaign", id, mapOf("social_support_cap" to "1000.12345"))
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("social_support_cap"), problemFields(refused.body))
        val renamed = edit("exploration_campaign", id, mapOf("name" to "Campaña renombrada"))
        assertEquals(HttpStatus.OK, renamed.status, renamed.body)
    }
}
