package sgspe.investment

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.RequestMemo
import sgspe.common.RuleGuard
import sgspe.common.toCodedException
import wasichai.core.data.RecordWrite

// una familia de reglas de InvestmentWriteGuard (especificación de F5 §3.3 a §3.6): las del registro (aporte, ítems y
// entrega), las de aprobación (P1 a P7 y P9), la de la entrega (P8) y, desde el plan 5.2b, las de programas. como
// CommitmentRule: no es una WriteRule, la consulta en seco las corre a través de la guarda, una sola vez
//
// ORDEN (§3.3, «Orden»): cada regla declara su `order` y la guarda las corre de menor a mayor; la escritura se detiene en el
// primer hallazgo que bloquea, así que una regla posterior no hace sus lecturas ni toma su candado si una anterior ya
// bloqueó, y la consulta en seco los junta todos
interface InvestmentRule {
    val order: Int

    suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding>
}

// las familias y su lugar: registro, aprobación (candado, P7, P9, P4 y P5, P6, P2 y P3, P1), entrega (P8) y programas (5.2b)
object InvestmentRuleOrder {
    const val RECORD = 100
    const val APPROVAL = 200
    const val DELIVERY = 300
    const val PROGRAM = 400
}

// InvestmentWriteGuard (sgspe.investment): las reglas de BC-10 por cualquier puerta (la API genérica, los comandos y la
// consulta en seco). lo que una regla no puede leer es 403 OBJECT_NOT_READABLE, nunca un pase
@Component
@Order(GuardOrder.DOMAIN)
class InvestmentWriteGuard(
    rules: List<InvestmentRule>
) : RuleGuard() {
    private val ordered = rules.sortedBy { it.order }

    // la consulta en seco: todos los hallazgos, en el orden de las reglas
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return RequestMemo.within { ordered.flatMap { run(it, change, dryRun) } }
    }

    // la escritura: se detiene en el primer hallazgo que bloquea
    override suspend fun beforeWrite(change: RecordWrite) {
        if (change.objectName !in OBJECTS) return
        RequestMemo.within {
            for (rule in ordered) {
                run(rule, change, dryRun = false).firstOrNull { it.blocking }?.let { throw it.toCodedException() }
            }
        }
    }

    private suspend fun run(
        rule: InvestmentRule,
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        try {
            rule.findings(change, dryRun)
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }

    companion object {
        // los objetos de aportes; delivery, por sus entregas de aportes (las reglas miran social_contribution). el plan 5.2b
        // suma los de programas
        val OBJECTS: Set<String> = setOf(Contributions.CONTRIBUTION, Contributions.ITEM, Contributions.DELIVERY)
    }
}
