package sgspe.common

import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import wasichai.core.platform.ClusterLock

// una transacción de la base alrededor de un comando (architecture.md §6.2): RecordService no abre ninguna pero se une a
// esta (WAS-ADR-0038), así que las escrituras, sus filas de auditoría y los listeners que escriben se confirman o se
// revierten juntos. el usuario que llama sigue en el contexto. `locked` toma además ClusterLock.withXactLock sobre la
// clave dentro de la misma transacción: dos comandos sobre el mismo registro quedan en fila en todas las réplicas
// (WAS-ADR-0039), y el candado se suelta con el commit o el rollback
@Component
class Transactions(
    private val operator: TransactionalOperator,
    private val locks: ClusterLock
) {
    suspend fun <T> run(block: suspend () -> T): T = operator.executeAndAwait { block() }

    suspend fun <T> locked(
        key: String,
        block: suspend () -> T
    ): T = operator.executeAndAwait { locks.withXactLock("sgspe.$key") { block() } }
}
