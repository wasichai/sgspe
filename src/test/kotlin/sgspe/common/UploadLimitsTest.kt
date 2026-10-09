package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation

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
}
