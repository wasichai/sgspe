package sgspe.common

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.slf4j.Logger
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// issue #164: una base que no responde termina en error y no en espera indefinida. la lectura de JOB-05 y JOB-09 lanza un
// error común (la organización falla y la vuelta sigue) y la vuelta entera de cada trabajo se corta con una línea de log
class JobTimeoutsTest {
    private val short = Duration.ofMillis(50)

    @Test
    fun `una lectura que no responde lanza un error comun, no una cancelacion`() {
        val e = assertThrows<JobTimeoutException> { runBlocking { JobTimeouts.read("pendientes", short) { awaitCancellation() } } }
        assertEquals("pendientes", e.step)
        assertEquals(short, e.limit)
    }

    @Test
    fun `una lectura a tiempo devuelve lo del bloque, tambien null`() {
        assertEquals(3, runBlocking { JobTimeouts.read("pendientes", short) { 3 } })
        assertNull(runBlocking { JobTimeouts.read<Int?>("pendientes", short) { null } })
    }

    @Test
    fun `el plazo de un bloque interior no se confunde con el propio`() {
        assertThrows<TimeoutCancellationException> {
            runBlocking { JobTimeouts.read("pendientes", Duration.ofSeconds(5)) { withTimeout(10.milliseconds) { awaitCancellation() } } }
        }
    }

    @Test
    fun `una vuelta colgada se corta con una linea de log y devuelve null`() {
        val log = mock(Logger::class.java)
        assertNull(runBlocking { JobTimeouts.run(log, "JOB-01 DeadlineJob", short) { awaitCancellation() } })
        verify(log).error("{}: {} pasó el plazo de {} y la vuelta se cortó", "JOB-01 DeadlineJob", "la vuelta", short)
    }

    @Test
    fun `una lectura colgada dentro de la vuelta tambien la corta, con su nombre`() {
        val log = mock(Logger::class.java)
        assertNull(
            runBlocking { JobTimeouts.run(log, "JOB-05", Duration.ofSeconds(5)) { JobTimeouts.read("las organizaciones", short) { awaitCancellation() } } }
        )
        verify(log).error("{}: {} pasó el plazo de {} y la vuelta se cortó", "JOB-05", "las organizaciones", short)
    }

    @Test
    fun `una vuelta a tiempo devuelve lo suyo sin log`() {
        val log = mock(Logger::class.java)
        assertEquals(7, runBlocking { JobTimeouts.run(log, "JOB-01 DeadlineJob", short) { 7 } })
        verifyNoInteractions(log)
    }

    @Test
    fun `los plazos por defecto, los de application yml y sus limites`() {
        val defaults = JobsProperties()
        assertEquals(Duration.ofMinutes(2), defaults.readTimeout)
        assertEquals(Duration.ofMinutes(50), defaults.runTimeout)
        val config = YamlPropertySourceLoader().load("application", ClassPathResource("application.yml")).single()
        assertEquals("2m", config.getProperty("sgspe.jobs.read-timeout")?.toString())
        assertEquals("50m", config.getProperty("sgspe.jobs.run-timeout")?.toString())
        assertThrows<IllegalArgumentException> { JobsProperties(readTimeout = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { JobsProperties(runTimeout = Duration.ofSeconds(30)) }
        assertThrows<IllegalArgumentException> { JobsProperties(readTimeout = Duration.ofMinutes(10), runTimeout = Duration.ofMinutes(5)) }
    }
}
