package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.util.unit.DataSize
import org.springframework.web.server.ContentTooLargeException
import wasichai.core.common.FieldViolation
import wasichai.core.common.GlobalExceptionHandler

// los topes del multipart de Spring fuera de las importaciones (la subida preparada de wasichai-files): un 400 con código,
// nunca el 500 de una excepción sin manejar (revisión final del plan 3.1), sin base
class UploadLimitsTest {
    private val max = 20L * 1024 * 1024

    @Test
    fun `el tope de disco de una parte es 400 FILE_TOO_LARGE sobre file con el maximo de wasichai-files`() {
        val e = UploadLimits.error(DataBufferLimitException("Part exceeded the disk usage limit of 26214400 bytes"), max)!!
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
        assertEquals(listOf(FieldViolation("file", "FILE_TOO_LARGE")), e.violations)
        assertEquals("Archivo demasiado grande: el máximo es 20 MB", e.message)
    }

    @Test
    fun `otro tope del multipart es 400 PART_TOO_LARGE sobre multipart, tambien envuelto`() {
        val e = UploadLimits.error(IllegalStateException("envuelta", DataBufferLimitException("Part headers exceeded the memory usage limit")), max)!!
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
        assertEquals(listOf(FieldViolation("multipart", "PART_TOO_LARGE")), e.violations)
    }

    @Test
    fun `una excepcion que no viene de un tope no se traduce`() {
        assertNull(UploadLimits.error(IllegalStateException("otra cosa"), max))
    }

    // ---- el advice: solo un multipart cuya excepción de afuera no es un ResponseStatusException ----

    private val advice = UploadLimitsAdvice(GlobalExceptionHandler("https://sgspe.test/problems/"), DataSize.ofMegabytes(20))

    private fun request(type: MediaType) = MockServerWebExchange.from(MockServerHttpRequest.post("/api/objects/evidence/files/file").contentType(type))

    private fun disk() = DataBufferLimitException("Part exceeded the disk usage limit of 20971520 bytes")

    private fun codec() = ContentTooLargeException(DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144"))

    @Test
    fun `un multipart que pasa el tope por parte es 400 FILE_TOO_LARGE con el problem+json de wasichai`() {
        val answer = advice.limit(disk(), request(MediaType.MULTIPART_FORM_DATA)).block()!!
        assertEquals(400, answer.statusCode.value())
        val errors = answer.body!!.properties!!["errors"].toString()
        assertTrue(errors.contains("FILE_TOO_LARGE") && errors.contains("file"), errors)
    }

    @Test
    fun `un cuerpo JSON que pasa el tope del codec sigue siendo el 413 de wasichai, tambien en un multipart`() {
        assertEquals(
            413,
            advice
                .limit(codec(), request(MediaType.APPLICATION_JSON))
                .block()!!
                .statusCode
                .value()
        )
        assertEquals(
            413,
            advice
                .limit(codec(), request(MediaType.MULTIPART_FORM_DATA))
                .block()!!
                .statusCode
                .value()
        )
    }

    @Test
    fun `fuera de un multipart el tope va al metodo que wasichai habria elegido`() {
        assertEquals(
            500,
            advice
                .limit(disk(), request(MediaType.APPLICATION_JSON))
                .block()!!
                .statusCode
                .value()
        )
    }
}
