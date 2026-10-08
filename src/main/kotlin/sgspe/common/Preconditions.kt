package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context
import wasichai.core.audit.AuditQueryService
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

// la precondición sobre updatedAt (api.md §3.6, ADR-0025, RNF-INT-003, ASM-035): el portal manda en If-Match el
// updatedAt que leyó, entre comillas como una etiqueta de entidad. se comparan instantes, no textos, así un formato
// distinto del mismo instante no falla. `*` no compara nada (RFC 9110)
object IfMatch {
    const val HEADER = "If-Match"

    // null: sin cabecera o `*`. un valor que no es un instante entre comillas es un 400 sobre If-Match
    fun parse(raw: String?): Instant? {
        val value = raw?.trim()?.ifEmpty { null } ?: return null
        if (value == "*") return null
        val unquoted = value.removePrefix("W/").removeSurrounding("\"")
        return try {
            Instant.parse(unquoted)
        } catch (_: DateTimeParseException) {
            throw ValidationException("If-Match inválido", HEADER, "envíe el updatedAt que leyó, entre comillas")
        }
    }

    // 412 si el registro cambió después de que el cliente lo leyera
    fun check(
        expected: Instant?,
        current: Instant?,
        what: String
    ) {
        if (expected == null) return
        if (current == null || current != expected) {
            throw PreconditionFailedException("$what cambió después de que lo leyeras: vuelve a cargarlo", current?.toString())
        }
    }

    // un comando con la marca IM de api.md §4: sin If-Match, 428
    fun required(raw: String?): Instant = parse(raw) ?: throw PreconditionRequiredException("Este comando exige If-Match con el updatedAt que leíste")

    // si después de la versión que leyó el cliente solo hubo escrituras de derivados o vínculos. la versión es la que
    // produjo el updatedAt: todas las entradas con el instante de la primera en o después de `expected` (una transacción
    // puede dejar varias con el mismo now(), y el orden entre ellas no importa). si ninguna cae dentro de VERSION_WINDOW
    // (un If-Match que no es la versión de ninguna escritura, como uno anterior al alta) no pasa. de las posteriores, un
    // vínculo (rel:…) no cambia la versión y cuenta como inofensivo; un derivado también. una entrada sin ningún campo
    // visible no se tolera: puede ser un cambio que el diff de atributos no ve (una geometría) y, aunque sea un campo
    // que quien llama no puede leer, el PUT de wasichai lo conserva, así que el 412 solo cuesta volver a cargar. así un
    // listener que actualiza un derivado (los conteos de asistentes, el área) no invalida el If-Match de quien sigue
    // trabajando
    fun onlyDerivedSince(
        expected: Instant,
        writes: List<LaterWrite>,
        derived: Set<String>
    ): Boolean {
        val after = writes.filter { !it.occurredAt.isBefore(expected) }
        val versionAt = after.minOfOrNull { it.occurredAt } ?: return false
        if (versionAt.isAfter(expected.plus(VERSION_WINDOW))) return false
        return after.filter { it.occurredAt != versionAt }.all { write ->
            write.fields.isNotEmpty() && write.fields.all { it.startsWith("rel:") || it in derived }
        }
    }

    // la mayor distancia entre el updated_at de un registro y el occurred_at de su entrada de audit_log, escritos en una
    // misma transacción
    private val VERSION_WINDOW: Duration = Duration.ofSeconds(2)
}

// una escritura del historial de un registro (audit_log), con los campos que cambió tal como los ve quien llama
data class LaterWrite(
    val occurredAt: Instant,
    val fields: Set<String>
)

