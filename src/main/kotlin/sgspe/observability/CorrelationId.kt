package sgspe.observability

import reactor.core.publisher.Mono
import java.util.UUID

// el identificador de correlación de una petición (ASM-034, ADR-0026, api.md §3.8): el que manda el cliente si cumple el
// formato, si no uno nuevo. un valor inválido nunca se devuelve tal cual: podría inyectar texto en una cabecera o en un log
object CorrelationId {
    const val HEADER = "X-Correlation-Id"

    // la clave en el contexto de Reactor y en el MDC de los logs de sgspe
    const val KEY = "correlationId"

    private val FORMAT = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun accept(sent: String?): String = if (sent != null && FORMAT.matches(sent)) sent else UUID.randomUUID().toString()

    // el de la petición en curso, para el código de sgspe que corre en su cadena reactiva
    fun current(): Mono<String> = Mono.deferContextual { context -> Mono.justOrEmpty(context.getOrEmpty<String>(KEY)) }
}
