package sgspe.privacy

import kotlinx.coroutines.reactor.mono
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
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
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.NotFoundException
import wasichai.core.common.UnauthorizedException
import wasichai.core.identity.CurrentUser

// quién puede emitir a mano un documento de un objeto (lo decide IssueAccessByUpdate; las pruebas del filtro dan la suya)
interface IssueAccess {
    suspend fun canIssue(objectName: String): Boolean
}

// emitir un documento a mano exige UPDATE sobre el objeto (security.md §11.4, hallazgo I1 de la revisión final del plan 3.1):
// emitir archiva el documento vigente del mismo tipo, así que es una escritura. sin usuario o con un objeto que no existe no
// decide: deja que wasichai conteste su 401 o su 404. nunca compara nombres de rol (RQ-SEC-005)
@Component
class IssueAccessByUpdate(
    private val currentUser: CurrentUser,
    private val permissions: Permissions
) : IssueAccess {
    override suspend fun canIssue(objectName: String): Boolean {
        val user =
            try {
                currentUser.require()
            } catch (_: UnauthorizedException) {
                return true
            }
        return try {
            permissions.has(user, Actions.UPDATE, objectName)
        } catch (_: NotFoundException) {
            true
        }
    }
}

// wasichai-documents 0.6.0 emite con POST /api/objects/{objeto}/records/{id}/documents/{tipo} y solo pide READ sobre el objeto
// (DocumentService.issueAsUser), sin un SPI ni una guarda que sgspe pueda enganchar: un ANALYST o un READ_ONLY reemplazaría
// la minuta que AUT-05 congeló al aprobarse el acta. este filtro exige UPDATE en esa ruta y solo en ella; sin el permiso
// responde 403 ACTION_REQUIRED sobre `record`, el problem+json del manejador de wasichai (un error lanzado en un filtro no
// llega al @RestControllerAdvice, por eso lo escribe él con los codificadores de WebFlux). las automatizaciones (AUT-05)
// emiten como la plataforma por el DocumentIssuer de wasichai-automation, sin pasar por HTTP, así que no las toca. corre
// después de la cadena de seguridad de wasichai, que deja al usuario en el contexto de Reactor
@Component
@Order(IssueDocumentFilter.ORDER)
class IssueDocumentFilter(
    private val access: IssueAccess,
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
        val objectName = objectOf(exchange.request) ?: return chain.filter(exchange)
        return mono { access.canIssue(objectName) }.flatMap { allowed ->
            if (allowed) chain.filter(exchange) else refuse(exchange, objectName)
        }
    }

    private fun refuse(
        exchange: ServerWebExchange,
        objectName: String
    ): Mono<Void> {
        val problem =
            handler.handleWasichai(
                Finding(
                    "ACTION_REQUIRED",
                    "Emitir un documento de $objectName exige permiso de edición sobre $objectName",
                    "record",
                    status = HttpStatus.FORBIDDEN
                ).toCodedException()
            )
        return ServerResponse
            .status(problem.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyValue(problem)
            .flatMap { it.writeTo(exchange, context) }
    }

    companion object {
        // justo después de WebFilterChainProxy, la cadena de seguridad: Spring Security la registra con el orden -100
        // (WebFluxSecurityConfiguration.WEB_FILTER_CHAIN_FILTER_ORDER, que no es pública)
        private const val SECURITY_CHAIN = -100
        const val ORDER = SECURITY_CHAIN + 1

        // la ruta de DocumentController.issue, con el mismo analizador que el mapeo de WebFlux: las variables llegan
        // decodificadas y sin parámetros de ruta, como las ve el controlador
        private val ROUTE = PathPatternParser.defaultInstance.parse("/api/objects/{object}/records/{id}/documents/{type}")

        // el objeto de una emisión manual, como lo lee wasichai (trim y minúsculas), o null si la petición no es una
        fun objectOf(request: ServerHttpRequest): String? {
            if (request.method != HttpMethod.POST) return null
            val match = ROUTE.matchAndExtract(request.path.pathWithinApplication()) ?: return null
            return match.uriVariables["object"]?.trim()?.lowercase()
        }
    }
}
