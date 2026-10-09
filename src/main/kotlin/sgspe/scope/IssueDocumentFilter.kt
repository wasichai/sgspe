package sgspe.scope

import kotlinx.coroutines.reactor.mono
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
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
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
import java.net.URI
import java.util.Optional
import java.util.UUID

// el registro de una emisión manual: el objeto como lo lee wasichai (trim y minúsculas) y el id de la ruta
data class IssueTarget(
    val objectName: String,
    val recordId: UUID
)

// por qué quien llama no puede emitir a mano un documento del registro, o null si puede (lo decide IssueAccessByScope; las
// pruebas del filtro dan la suya)
interface IssueAccess {
    suspend fun refusal(target: IssueTarget): Finding?
}

// emitir un documento a mano archiva el documento vigente del mismo tipo, así que es editar ese registro y pide lo mismo que
// cambiarlo (security.md §11.4, hallazgo I1 de la revisión final del plan 3.1, rulings P60 y P62), en el orden de las guardas:
// UPDATE sobre el objeto; como en ProjectScopeGuard, una asignación vigente al proyecto del registro, resuelto con
// ProjectResolver sobre el registro guardado leído como quien llama (ADMIN y los objetos sin alcance por proyecto no la
// necesitan); y, como en EditAnyGuard, la regla de OwnRecords: sin EDIT_ANY, solo en un registro propio. un registro que quien
// llama no puede leer (o que no existe) falla cerrado con OBJECT_NOT_READABLE, y uno sin proyecto resoluble con NOT_ASSIGNED,
// como en la guarda. sin usuario o con un objeto que no existe no decide: wasichai contesta su 401 o su 404. nunca compara
// nombres de rol (RQ-SEC-005)
@Component
class IssueAccessByScope(
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val permissions: Permissions,
    private val metadata: MetadataService,
    private val records: Records,
    private val resolver: ProjectResolver,
    private val assignments: Assignments
) : IssueAccess {
    override suspend fun refusal(target: IssueTarget): Finding? {
        val user = callers.userOrNull() ?: return null
        val objectId =
            try {
                metadata.loadDefinition(user.organizationId, target.objectName).obj.id
            } catch (_: NotFoundException) {
                return null
            }
        val name = target.objectName
        return decide(
            name,
            canUpdate = permissions.has(user, Actions.UPDATE, name),
            exempt = user.isAdmin || name in ProjectScope.EXEMPT || !ProjectScope.covers(name),
            scope = {
                stored(target)?.let { attributes ->
                    // la emisión como una actualización que no cambia nada: el alcance es el del registro guardado
                    val write =
                        RecordWrite(
                            organizationId = user.organizationId,
                            userId = user.userId,
                            objectId = objectId,
                            objectName = name,
                            recordId = target.recordId,
                            kind = RecordChangeKind.UPDATED,
                            before = attributes,
                            attributes = attributes
                        )
                    resolver.resolve(write, attributes)
                }
            },
            assigned = { project -> assignments.isAssigned(user.userId, project) },
            ownRecord = {
                OwnRecords.refusal(
                    name,
                    declaresEditAny = name in catalog.objectsDeclaring(OwnRecords.EDIT_ANY),
                    user = user,
                    holdsEditAny = { caller -> permissions.has(caller, OwnRecords.EDIT_ANY, name) },
                    createdIt = { caller -> OwnRecords.createdBy(records, name, target.recordId, caller.userId) }
                )
            }
        )
    }

    // el registro como lo lee quien llama, o null si no lo puede leer o no existe
    private suspend fun stored(target: IssueTarget): Map<String, Any?>? =
        try {
            records.find(target.objectName, target.recordId)?.attributes
        } catch (_: ForbiddenException) {
            null
        }

    companion object {
        // pura, para las pruebas unitarias: la primera negativa, o null. `scope` lee el registro (null si no se puede) y solo
        // se llama con UPDATE y sin exención; `assigned` dice si quien llama tiene una asignación vigente al proyecto;
        // `ownRecord` es la negativa de OwnRecords, la última, también para quien está exento del alcance
        suspend fun decide(
            objectName: String,
            canUpdate: Boolean,
            exempt: Boolean,
            scope: suspend () -> Resolution?,
            assigned: suspend (UUID) -> Boolean,
            ownRecord: suspend () -> Finding?
        ): Finding? {
            if (!canUpdate) {
                return Finding(
                    "ACTION_REQUIRED",
                    "Emitir un documento de $objectName exige permiso de edición sobre $objectName",
                    "record",
                    status = HttpStatus.FORBIDDEN
                )
            }
            if (exempt) return ownRecord()
            val resolution =
                scope() ?: return Finding(
                    "OBJECT_NOT_READABLE",
                    "No puede leer este registro de $objectName, así que no puede emitir sus documentos",
                    "record",
                    status = HttpStatus.FORBIDDEN
                )
            if (resolution.unresolved) {
                return notAssigned(
                    resolution.field,
                    "No se pudo determinar el proyecto de este registro (${resolution.field}): solo el administrador emite sus documentos"
                )
            }
            val outside = resolution.projects.firstOrNull { !assigned(it) } ?: return ownRecord()
            return notAssigned(resolution.field, "No tiene una asignación vigente al proyecto $outside: solo su equipo emite sus documentos")
        }

        // field: el campo de la relación de alcance, como en ProjectScopeGuard
        private fun notAssigned(
            field: String,
            message: String
        ) = Finding("NOT_ASSIGNED", message, field, status = HttpStatus.FORBIDDEN)
    }
}

