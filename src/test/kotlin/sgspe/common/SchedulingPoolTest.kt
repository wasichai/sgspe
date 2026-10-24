package sgspe.common

import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// issue #164: Spring corre un @Scheduled(fixedDelay) suspend bloqueando su hilo hasta que termina
// (ScheduledAnnotationReactiveSupport). con el hilo único por defecto, JOB-05 y JOB-09 colgados en una base que no responde
// tapaban los cron de JOB-01, JOB-02 y JOB-04. el pool de application.yml deja hilos libres para los cron
class SchedulingPoolTest {
    private val config = YamlPropertySourceLoader().load("application", ClassPathResource("application.yml")).single()
    private val poolSize = config.getProperty("spring.task.scheduling.pool.size")?.toString()?.toInt() ?: 1

    // los dos sondeos (JOB-05 y JOB-09) y al menos un hilo para los cron
    @Test
    fun `el pool de application yml tiene hilos para los dos sondeos y los cron`() {
        assertTrue(poolSize >= 3, "spring.task.scheduling.pool.size es $poolSize")
    }

    @Test
    fun `dos sondeos colgados no tapan un cron con el pool de application yml`() {
        runner(poolSize).run { context ->
            assertTrue(context.getBean(HungPolls::class.java).cron.await(5, TimeUnit.SECONDS), "el cron no corrió")
        }
    }

    // el control: con el hilo único por defecto, el mismo cron no sale, que es lo que pasó en la demo viva
    @Test
    fun `con un solo hilo el cron no sale`() {
        runner(1).run { context ->
            assertFalse(context.getBean(HungPolls::class.java).cron.await(3, TimeUnit.SECONDS), "el cron corrió con un solo hilo")
        }
    }

    private fun runner(size: Int) =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LifecycleAutoConfiguration::class.java, TaskSchedulingAutoConfiguration::class.java))
            // los sondeos colgados no terminan: sin esto, el cierre del contexto espera 10 s por el planificador
            .withPropertyValues("spring.task.scheduling.pool.size=$size", "spring.lifecycle.timeout-per-shutdown-phase=1s")
            .withUserConfiguration(HungPolls::class.java)

    // dos sondeos como los de JOB-05 y JOB-09, colgados como en una base que no responde, y un cron cada segundo
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    class HungPolls {
        val cron = CountDownLatch(1)

        @Scheduled(fixedDelay = 5000)
        suspend fun deliverables() {
            awaitCancellation()
        }

        @Scheduled(fixedDelay = 5000)
        suspend fun decks() {
            awaitCancellation()
        }

        @Scheduled(cron = "* * * * * *")
        suspend fun deadline() {
            cron.countDown()
        }
    }
}
