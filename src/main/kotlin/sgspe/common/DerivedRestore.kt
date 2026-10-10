package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import wasichai.automation.AutomationDispatcher
import wasichai.automation.AutomationProperties
import wasichai.automation.AutomationRepository
import wasichai.automation.AutomationRunRepository
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// la escritura con que DerivedFieldsKeeper repone los derivados que un PUT dejó vacíos (`values`, los de antes del PUT): un
// elemento del contexto de la corrutina mientras dura esa escritura. reponer un valor igual al guardado no es un cambio
// (ronda branchfix de 4.2b, R-I2): el PUT de un riesgo CRITICO deja vacío risk_level y la reposición lo devuelve, y sin esto
// AUT-11 (risk_level CHANGED y EQUALS CRITICO) vería el vacío -> CRITICO como una subida y crearía otra tarea por edición
class DerivedRestore(
    val objectName: String,
    val recordId: UUID,
    val values: Map<String, Any?>
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<DerivedRestore> {
        suspend fun current(): DerivedRestore? = currentCoroutineContext()[Key]
    }

    // si `change` es el eco de la reposición: el cambio del mismo registro que lleva cada derivado repuesto de vacío a su valor
    // de antes del PUT. una escritura anidada sobre el registro (un listener que reacciona a la reposición) ya lee los valores
    // repuestos en su `before`, así que no es el eco y las automatizaciones la ven
    fun echoes(change: RecordChange): Boolean {
        if (change.kind != RecordChangeKind.UPDATED || change.objectName != objectName || change.recordId != recordId) return false
        val before = change.before ?: return false
        val after = change.after ?: return false
        return values.isNotEmpty() && values.all { (field, value) -> Values.text(before[field]) == null && Values.same(after[field], value) }
    }

    override fun toString() = "DerivedRestore($objectName $recordId ${values.keys})"
}

// el despachador de automatizaciones de wasichai-automation con una sola diferencia: no evalúa el eco de una reposición de
// DerivedFieldsKeeper (DerivedRestore), que no es un cambio. todo lo demás (las condiciones, las corridas, la profundidad y
// causedBy) es el de wasichai. reemplaza al de la autoconfiguración, que se declara @ConditionalOnMissingBean
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

@Configuration
@ConditionalOnProperty(prefix = "wasichai.automation", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class AutomationDispatcherConfiguration {
    @Bean
    fun restoreAwareAutomationDispatcher(
        automations: AutomationRepository,
        runs: AutomationRunRepository,
        properties: AutomationProperties
    ): AutomationDispatcher = RestoreAwareAutomationDispatcher(automations, runs, properties)
}
