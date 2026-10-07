package sgspe.common

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// la marca de una escritura que hace el código de sgspe (Records, y en F2 los listeners y los trabajos): un elemento del
// contexto de la corrutina, como EscrituraDeCaja de caja-backend@dc9e391 (src/main/kotlin/caja/comun/EscrituraDeCaja.kt).
// RecordService llama a las guardas en esa misma corrutina, así que una guarda la ve antes de escribir (WAS-ADR-0040).
// una escritura sin la marca entró por otra puerta: la API genérica de wasichai, /admin o un módulo. un cliente http no
// puede ponerla: es un objeto del proceso, no un dato de la petición
object SgspeWrite : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<SgspeWrite>

    override fun toString() = "SgspeWrite"
}
