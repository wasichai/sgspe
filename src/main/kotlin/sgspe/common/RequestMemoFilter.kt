package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange

// abre el memo de la petición (RequestMemo.forRequest) para toda petición de /api (especificación 4.4 §3.10; la medición de la
// Tarea 17 del plan 4.4a lo pidió: una lista de 50 evaluaciones como ANALYST tardaba el doble que como ADMIN por las preguntas
// de permisos, no por el SQL del alcance). la pregunta de permisos de ProjectReach.readsAll y ConfidentialAttentionScope se
// hace una vez por petición y no una vez por objeto y lectura. CoWebFilter pasa el contexto de la corrutina al controlador
// (COROUTINE_CONTEXT_ATTRIBUTE de Spring), así que el memo llega a RecordService y a los RecordReadScope. el memo de la
// petición solo guarda lo que una petición no cambia, los permisos de quien llama (RequestMemo explica por qué).
// un comando de SGSPE (POST, PUT, PATCH o DELETE bajo /api/sgspe/) abre además el memo del comando (RequestMemo.within; plan
// de la caché del esquema, Tarea 6): ProjectScopeGuard pregunta Assignments.isAssigned en el alta y en cada vínculo, y el alta
// de una entrega con tres evidencias leía project_assignment unas 7 veces. vale porque ningún comando de /api/sgspe escribe
// asignaciones, ítems de aporte ni solicitantes de un compromiso (lo que guarda ese memo). la API genérica escribe un registro
// por petición y no lo abre. cada petición abre memos nuevos: nada pasa de una a otra
@Component
@Order(RequestMemoFilter.ORDER)
class RequestMemoFilter : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain
    ) {
        val path =
            exchange.request.path
                .pathWithinApplication()
                .value()
        if (!path.startsWith("/api/")) return chain.filter(exchange)
        if (path.startsWith(COMMANDS) && exchange.request.method in WRITES) {
            RequestMemo.forRequest { RequestMemo.within { chain.filter(exchange) } }
        } else {
            RequestMemo.forRequest { chain.filter(exchange) }
        }
    }

    companion object {
        // después de la cadena de seguridad (-100) y del filtro de documentos (IssueDocumentFilter, -99): quien llama ya está
        // autenticado. common no conoce scope, por eso el número
        const val ORDER = -98

        // los comandos de SGSPE: sus escrituras abren el memo del comando
        private const val COMMANDS = "/api/sgspe/"
        private val WRITES = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
    }
}
