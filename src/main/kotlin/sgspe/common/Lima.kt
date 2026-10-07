package sgspe.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

// la hora de sgspe es la de Lima, no la de la máquina (architecture.md §5.2), como Lima.kt de caja-backend@dc9e391
val LIMA: ZoneId = ZoneId.of("America/Lima")

// el reloj de sgspe. una prueba lo sustituye con un @Bean @Primary propio (Clock.fixed): nada de sgspe lee la hora de otro
// sitio
@Configuration(proxyBeanMethods = false)
class Clocks {
    @Bean
    fun clock(): Clock = Clock.system(LIMA)
}
