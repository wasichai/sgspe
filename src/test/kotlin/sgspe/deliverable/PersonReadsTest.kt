package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.common.Records
import sgspe.common.RuleException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException

// las lecturas de AI-04 como la persona (decisión 16 del plan 8.2c): lo que no lee sale vacío; el tope de filas y cualquier otro
// error se propagan, para que la corrida falle a la vista en vez de redactar con tablas vacías
class PersonReadsTest {
    @Test
    fun `lo que la persona no lee sale vacio`() {
        assertEquals(emptyList<String>(), runBlocking { ReportSources.readable(emptyList<String>()) { throw ForbiddenException("sin READ") } })
        assertEquals(null, runBlocking { ReportSources.readable<String?>(null) { throw NotFoundException("no existe") } })
        assertEquals(listOf("a"), runBlocking { ReportSources.readable(emptyList()) { listOf("a") } })
    }

    @Test
    fun `el tope de filas y los demas errores se propagan`() {
        val tooMany =
            assertThrows<WasichaiException> { runBlocking { ReportSources.readable(emptyList<String>()) { throw Records.tooMany("engagement", 20_000) } } }
        assertEquals(HttpStatus.BAD_REQUEST, tooMany.status)
        assertThrows<RuleException> {
            runBlocking { ReportSources.readable(emptyList<String>()) { throw RuleException(HttpStatus.UNPROCESSABLE_CONTENT, "otra") } }
        }
    }
}
