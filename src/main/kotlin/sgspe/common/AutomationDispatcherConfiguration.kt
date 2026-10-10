package sgspe.common

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import wasichai.automation.AutomationDispatcher
import wasichai.automation.AutomationProperties
import wasichai.automation.AutomationRepository
import wasichai.automation.AutomationRunRepository
import wasichai.core.data.RecordChange

// el despachador de automatizaciones de wasichai-automation con una sola diferencia: no evalúa el eco de una reposición de
// DerivedFieldsKeeper (DerivedRestore), que no es un cambio (ronda branchfix de 4.2b, R-I2). todo lo demás (las condiciones,
// las corridas, la profundidad y causedBy) es el de wasichai
class RestoreAwareAutomationDispatcher(
    automations: AutomationRepository,
    runs: AutomationRunRepository,
    properties: AutomationProperties
) : AutomationDispatcher(automations, runs, properties) {
    override suspend fun recordChanged(change: RecordChange) {
        if (DerivedRestore.current()?.echoes(change) == true) return
        super.recordChanged(change)
    }
}

// reemplaza al despachador de la autoconfiguración de wasichai-automation, que se declara @ConditionalOnMissingBean, con
// su misma condición: debe quedar uno solo, porque RecordService avisa a todos los RecordChangeListener y dos duplicarían
// cada corrida (SgspeSmokeTest lo vigila)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wasichai.automation", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class AutomationDispatcherConfiguration {
    @Bean
    fun restoreAwareAutomationDispatcher(
        automations: AutomationRepository,
        runs: AutomationRunRepository,
        properties: AutomationProperties
    ): AutomationDispatcher = RestoreAwareAutomationDispatcher(automations, runs, properties)
}
