package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard

// lo que una regla de sgspe encuentra en una escritura: un bloqueo (la escritura no ocurre) o una advertencia (pide
// justificación pero no bloquea). code es una constante en inglés UPPER_SNAKE (api.md §3.2); message va en español y sin
// datos personales (RQ-SEC-010). status: 400 si la regla rechaza los datos enviados; 409 si una transición choca con lo
// guardado o con otros registros; 403 si falta un permiso, una asignación o la marca de un comando (api.md §3.3)
data class Finding(
    val code: String,
    val message: String,
    val field: String? = null,
    val blocking: Boolean = true,
    val status: HttpStatus = HttpStatus.CONFLICT
)

// la excepción de la regla, con el código en errors[0].message: el 403 de wasichai no lleva campos, y un 409 o un 403 de
// regla sin código no se distingue de otro por permiso o por estado. detail lleva el texto; errors[0], el campo y el código
fun Finding.toCodedException(): WasichaiException = RuleException(status, message, listOf(FieldViolation(field ?: "record", code)))

// una regla que POST /api/sgspe/transition-checks puede correr en seco. dryRun: la escritura no va a ocurrir, así que una
// regla que pide algo que la petición traería (el motivo) lo informa como advertencia
interface WriteRule {
    suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean = false
    ): List<Finding>
}

// una guarda de wasichai (WAS-ADR-0040) hecha de reglas: la primera que bloquea lanza su excepción, antes de guardar. la
// lanza con su código en errors[0].message y el campo en errors[0].field (P15): así el 400, el 403 y el 409 de la API
// genérica se distinguen por código y no solo por estado
abstract class RuleGuard :
    RecordWriteGuard,
    WriteRule {
    override suspend fun beforeWrite(change: RecordWrite) {
        findings(change, dryRun = false).firstOrNull { it.blocking }?.let { throw it.toCodedException() }
    }
}

// el orden en que wasichai llama a las guardas de sgspe (@Order): primero lo que nunca puede pasar por esa puerta (la
// marca de un comando, los derivados, el motivo), después quién escribe (alcance, edición de lo propio, precondición) y
// al final las reglas de datos y las de cada paquete
object GuardOrder {
    const val COMMAND = 100
    const val DERIVED = 110
    const val REASON = 120
    const val FIELD_REASON = 130
    const val SCOPE = 200
    const val EDIT_ANY = 210
    const val IF_MATCH = 220
    const val EXACTLY_ONE = 300
    const val AMOUNTS = 305
    const val USER_REFERENCE = 310
    const val ASSIGNMENT_ROLE = 320
    const val DEPENDENTS = 330
    const val DOMAIN = 400
}

// una escritura que vincula o desvincula dos registros: wasichai la manda como UPDATE con `rel:<relación>`
fun RecordWrite.isLink(): Boolean = kind == RecordChangeKind.UPDATED && attributes.orEmpty().keys.any { it.startsWith("rel:") }
