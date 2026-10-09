package sgspe.commitment

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordWrite

// una familia de reglas de CommitmentWriteGuard (workflows.md WF-D y WF-K, domain-model §7.1): las del alta y la edición, las
// de aprobación (D1 a D5 y D8), las de cumplimiento y verificación (D9 a D11) y las de los candidatos. no es una WriteRule:
// la consulta en seco las corre a través de la guarda, una sola vez
interface CommitmentRule {
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
    private val rules: List<CommitmentRule>
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return rules.flatMap { rule ->
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
        }
    }

    companion object {
        val OBJECTS = setOf(Commitments.COMMITMENT, Commitments.DEPENDENCY, Commitments.DELIVERY, Commitments.CANDIDATE)
    }
}