// wasichai-documents 0.6.0 emite con POST /api/objects/{objeto}/records/{id}/documents/{tipo} y solo pide READ sobre el objeto
// (DocumentService.issueAsUser), sin un SPI ni una guarda que sgspe pueda enganchar: un ANALYST o un READ_ONLY reemplazaría
// la minuta que AUT-05 congeló al aprobarse el acta, y quien edita interacciones lo haría en un proyecto que no es el suyo.
// este filtro pregunta a IssueAccess en esa ruta y solo en ella; una negativa es un 403 con su código y su campo, el
// problem+json del manejador de wasichai (un error lanzado en un filtro no llega al @RestControllerAdvice, por eso lo escribe
// él con los codificadores de WebFlux). las automatizaciones (AUT-05) emiten como la plataforma por el DocumentIssuer de
// wasichai-automation, sin pasar por HTTP, así que no las toca. corre después de la cadena de seguridad de wasichai, que deja
// al usuario en el contexto de Reactor
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
        val target = targetOf(exchange.request) ?: return chain.filter(exchange)
        // Optional: una corrutina que devuelve null completaría vacía y la petición no seguiría ni se respondería. un error al
        // decidir (la base, por ejemplo) no es un pase: sale como el problem+json del manejador de wasichai, no como el 500 de
        // Boot. solo la decisión se envuelve; lo que falle después en la cadena lo maneja el controlador
        val decision =
            mono { Optional.ofNullable(access.refusal(target)?.let { handler.handleWasichai(it.toCodedException()) }) }
                .onErrorResume(Exception::class.java) { e -> Mono.just(Optional.of(problemOf(e))) }
        return decision.flatMap { problem ->
            if (problem.isPresent) write(exchange, problem.get()) else chain.filter(exchange)
        }
    }

    private fun problemOf(e: Exception): ProblemDetail = if (e is WasichaiException) handler.handleWasichai(e) else handler.handleUnexpected(e)

    private fun write(
        exchange: ServerWebExchange,
        problem: ProblemDetail
    ): Mono<Void> {
        // como el problem+json de un controlador, que WebFlux completa con la ruta de la petición
        if (problem.instance == null) problem.instance = URI.create(exchange.request.path.value())
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

        // el registro de una emisión manual, o null si la petición no es una. un id que no es UUID no llega a emitir: el
        // controlador de wasichai responde su 400
        fun targetOf(request: ServerHttpRequest): IssueTarget? {
            if (request.method != HttpMethod.POST) return null
            val match = ROUTE.matchAndExtract(request.path.pathWithinApplication()) ?: return null
            val objectName = match.uriVariables["object"]?.trim()?.lowercase() ?: return null
            val recordId = runCatching { UUID.fromString(match.uriVariables["id"]) }.getOrNull() ?: return null
            return IssueTarget(objectName, recordId)
        }
    }
}
