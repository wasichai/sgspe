package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// lo que se pregunta una sola vez aunque se escriban o lean muchos registros, en dos alcances:
// - el de un comando (`within` y `remember`): RequestMemoFilter lo abre en cada comando de SGSPE (POST, PUT, PATCH o DELETE
//   bajo /api/sgspe/; plan de la caché del esquema, Tarea 6), y la confirmación de una importación y las guardas que juntan
//   reglas abren el suyo, que tapa al de afuera. ProjectScopeGuard pregunta en cada alta, cambio y vínculo si quien escribe
//   está asignado al proyecto (el alta de una entrega con tres evidencias, unas 7 veces; una importación, cientos), y
//   RecordService llama a las guardas en la corrutina del comando (como la marca SgspeWrite). solo para datos que el comando
//   no cambia: sus asignaciones, los ítems de un aporte, los solicitantes de un compromiso (ningún comando los escribe)
// - el de una petición de /api (`forRequest`, que abre RequestMemoFilter, y `rememberForRequest`): solo lo que ninguna
//   petición cambia, los permisos de quien llama (ProjectReach.readsAll, ConfidentialAttentionScope), que wasichai pide en
//   cada RecordReadScope, una vez por objeto y lectura (plan 4.4a, Tarea 17), y EvidenceFileReadPolicy, una vez por
//   evidencia restringida de una lista (UPS-24).
//   también las definiciones de objeto (ObjectDefinitions.current) y lo que TenantModel responde: ninguna petición de SGSPE
//   cambia el modelo, lo cambian las de /api/metadata de wasichai, que no pasan por SGSPE después.
//   los permisos se cambian por /api/roles, que no
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
