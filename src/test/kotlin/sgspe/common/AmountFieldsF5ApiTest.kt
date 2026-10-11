package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ApiTest

// AmountFieldsGuard con los montos de BC-10 por la API genérica (elección 11, ASM-092)
class AmountFieldsF5ApiTest : F5ApiTest() {
    private fun create(
        objectName: String,
        attributes: Map<String, Any?>
    ): Response = exchange("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), token)

    @Test
    fun `un item con monto negativo y un comprobante con un texto no se guardan`() {
        val id = socialContribution(testProject())
        val item = create("contribution_item", mapOf("social_contribution" to id, "description" to "Útiles", "item_kind" to "BIEN", "line_amount" to -1))
        assertEquals(HttpStatus.BAD_REQUEST, item.status, item.body)
        assertEquals("OUT_OF_RANGE", code(item))
        assertEquals(listOf("line_amount"), problemFields(item.body))
        val receipt = create("receipt", mapOf("social_contribution" to id, "amount" to "mil", "currency" to "PEN"))
        assertEquals("INVALID_NUMBER", code(receipt))
        assertEquals(listOf("amount"), problemFields(receipt.body))
        assertEquals(HttpStatus.CREATED, create("receipt", mapOf("social_contribution" to id, "amount" to "250.50", "currency" to "PEN")).status)
    }
}
