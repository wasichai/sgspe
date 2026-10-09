package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.mono
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
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
}

// la comparación de If-Match con el registro guardado: igual, pasa; distinto, pasa solo si lo vigente es una de las versiones
// a las que se llega desde la leída por saltos de derivados (ADR-0027, VersionChains); si no, 412 con el valor actual. un
// vínculo no mueve updated_at [WAS-ADR-0051], así que no hace falta tolerarlo. la usan IfMatchGuard, con las versiones que
// ya pidió IfMatchFilter, y los comandos con IM, dentro de su transacción y con su candado
@Component
class Versions(
    private val chains: VersionChains
) {
    suspend fun check(
        objectName: String,
        recordId: UUID,
        expected: Instant?,
        current: Instant?,
        what: String,
        accepted: List<Instant>? = null
    ) {
        if (expected == null || current == expected) return
        if (current != null && current in (accepted ?: chains.accepted(objectName, recordId, expected))) return
        IfMatch.check(expected, current, what)
    }
}

// lo que pide el If-Match de una petición a la API genérica: el registro de la ruta, el valor tal como llegó y, si es un
// instante, las versiones que acepta (la leída y las que la siguieron por derivados). lo lee la guarda, dentro del
// controlador, para que un valor inválido sea un 400 con Problem Details y no un error del filtro
data class IfMatchExpectation(
    val objectName: String,
    val recordId: UUID,
    val raw: String,
    val accepted: List<Instant>? = null
)

// pone la expectativa en el contexto de Reactor, como el filtro de correlación pone su identificador: la guarda corre en
// la corrutina de la petición, que conserva ese contexto (WAS-ADR-0040). solo las escrituras de la API genérica sobre un
// registro (PUT, PATCH y DELETE de /api/objects/{objeto}/records/{id}, sus transiciones, sus vínculos y sus archivos); los
// comandos propios comparan dentro de su transacción y con su candado
//
// y cambia If-Match por la lista de versiones aceptadas, como etiquetas fuertes (ADR-0027): wasichai compara en el propio
// UPDATE, DELETE o transición que updated_at sea una de ellas [WAS-ADR-0051], así una escritura de otro que cae entre la
// guarda y la escritura es un 412 de la plataforma y no una actualización perdida. un valor que no es un instante, o `*`,
// sale de la petición: la guarda responde el 400 con Problem Details, o no compara. corre después de la cadena de
// seguridad, que deja a quien llama en el contexto: los saltos se leen con su organización
@Component
@Order(IfMatchFilter.ORDER)
class IfMatchFilter(
    private val chains: VersionChains
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val request = exchange.request
        val raw = request.headers.getFirst(IfMatch.HEADER) ?: return chain.filter(exchange)
        if (request.method == HttpMethod.GET) return chain.filter(exchange)
        val match = RECORD_PATH.find(request.path.value()) ?: return chain.filter(exchange)
        val recordId = runCatching { UUID.fromString(match.groupValues[2]) }.getOrNull() ?: return chain.filter(exchange)
        val objectName = match.groupValues[1]
        return mono {
            // un valor inválido no se compara aquí: la guarda responde su 400
            val expected =
                try {
                    IfMatch.parse(raw)
                } catch (_: ValidationException) {
                    null
                }
            IfMatchExpectation(objectName, recordId, raw, expected?.let { chains.accepted(objectName, recordId, it) })
        }.flatMap { expectation ->
            val accepted = expectation.accepted?.joinToString(", ") { "\"$it\"" }
            val rewritten =
                exchange
                    .mutate()
                    .request {
                        it.headers { headers ->
                            headers.remove(IfMatch.HEADER)
                            if (accepted != null) headers.set(IfMatch.HEADER, accepted)
                        }
                    }.build()
            chain.filter(rewritten).contextWrite(Context.of(KEY, expectation))
        }
    }

    companion object {
        const val KEY = "sgspe.ifMatch"

        // después de la cadena de Spring Security, que es WebFluxSecurityConfiguration.WEB_FILTER_CHAIN_FILTER_ORDER (-100;
        // la clase no es pública)
        const val ORDER = -90
        private val RECORD_PATH = Regex("^/api/objects/([a-z][a-z0-9_]*)/records/([0-9a-fA-F-]{36})(/.*)?$")
    }
}

// compara la expectativa con el updatedAt guardado antes de cambiar, transicionar, vincular o borrar el registro de la
// ruta, para responder el 412 de sgspe (con el valor actual) antes que las demás guardas. por la API genérica no hay
// transacción: entre esta comparación y la escritura, la que cierra la ventana es la de wasichai en el propio UPDATE, con
// las mismas versiones (IfMatchFilter); un vínculo no la tiene, pero tampoco mueve updated_at ni reemplaza nada
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
        versions.check(change.objectName, recordId, expected, current, "El registro", expectation.accepted)
    }
}
