package sgspe.observability

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import reactor.util.context.Context
import wasichai.core.platform.CorrelationIdWebFilter

// antes de la cadena de seguridad de wasichai (WebFilterChainProxy, orden -100), así que toda respuesta lleva el
// identificador, también un 401. viaja en el contexto de Reactor y en la línea de acceso: una por petición, con método,
// ruta sin la cadena de consulta, estado, duración e identificador. nunca el cuerpo ni la consulta, que pueden llevar
// datos personales (security.md §11.6). no depende de ningún paquete de dominio (architecture.md §5.3)
//
// desde wasichai 0.5.0 el CorrelationIdWebFilter de la plataforma acepta o genera el identificador con el mismo formato y
// lo deja en el contexto de Reactor con la misma clave, de donde lo toman sus filas de auditoría [WAS-ADR-0050]. este
// filtro corre justo después y usa ese, así la respuesta, la línea de acceso y la auditoría dicen lo mismo aunque el
// cliente no mande ninguno; sin el de la plataforma (la cadena pelada de las pruebas unitarias) lo resuelve él
@Component
@Order(CorrelationIdWebFilter.ORDER + 1)
class CorrelationIdFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> =
        Mono.deferContextual { context ->
            val platform = (context.getOrEmpty<Any>(CorrelationId.KEY).orElse(null) as? String)?.takeIf(CorrelationId::valid)
            serve(exchange, chain, platform ?: CorrelationId.accept(exchange.request.headers.getFirst(CorrelationId.HEADER)))
        }

    private fun serve(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
        id: String
    ): Mono<Void> {
        val response = exchange.response
        response.headers.set(CorrelationId.HEADER, id)
        // un manejador de errores puede rehacer las cabeceras: se vuelve a poner justo antes de responder
        response.beforeCommit {
            response.headers.set(CorrelationId.HEADER, id)
            Mono.empty()
        }
        val start = System.nanoTime()
        // el estado de la línea de acceso según cómo terminó la cadena. en el servidor real doFinally corre después de que
        // ExceptionHandlingWebHandler (fuera de los filtros) puso el estado del error, y en Netty getStatusCode() nunca es
        // null: el estado sale de la respuesta. el respaldo de ResponseStatusException y 500 solo cuenta con una cadena
        // pelada, sin ese manejador (las pruebas unitarias)
        var failure: Throwable? = null
        return chain
            .filter(exchange)
            .doOnError { failure = it }
            .contextWrite(Context.of(CorrelationId.KEY, id))
            .doFinally { signal -> access(exchange, id, start, statusOf(exchange, signal, failure)) }
    }

    // ON_COMPLETE: el de la respuesta. ON_ERROR: el de la respuesta si ya lo tiene, el del ResponseStatusException si es
    // uno, y si no 500. CANCEL: 499, el «client closed request» de nginx, un marcador y no un estado que sgspe devuelva
    private fun statusOf(
        exchange: ServerWebExchange,
        signal: SignalType,
        failure: Throwable?
    ): Int =
        when (signal) {
            SignalType.CANCEL -> CLIENT_CLOSED
            SignalType.ON_ERROR ->
                exchange.response.statusCode?.value()
                    ?: (failure as? ResponseStatusException)?.statusCode?.value()
                    ?: HttpStatus.INTERNAL_SERVER_ERROR.value()
            else -> exchange.response.statusCode?.value() ?: HttpStatus.OK.value()
        }

    private fun access(
        exchange: ServerWebExchange,
        id: String,
        start: Long,
        status: Int
    ) {
        val millis = (System.nanoTime() - start) / 1_000_000
        MDC.putCloseable(CorrelationId.KEY, id).use {
            ACCESS.info(
                "{} {} {} {}ms correlationId={}",
                exchange.request.method.name(),
                exchange.request.path.value(),
                status,
                millis,
                id
            )
        }
    }

    companion object {
        private val ACCESS = LoggerFactory.getLogger("sgspe.access")
        private const val CLIENT_CLOSED = 499
    }
}
