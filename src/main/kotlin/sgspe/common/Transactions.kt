package sgspe.common

import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import wasichai.core.platform.ClusterLock

// una transacción de la base alrededor de un comando (architecture.md §6.2): RecordService no abre ninguna pero se une a
// esta (WAS-ADR-0038), así que las escrituras, sus filas de auditoría y los listeners que escriben se confirman o se
// revierten juntos. el usuario que llama sigue en el contexto. `locked` toma además ClusterLock.withXactLock sobre la
// clave (withXactLock abre la transacción, o se une a la de `run`): dos comandos sobre el mismo registro quedan en fila en todas las réplicas
// (WAS-ADR-0039), y el candado se suelta con el commit o el rollback.
// dos límites: un `locked` anidado con claves distintas debe tomarlas siempre en el mismo orden, o dos comandos pueden
// interbloquearse (PostgreSQL corta uno con 40P01, un 500); y quien espera el candado retiene una conexión del pool, así
// que no conviene dejar `locked` envolviendo trabajo lento.
// el orden acordado: varias claves del mismo tipo en una transacción van por id ascendente (el texto del UUID, que es el orden
// de PostgreSQL). hoy pasa con commitment:<id> y, desde F5, social_contribution:<id>: el alta de una evidencia con vínculos a
// entregas de varios padres toma el candado de cada uno al vincular (DELIVERY_FROZEN), y por eso vincula en
// EvidenceRules.lockOrder, por la clave completa del candado. también con social_contribution:<id> cuando un comprobante
// cambia de aporte (ContributionReceiptRule toma los dos) y con social_program:<id> cuando un presupuesto cambia de programa
// (ProgramTotalsListener recalcula los dos)
@Component
class Transactions(
    private val operator: TransactionalOperator,
    private val locks: ClusterLock
) {
    suspend fun <T> run(block: suspend () -> T): T = operator.executeAndAwait { block() }

    suspend fun <T> locked(
        key: String,
        block: suspend () -> T
    ): T = locks.withXactLock("sgspe.$key", block)
}
