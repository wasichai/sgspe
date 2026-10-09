package sgspe.commitment

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordWrite

// una familia de reglas de CommitmentWriteGuard (workflows.md WF-D y WF-K, domain-model §7.1): las del alta y la edición, las
// de aprobación (D1 a D5 y D8), las de cumplimiento y verificación (D9 a D11) y las de los candidatos. no es una WriteRule:
// la consulta en seco las corre a través de la guarda, una sola vez
//
// ORDEN (P49): cada regla declara su `order` y la guarda las corre de menor a mayor; en beforeWrite se detiene en el primer
// hallazgo que bloquea, así que dos violaciones simultáneas dan siempre el mismo código y una regla posterior no hace sus
// lecturas si una anterior ya bloqueó. la consulta en seco (transition-checks) sí junta todos los hallazgos. una regla nueva
// elige su lugar con las constantes de CommitmentRuleOrder (la familia) y, dentro de la familia, un desplazamiento: lo más
// barato y lo que no depende de otros registros va primero
interface CommitmentRule {
    val order: Int

    suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding>
}

// lo que una regla no puede leer como quien escribe (P12): un objeto sin READ es un bloqueo OBJECT_NOT_READABLE, nunca un
// pase en silencio
class NotReadable(
    val objectName: String
) : RuntimeException("sin lectura de $objectName")

// las familias de reglas y su lugar (CommitmentRule.order): alta y edición, aprobación (D1 a D5 y D8), cumplimiento y
// verificación (D9 a D11) y candidatos. dentro de una familia, se suma un desplazamiento (1, 2, …)
object CommitmentRuleOrder {
    const val RECORD = 100
    const val APPROVAL = 200
    const val FULFILLMENT = 300
    const val CANDIDATE = 400
}

// lee como quien escribe; sin READ sobre el objeto, NotReadable
suspend fun <T> readAs(
    objectName: String,
    block: suspend () -> T
): T =
    try {
        block()
    } catch (_: ForbiddenException) {
        throw NotReadable(objectName)
    }

// CommitmentWriteGuard (sgspe.commitment): junta las reglas de compromisos, entregas, dependencias y candidatos. cada regla
// mira solo sus objetos y sus transiciones
@Component
@Order(GuardOrder.DOMAIN)
class CommitmentWriteGuard(
    rules: List<CommitmentRule>
) : RuleGuard() {
    private val ordered = rules.sortedBy { it.order }

    // la consulta en seco: todos los hallazgos, en el orden de las reglas
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return ordered.flatMap { run(it, change, dryRun) }
    }

    // la escritura: se detiene en el primer hallazgo que bloquea
    override suspend fun beforeWrite(change: RecordWrite) {
        if (change.objectName !in OBJECTS) return
        for (rule in ordered) {
            run(rule, change, dryRun = false).firstOrNull { it.blocking }?.let { throw it.toCodedException() }
        }
    }

    private suspend fun run(
        rule: CommitmentRule,
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        try {
            rule.findings(change, dryRun)
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName} y quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }

    companion object {
        val OBJECTS = setOf(Commitments.COMMITMENT, Commitments.DEPENDENCY, Commitments.DELIVERY, Commitments.CANDIDATE)
    }
}
