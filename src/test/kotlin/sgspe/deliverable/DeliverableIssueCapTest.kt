package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import wasichai.core.common.WasichaiException

// menor del plan 8.2b: emitir copia cada candidata con el tope de los archivos de entregables; más grande, 400 FILE_TOO_LARGE
class DeliverableIssueCapTest {
    private fun content(size: Int) = Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(ByteArray(size)))

    @Test
    fun `una candidata mas grande que el tope es 400 FILE_TOO_LARGE y una dentro vuelve entera`() {
        assertEquals(16, runBlocking { DeliverableIssue.capped(content(16), 16) }.size)
        val refused = assertThrows<WasichaiException> { runBlocking { DeliverableIssue.capped(content(17), 16) } }
        assertEquals("FILE_TOO_LARGE", refused.violations.first().message)
    }
}