// la comparación de If-Match con el registro guardado: igual, pasa; distinto, pasa solo si lo que cambió desde la versión
// leída son derivados (IfMatch.onlyDerivedSince, leído del historial con los permisos de quien llama); si no, 412. la usan
// IfMatchGuard y los comandos con IM
@Component
class Versions(
    private val audit: AuditQueryService,
    private val catalog: ModelCatalog
) {
    suspend fun check(
        objectName: String,
        recordId: UUID,
        expected: Instant?,
        current: Instant?,
        what: String
    ) {
        if (expected == null || current == expected) return
        if (current != null && onlyDerived(objectName, recordId, expected)) return
        IfMatch.check(expected, current, what)
    }

    private suspend fun onlyDerived(
        objectName: String,
        recordId: UUID,
        expected: Instant
    ): Boolean {
        val writes =
            audit.history(objectName, recordId, HISTORY).mapNotNull { entry ->
                entry.occurredAt?.let { LaterWrite(it, entry.changes.map { change -> change.field }.toSet()) }
            }
        return IfMatch.onlyDerivedSince(expected, writes, catalog.derivedFields(objectName))
    }

    companion object {
        // el tope que acepta AuditQueryService.history de wasichai 0.3.2 (coerceIn(1, 500)). una asamblea grande deja
        // decenas de escrituras de conteos y vínculos tras el alta; con más de 500 posteriores, la entrada de la versión
        // leída queda fuera de la ventana y el resultado es un 412 (falla cerrado; api.md §3.6)
        private const val HISTORY = 500
    }
}

// lo que pide el If-Match de una petición a la API genérica: el registro de la ruta y el valor tal como llegó. lo lee la
// guarda, dentro del controlador, para que un valor inválido sea un 400 con Problem Details y no un error del filtro
data class IfMatchExpectation(
    val objectName: String,
    val recordId: UUID,
    val raw: String
)

// pone la expectativa en el contexto de Reactor, como el filtro de correlación pone su identificador: la guarda corre en
// la corrutina de la petición, que conserva ese contexto (WAS-ADR-0040). solo las escrituras de la API genérica sobre un
// registro (PUT y DELETE de /api/objects/{objeto}/records/{id}, sus transiciones y sus vínculos); los comandos propios
// comparan dentro de su transacción y con su candado
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class IfMatchFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val request = exchange.request
        val raw = request.headers.getFirst(IfMatch.HEADER) ?: return chain.filter(exchange)
        if (request.method == HttpMethod.GET) return chain.filter(exchange)
        val match = RECORD_PATH.find(request.path.value()) ?: return chain.filter(exchange)
        val recordId = runCatching { UUID.fromString(match.groupValues[2]) }.getOrNull() ?: return chain.filter(exchange)
        val expectation = IfMatchExpectation(match.groupValues[1], recordId, raw)
        return chain.filter(exchange).contextWrite(Context.of(KEY, expectation))
    }

    companion object {
        const val KEY = "sgspe.ifMatch"
        private val RECORD_PATH = Regex("^/api/objects/([a-z][a-z0-9_]*)/records/([0-9a-fA-F-]{36})(/.*)?$")
    }
}

// compara la expectativa con el updatedAt guardado antes de cambiar, transicionar, vincular o borrar el registro de la
// ruta. por la API genérica no hay transacción: queda una ventana mínima entre comparar y escribir hasta UPS-04
@Component
@Order(GuardOrder.IF_MATCH)
class IfMatchGuard(
    private val records: Records,
    private val versions: Versions
) : RecordWriteGuard {
    override suspend fun beforeWrite(change: RecordWrite) {
        // las escrituras del propio sgspe dentro de la petición (un listener que actualiza un derivado del mismo registro)
        // no son la escritura que el cliente condicionó
        val recordId = change.recordId
        if (change.kind == RecordChangeKind.CREATED || recordId == null || WriteMark.present()) return
        val expectation =
            currentCoroutineContext()[ReactorContext]?.context?.getOrDefault<IfMatchExpectation>(IfMatchFilter.KEY, null) ?: return
        if (expectation.objectName != change.objectName || expectation.recordId != recordId) return
        val expected = IfMatch.parse(expectation.raw) ?: return
        val current = records.find(change.objectName, recordId)?.updatedAt
        versions.check(change.objectName, recordId, expected, current, "El registro")
    }
}
