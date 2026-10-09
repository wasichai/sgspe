package sgspe.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.util.unit.DataSize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.WasichaiException

// los topes del multipart de Spring (spring.webflux.multipart.*, application.yml) fuera de las importaciones, que los traducen
// ellas mismas (ImportUpload.multipartError). la subida preparada de wasichai-files (POST /api/objects/{object}/files/{field},
// D1) recibe el archivo como @RequestPart, y Spring entrega la DataBufferLimitException de un archivo que pasa
// max-disk-usage-per-part sin traducirla: sin esto sería el 500 de una excepción sin manejar. un archivo que pasa los 20 MB
// de wasichai.files.max-bytes pero no los 25 MB de Spring lo rechaza wasichai-files; uno que pasa los dos llega aquí
object UploadLimits {
    // solo el tope de disco de una parte («Part exceeded the disk usage limit») es el archivo; cualquier otro «demasiado
    // grande» (los encabezados de una parte, un campo de texto) es la parte. null si `e` no viene de un tope. la cadena de
    // causas se recorre acotada
    fun error(
        e: Throwable,
        maxBytes: Long
    ): WasichaiException? {
        val limit =
            generateSequence(e) { it.cause?.takeIf { next -> next !== it } }
                .take(MAX_CAUSES)
                .filterIsInstance<DataBufferLimitException>()
                .firstOrNull() ?: return null
        val finding =
            if (limit.message.orEmpty().startsWith("Part exceeded the disk")) {
                Finding("FILE_TOO_LARGE", "Archivo demasiado grande: el máximo es ${label(maxBytes)}", "file", status = HttpStatus.BAD_REQUEST)
            } else {
                Finding("PART_TOO_LARGE", "Una parte del formulario es demasiado grande", "multipart", status = HttpStatus.BAD_REQUEST)
            }
        return finding.toCodedException()
    }

    private fun label(bytes: Long): String = if (bytes >= MB && bytes % MB == 0L) "${bytes / MB} MB" else "$bytes bytes"

    private const val MAX_CAUSES = 32
    private const val MB = 1024L * 1024
}

// antes que el manejador de wasichai, que respondería 500 a una DataBufferLimitException (su manejador de Exception); el
// problem+json sale de ese mismo manejador, así que el 400 tiene la forma de cualquier otro
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UploadLimitsAdvice(
    private val handler: GlobalExceptionHandler,
    @Value("\${wasichai.files.max-bytes:20MB}") private val maxBytes: DataSize
) {
    @ExceptionHandler(DataBufferLimitException::class)
    fun tooLarge(e: DataBufferLimitException): ProblemDetail = handler.handleWasichai(UploadLimits.error(e, maxBytes.toBytes()) ?: throw e)
}
