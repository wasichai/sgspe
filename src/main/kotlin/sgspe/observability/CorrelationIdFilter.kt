package sgspe.observability

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context

// antes de la cadena de seguridad de wasichai (WebFilterChainProxy, orden -100), así que toda respuesta lleva el
// identificador, también un 401. viaja en el contexto de Reactor y en la línea de acceso: una por petición, con método,
// ruta sin la cadena de consulta, estado, duración e identificador. nunca el cuerpo ni la consulta, que pueden llevar
// datos personales (security.md §11.6). no depende de ningún paquete de dominio (architecture.md §5.3)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val id = CorrelationId.accept(exchange.request.headers.getFirst(CorrelationId.HEADER))
        val response = exchange.response
        response.headers.set(CorrelationId.HEADER, id)
        // un manejador de errores puede rehacer las cabeceras: se vuelve a poner justo antes de responder
        response.beforeCommit {
            response.headers.set(CorrelationId.HEADER, id)
            Mono.empty()
        }
        val start = System.nanoTime()
        return chain
            .filter(exchange)
            .contextWrite(Context.of(CorrelationId.KEY, id))
            .doFinally { access(exchange, id, start) }
    }

    private fun access(
        exchange: ServerWebExchange,
        id: String,
        start: Long
    ) {
        val millis = (System.nanoTime() - start) / 1_000_000
        MDC.putCloseable(CorrelationId.KEY, id).use {
            ACCESS.info(
                "{} {} {} {}ms correlationId={}",
                exchange.request.method.name(),
                exchange.request.path.value(),
                exchange.response.statusCode?.value(),
                millis,
                id
            )
        }
    }

    companion object {
        private val ACCESS = LoggerFactory.getLogger("sgspe.access")
    }
}
