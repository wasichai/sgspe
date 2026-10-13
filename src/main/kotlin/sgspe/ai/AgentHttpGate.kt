package sgspe.ai

import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.result.view.ViewResolver
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono
import wasichai.core.common.ForbiddenException
import wasichai.core.common.GlobalExceptionHandler
import java.net.URI

// POST /api/agent/ask por HTTP responde 403 (especificación de F7 §3.3, ai-architecture.md §8): toda pregunta pasa por
// POST /api/sgspe/ai/assistant/questions, que comprueba la activación, seudonimiza y registra. se cierra todo método sobre esa
// ruta, también con parámetros de ruta; GET /api/agent/status queda abierto. corre después de la cadena de seguridad (sin token
// sigue siendo 401) y escribe el problem+json del manejador de wasichai, como IssueDocumentFilter: un error lanzado en un filtro
// no llega al manejador de los controladores
@Component
@Order(AgentHttpGate.ORDER)
class AgentHttpGate(
    private val handler: GlobalExceptionHandler,
    private val codecs: ServerCodecConfigurer
) : WebFilter {
    private val context =
        object : ServerResponse.Context {
            override fun messageWriters(): List<HttpMessageWriter<*>> = codecs.writers

            override fun viewResolvers(): List<ViewResolver> = emptyList()
        }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        if (!closed(exchange.request)) return chain.filter(exchange)
        val problem = handler.handleWasichai(ForbiddenException(MESSAGE))
        if (problem.instance == null) problem.instance = URI.create(exchange.request.path.value())
        return ServerResponse
            .status(problem.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyValue(problem)
            .flatMap { it.writeTo(exchange, context) }
    }

    companion object {
        // después de la cadena de seguridad (-100), de IssueDocumentFilter (-99) y de RequestMemoFilter (-98)
        const val ORDER = -97
        const val MESSAGE = "El asistente se usa por POST /api/sgspe/ai/assistant/questions, que activa, seudonimiza y registra cada pregunta"

        // la ruta de AgentController.ask, con el analizador del mapeo de WebFlux: sin parámetros de ruta, decodificada
        private val ASK = PathPatternParser.defaultInstance.parse("/api/agent/ask")

        fun closed(request: ServerHttpRequest): Boolean = ASK.matches(request.path.pathWithinApplication())
    }
}
