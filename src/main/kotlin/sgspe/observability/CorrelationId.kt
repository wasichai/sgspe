package sgspe.observability

import reactor.core.publisher.Mono
import java.util.UUID

// el identificador de correlación de una petición (ASM-034, ADR-0026, api.md §3.8): el que manda el cliente si cumple el
// formato, si no uno nuevo. un valor inválido nunca se devuelve tal cual: podría inyectar texto en una cabecera o en un log
object CorrelationId {
    const val HEADER = "X-Correlation-Id"

    // la clave en el contexto de Reactor y en el MDC de la línea de acceso. es la misma de wasichai (ChangeOrigin.MDC_KEY),
    // que desde 0.5.0 la pasa del contexto de Reactor al MDC de todos los logs de la petición [WAS-ADR-0050]
    const val KEY = "correlationId"

    private val FORMAT = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun accept(sent: String?): String = if (sent != null && valid(sent)) sent else UUID.randomUUID().toString()

    fun valid(value: String): Boolean = FORMAT.matches(value)

    // el de la petición en curso, para el código de sgspe que corre en su cadena reactiva
    fun current(): Mono<String> = Mono.deferContextual { context -> Mono.justOrEmpty(context.getOrEmpty<String>(KEY)) }
}
