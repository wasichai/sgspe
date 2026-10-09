package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

// ruling P58: toda escritura valida los montos con las cotas de Amounts (P44). DECIMAL de wasichai es un numeric sin escala ni
// mínimo, y un tope de -1 o de 1000.12345 dejaría a SupportPolicyGuard contestando CAP_DATA_INVALID en cada aprobación de la
// campaña. un valor que la escritura no cambia no se juzga: un dato heredado no bloquea una edición ajena
class AmountFieldsGuardTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String> = after.keys
    ): List<Pair<String?, String>> = AmountFieldsGuard.findings(objectName, after, changed).map { it.field to it.code }

    @Test
    fun `un tope negativo o con cinco decimales es OUT_OF_RANGE sobre el campo, y un texto INVALID_NUMBER`() {
        assertEquals(listOf("social_support_cap" to "OUT_OF_RANGE"), codes("exploration_campaign", mapOf("social_support_cap" to -1)))
        assertEquals(listOf("social_support_cap" to "OUT_OF_RANGE"), codes("exploration_campaign", mapOf("social_support_cap" to "1000.12345")))
        assertEquals(listOf("amount" to "OUT_OF_RANGE"), codes("commitment", mapOf("amount" to 1000.12345)))
        assertEquals(listOf("delivered_amount" to "INVALID_NUMBER"), codes("delivery", mapOf("delivered_amount" to "mil")))
        assertEquals(listOf("amount" to "OUT_OF_RANGE"), codes("agreement", mapOf("amount" to "-0.5")))
        val finding = AmountFieldsGuard.findings("commitment", mapOf("amount" to -1), setOf("amount")).single()
        assertEquals(HttpStatus.BAD_REQUEST, finding.status)
        assertTrue(finding.blocking)
    }

    @Test
    fun `un monto valido, vacio o con ceros finales pasa`() {
        assertEquals(emptyList<Pair<String?, String>>(), codes("exploration_campaign", mapOf("social_support_cap" to 1500)))
        assertEquals(emptyList<Pair<String?, String>>(), codes("commitment", mapOf("amount" to "1000.12340")))
        assertEquals(emptyList<Pair<String?, String>>(), codes("delivery", mapOf("delivered_amount" to null)))
        assertEquals(emptyList<Pair<String?, String>>(), codes("agreement", mapOf("amount" to "0")))
    }

    @Test
    fun `un valor que la escritura no cambia no se juzga`() {
        assertEquals(
            emptyList<Pair<String?, String>>(),
            codes("exploration_campaign", mapOf("social_support_cap" to -1, "name" to "Campaña"), changed = setOf("name"))
        )
    }

    @Test
    fun `los derivados y los decimales que no son montos no se juzgan`() {
        assertEquals(emptyList<Pair<String?, String>>(), codes("commitment", mapOf("executed_amount" to -1, "quantity" to "1.123456")))
        assertEquals(emptyList<Pair<String?, String>>(), codes("exploration_campaign", mapOf("executed_social_amount" to -1)))
        assertEquals(emptyList<Pair<String?, String>>(), codes("stakeholder_assessment", mapOf("interest_index" to -1)))
    }

    // la lista de montos vive en AmountFieldsGuard.FIELDS porque el modelo no marca qué DECIMAL es dinero: cada campo listado
    // existe, es DECIMAL y no es derivado (los derivados los escribe solo la plataforma con la marca de escritura)
    @Test
    fun `cada monto listado existe en el modelo, es DECIMAL y no es derivado`() {
        val wrong =
            AmountFieldsGuard.FIELDS.flatMap { (objectName, fields) ->
                fields.mapNotNull { name ->
                    val field = catalog.objectOrNull(objectName)?.fields?.get(name)
                    when {
                        field == null -> "$objectName.$name no existe"
                        field.type != "DECIMAL" -> "$objectName.$name es ${field.type}"
                        field.derived -> "$objectName.$name es derivado"
                        else -> null
                    }
                }
            }
        assertEquals(emptyList<String>(), wrong)
        assertEquals(
            mapOf(
                "exploration_campaign" to setOf("social_support_cap"),
                "commitment" to setOf("amount"),
                "delivery" to setOf("delivered_amount"),
                "agreement" to setOf("amount")
            ),
            AmountFieldsGuard.FIELDS
        )
    }

    // la completitud: cada DECIMAL no derivado del modelo es un monto (FIELDS) o está en NOT_MONEY con su motivo. un DECIMAL
    // nuevo (los montos de F4: receipt.amount, contribution_item.line_amount, program_budget.*) hace fallar esta prueba hasta
    // que alguien lo clasifique, así un monto no entra al modelo sin cotas
    @Test
    fun `todo DECIMAL no derivado del modelo es un monto de FIELDS o esta en NOT_MONEY con su motivo`() {
        val decimals =
            catalog.objects.values
                .flatMap { model ->
                    model.fields.values
                        .filter { it.type == "DECIMAL" && !it.derived }
                        .map { "${model.name}.${it.name}" }
                }.toSet()
        val money = AmountFieldsGuard.FIELDS.flatMap { (objectName, fields) -> fields.map { "$objectName.$it" } }.toSet()
        assertEquals(emptySet<String>(), decimals - money - AmountFieldsGuard.NOT_MONEY.keys, "clasifíquelos en FIELDS o en NOT_MONEY")
        assertEquals(emptySet<String>(), AmountFieldsGuard.NOT_MONEY.keys - decimals, "NOT_MONEY nombra campos que no son DECIMAL no derivados")
        assertEquals(emptySet<String>(), AmountFieldsGuard.NOT_MONEY.keys.intersect(money), "un campo no puede ser monto y no serlo")
        assertEquals(
            emptyList<String>(),
            AmountFieldsGuard.NOT_MONEY
                .filterValues { it.isBlank() }
                .keys
                .toList(),
            "cada uno lleva su motivo"
        )
    }
}
