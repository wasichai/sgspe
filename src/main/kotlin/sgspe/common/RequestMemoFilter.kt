package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange

// abre el memo de la petición (RequestMemo.forRequest) para toda petición de /api (especificación 4.4 §3.10; la medición de la
// Tarea 17 del plan 4.4a lo pidió: una lista de 50 evaluaciones como ANALYST tardaba el doble que como ADMIN por las preguntas
// de permisos, no por el SQL del alcance). la pregunta de permisos de ProjectReach.readsAll y ConfidentialAttentionScope se
// hace una vez por petición y no una vez por objeto y lectura. CoWebFilter pasa el contexto de la corrutina al controlador
// (COROUTINE_CONTEXT_ATTRIBUTE de Spring), así que el memo llega a RecordService y a los RecordReadScope. el memo de la
// petición solo guarda lo que una petición no cambia, los permisos de quien llama (RequestMemo explica por qué); las
// asignaciones (Assignments.isAssigned) y los solicitantes de un compromiso siguen en el memo de su comando, porque una
// petición sí los escribe. cada petición abre un memo nuevo: nada pasa de una a otra
@Component
@Order(RequestMemoFilter.ORDER)
class RequestMemoFilter : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain
    ) {
        if (!exchange.request.path
                .pathWithinApplication()
                .value()
                .startsWith("/api/")
        ) {
            return chain.filter(exchange)
        }
        RequestMemo.forRequest { chain.filter(exchange) }
    }

    companion object {
        // después de la cadena de seguridad (-100) y del filtro de documentos (IssueDocumentFilter, -99): quien llama ya está
        // autenticado. common no conoce scope, por eso el número
        const val ORDER = -98
    }
}
