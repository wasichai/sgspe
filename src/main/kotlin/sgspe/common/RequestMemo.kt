package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// lo que se pregunta una sola vez aunque se escriban o lean muchos registros, en dos alcances:
// - el de un comando (`within` y `remember`): la confirmación de una importación crea cientos de componentes, y
//   ProjectScopeGuard preguntaría en cada alta si quien importa está asignado al proyecto (la revisión de la Tarea 5). el
//   comando abre el memo dentro de su transacción, y RecordService llama a las guardas en esa misma corrutina (como la marca
//   SgspeWrite). solo para datos que el comando no cambia: sus asignaciones, los solicitantes de un compromiso
// - el de una petición de /api (`forRequest`, que abre RequestMemoFilter, y `rememberForRequest`): solo lo que ninguna
//   petición cambia, los permisos de quien llama (ProjectReach.readsAll, ConfidentialAttentionScope), que wasichai pide en
//   cada RecordReadScope, una vez por objeto y lectura (plan 4.4a, Tarea 17). los permisos se cambian por /api/roles, que no
//   lee registros después en la misma petición, y un permiso cambiado vale desde la petición siguiente. lo que una petición
//   sí puede escribir (asignaciones, solicitantes) nunca va al memo de la petición: un comando que escribe varios registros
//   sigue usando el suyo
// sin memo en el contexto, cada pregunta va a la base, como siempre. un memo vive lo que dura su bloque
class RequestMemo private constructor(
    key: CoroutineContext.Key<RequestMemo>
) : AbstractCoroutineContextElement(key) {
    private val values = ConcurrentHashMap<Any, Any>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> get(
        key: Any,
        compute: suspend () -> T
    ): T {
        values[key]?.let { return it as T }
        return compute().also { values[key] = it }
    }

    // el memo de la petición: otra clave de contexto, así que el de un comando abierto dentro no lo tapa
    private object WholeRequest : CoroutineContext.Key<RequestMemo>

    companion object Key : CoroutineContext.Key<RequestMemo> {
        suspend fun <T> within(block: suspend () -> T): T = withContext(RequestMemo(Key)) { block() }

        // el memo de toda una petición de /api (RequestMemoFilter)
        suspend fun <T> forRequest(block: suspend () -> T): T = withContext(RequestMemo(WholeRequest)) { block() }

        // el valor de `key`, calculado una vez dentro de `within`; afuera (aunque haya memo de la petición), calculado cada vez
        suspend fun <T : Any> remember(
            key: Any,
            compute: suspend () -> T
        ): T = currentCoroutineContext()[Key]?.get(key, compute) ?: compute()

        // lo que ninguna petición cambia (los permisos de quien llama): calculado una vez por petición de /api y, fuera de una
        // (un trabajo, una prueba), una vez dentro de `within`; si no, cada vez
        suspend fun <T : Any> rememberForRequest(
            key: Any,
            compute: suspend () -> T
        ): T {
            val context = currentCoroutineContext()
            val memo = context[WholeRequest] ?: context[Key] ?: return compute()
            return memo.get(key, compute)
        }
    }
}
