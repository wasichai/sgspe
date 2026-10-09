package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// ruling P58: toda escritura valida los montos con las cotas de Amounts (ruling P44: hasta 15 dígitos enteros y 4 decimales
// tras quitar los ceros finales, nunca negativo). DECIMAL de wasichai es un numeric sin escala ni mínimo, así que sin esta
// guarda la API genérica, /admin o un módulo guardarían un tope de -1 o de 1000.12345, y SupportPolicyGuard contestaría
// CAP_DATA_INVALID en cada aprobación de esa campaña. responde el código de Amounts (400 INVALID_NUMBER u OUT_OF_RANGE) sobre
// el campo (P15). un valor que la escritura no cambia no se juzga: un dato heredado no bloquea una edición de otro campo
@Component
@Order(GuardOrder.AMOUNTS)
class AmountFieldsGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink() || change.objectName !in FIELDS) return emptyList()
        val after = view.after(change)
        return findings(change.objectName, after, view.changed(change, after))
    }

    companion object {
        // los montos que alguien escribe: el modelo no marca qué DECIMAL es dinero (también hay índices, áreas, coordenadas y
        // cantidades), así que la lista vive aquí y AmountFieldsGuardTest exige que cada uno exista, sea DECIMAL y no sea
        // derivado. los derivados (executed_amount, executed_social_amount) los escribe solo la plataforma. los montos de F4 en
        // adelante (receipt.amount, contribution_item.line_amount, program_budget.allocated_amount y executed_amount) los
        // agrega su fase
        val FIELDS: Map<String, Set<String>> =
            mapOf(
                "exploration_campaign" to setOf("social_support_cap"),
                "commitment" to setOf("amount"),
                "delivery" to setOf("delivered_amount"),
                "agreement" to setOf("amount")
            )

        // pura, para las pruebas unitarias: un hallazgo por monto de `objectName` que cambia y no pasa Amounts.parse
        fun findings(
            objectName: String,
            after: Map<String, Any?>,
            changed: Set<String>
        ): List<Finding> =
            FIELDS[objectName]
                .orEmpty()
                .filter { it in changed }
                .sorted()
                .mapNotNull { field -> violation(field, after[field]) }

        private fun violation(
            field: String,
            value: Any?
        ): Finding? =
            try {
                Amounts.parse(value, field)
                null
            } catch (e: RuleException) {
                Finding(e.violations.single().message, e.message ?: "$field no es un monto válido", field, status = HttpStatus.BAD_REQUEST)
            }
    }
}
