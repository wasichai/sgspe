package sgspe.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.util.ClassUtils
import org.springframework.util.unit.DataSize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.WasichaiException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

// los topes del multipart de Spring (spring.webflux.multipart.*, application.yml): un 400 con código, nunca el 500 de una
// DataBufferLimitException sin manejar. los usan la importación (ImportUpload.multipartError, que suma INVALID_MULTIPART y
// su propio texto) y UploadLimitsAdvice, para la subida preparada de wasichai-files (POST /api/objects/{object}/files/{field},
// D1). el tope por parte es el mismo que wasichai.files.max-bytes y que sgspe.territory.import.max-bytes (20 MB, P52):
// cualquier archivo de más llega aquí y responde FILE_TOO_LARGE
object UploadLimits {
    const val PART_MESSAGE = "Una parte del formulario es demasiado grande"

    // la excepción y sus causas; la cadena se recorre acotada: un ciclo de dos no deja el hilo girando
    fun causes(e: Throwable): List<Throwable> = generateSequence(e) { it.cause?.takeIf { next -> next !== it } }.take(MAX_CAUSES).toList()

    // solo el tope de disco de una parte («Part exceeded the disk usage limit», el texto de Spring) es el archivo; cualquier
    // otro «demasiado grande» (los encabezados de una parte, un campo de texto) es la parte. null si `e` no viene de un tope
    fun error(
        e: Throwable,
        maxBytes: Long,
        partMessage: String = PART_MESSAGE
    ): WasichaiException? {
        val limit = causes(e).filterIsInstance<DataBufferLimitException>().firstOrNull() ?: return null
        if (limit.message.orEmpty().startsWith("Part exceeded the disk")) return tooLarge(maxBytes)
        return Finding("PART_TOO_LARGE", partMessage, "multipart", status = HttpStatus.BAD_REQUEST).toCodedException()
    }

    fun tooLarge(maxBytes: Long): WasichaiException =
        Finding("FILE_TOO_LARGE", "Archivo demasiado grande: el máximo es ${label(maxBytes)}", "file", status = HttpStatus.BAD_REQUEST)
            .toCodedException()

    fun multipart(contentType: MediaType?): Boolean = contentType?.isCompatibleWith(MediaType.MULTIPART_FORM_DATA) == true

    // los argumentos con que se llama al @ExceptionHandler `method`: a su primer parámetro Throwable, la primera de `thrown` y
    // sus causas que encaja; a uno que acepta el exchange, el exchange. null si pide otra cosa o si ninguna causa le sirve
    fun handlerArguments(
        method: Method,
        thrown: Throwable,
        exchange: ServerWebExchange
    ): Array<Any?>? {
        val types = method.parameterTypes
        val index = types.indexOfFirst { Throwable::class.java.isAssignableFrom(it) }
        if (index < 0) return null
        val error = causes(thrown).firstOrNull { types[index].isInstance(it) } ?: return null
        return types
            .mapIndexed { i, type ->
                when {
                    i == index -> error
                    type.isInstance(exchange) -> exchange
                    else -> return null
                }
            }.toTypedArray()
    }

    private fun label(bytes: Long): String =
        when {
            bytes >= MB && bytes % MB == 0L -> "${bytes / MB} MB"
            bytes >= KB -> "${bytes / KB} KB"
            else -> "$bytes bytes"
        }

    private const val MAX_CAUSES = 32
    private const val KB = 1024L
    private const val MB = 1024L * 1024
}

// Spring elige un @ExceptionHandler por la excepción o por cualquiera de sus causas, y recorre los advices en orden: este,
// el primero, recibe toda excepción con una DataBufferLimitException en su cadena, también el ContentTooLargeException (413)
// de un cuerpo JSON que pasa spring.codec.max-in-memory-size. por eso traduce solo un multipart cuya excepción de afuera no
// es un ResponseStatusException (la subida de wasichai-files recibe el archivo como @RequestPart, y Spring entrega la
// DataBufferLimitException sin envolver); lo demás va al método que el manejador de wasichai habría elegido, porque WebFlux
// no pasa al advice siguiente (un error aquí llegaría al manejador de Boot). el problem+json sale del manejador de wasichai
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UploadLimitsAdvice(
    private val handler: GlobalExceptionHandler,
    @Value("\${wasichai.files.max-bytes:20MB}") maxBytes: DataSize
) {
    private val maxBytes = maxBytes.toBytes()
    private val wasichai = ExceptionHandlerMethodResolver(ClassUtils.getUserClass(handler))

    // `thrown` es la excepción de afuera: Spring ofrece la excepción y después sus causas, y da el primero que encaja
    @ExceptionHandler(DataBufferLimitException::class)
    fun limit(
        thrown: Throwable,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ProblemDetail>> {
        val coded =
            if (UploadLimits.multipart(exchange.request.headers.contentType) &&
                thrown !is ResponseStatusException
            ) {
                UploadLimits.error(thrown, maxBytes)
            } else {
                null
            }
        return if (coded != null) Mono.just(entity(handler.handleWasichai(coded))) else delegate(thrown, exchange)
    }

    // el método de GlobalExceptionHandler para `thrown` (handleResponseStatus para el 413), con los argumentos que pide (la
    // causa que acepta su primer parámetro Throwable y el exchange, UploadLimits.handlerArguments). un método que pide otra
    // cosa, o ninguno, cae en handleUnexpected: el 500 de wasichai con su problem+json y su línea en el log
    private fun delegate(
        thrown: Throwable,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ProblemDetail>> {
        val method = wasichai.resolveMethodByThrowable(thrown) ?: return unexpected(thrown)
        val arguments = UploadLimits.handlerArguments(method, thrown, exchange) ?: return unexpected(thrown)
        val answer =
            try {
                method.invoke(handler, *arguments)
            } catch (e: InvocationTargetException) {
                return Mono.error(e.targetException)
            }
        return if (answer is Mono<*>) answer.map(::entity) else Mono.just(entity(answer))
    }

    // un Error (no una Exception) no se traduce: sigue su camino
    private fun unexpected(thrown: Throwable): Mono<ResponseEntity<ProblemDetail>> =
        if (thrown is Exception) Mono.just(entity(handler.handleUnexpected(thrown))) else Mono.error(thrown)

    @Suppress("UNCHECKED_CAST")
    private fun entity(answer: Any?): ResponseEntity<ProblemDetail> =
        when (answer) {
            is ResponseEntity<*> -> answer as ResponseEntity<ProblemDetail>
            is ProblemDetail -> ResponseEntity.status(answer.status).body(answer)
            else -> error("GlobalExceptionHandler respondió ${answer?.javaClass?.name}")
        }
}
