package sgspe.common

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException

// los atributos de un comando: un nombre que el objeto no tiene, un derivado o uno que fija el comando son un 400 que lo nombra
class StrictBodyRequireAttributesTest {
    private val catalog =
        ModelCatalog.parse(
            """
            {"objects":[{"name":"delivery","fields":[
               {"name":"notes","type":"TEXT"},
               {"name":"delivered_amount","type":"DECIMAL"},
               {"name":"status_label","type":"TEXT","derived":true}]}],
             "relationships":[{"name":"delivery_commitment","source":"delivery","target":"commitment","fieldName":"commitment"}]}
            """.trimIndent(),
            """{"roles":[{"name":"APPROVER"}]}""",
            emptyMap()
        )

    private fun violations(
        attributes: Map<String, Any?>,
        fixed: Set<String> = emptySet(),
        prefix: String = "attributes."
    ) = assertThrows<ValidationException> { StrictBody.requireAttributes(catalog, "delivery", attributes, fixed, prefix) }.violations.map {
        it.field to
            it.message
    }

    @Test
    fun `campos y relaciones MANY_TO_ONE del objeto pasan`() {
        assertDoesNotThrow { StrictBody.requireAttributes(catalog, "delivery", mapOf("notes" to "x", "commitment" to "id")) }
    }

    @Test
    fun `un nombre desconocido, un derivado y uno fijo se nombran con su prefijo, en orden`() {
        val found = violations(mapOf("zeta" to 1, "status_label" to "x", "delivered_amount" to 1), fixed = setOf("delivered_amount"))
        assertEquals(
            listOf(
                "attributes.delivered_amount" to "lo fija este comando",
                "attributes.status_label" to "es derivado: lo calcula SGSPE",
                "attributes.zeta" to "no es un campo de delivery"
            ),
            found
        )
    }

    @Test
    fun `el prefijo se puede cambiar`() {
        assertEquals(listOf("body.zeta"), violations(mapOf("zeta" to 1), prefix = "body.").map { it.first })
    }
}
