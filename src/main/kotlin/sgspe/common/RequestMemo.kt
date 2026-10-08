package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// lo que un comando pregunta una sola vez aunque escriba muchos registros: la confirmación de una importación crea cientos
// de componentes, y ProjectScopeGuard preguntaría en cada alta si quien importa está asignado al proyecto (la revisión de la
// Tarea 5). el comando abre el memo con `within` dentro de su transacción, y RecordService llama a las guardas en esa misma
// corrutina (como la marca SgspeWrite), así que la segunda pregunta sale del memo. sin memo en el contexto, cada pregunta
// va a la base, como siempre. solo para datos que el comando no cambia: un memo vive lo que dura el bloque
class RequestMemo private constructor() : AbstractCoroutineContextElement(Key) {
    private val values = ConcurrentHashMap<Any, Any>()

    companion object Key : CoroutineContext.Key<RequestMemo> {
        suspend fun <T> within(block: suspend () -> T): T = withContext(RequestMemo()) { block() }

        // el valor de `key`, calculado una vez dentro de `within`; afuera, calculado cada vez
        @Suppress("UNCHECKED_CAST")
        suspend fun <T : Any> remember(
            key: Any,
            compute: suspend () -> T
        ): T {
            val memo = currentCoroutineContext()[Key] ?: return compute()
            memo.values[key]?.let { return it as T }
            return compute().also { memo.values[key] = it }
        }
    }
}
