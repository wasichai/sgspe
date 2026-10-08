package sgspe.common

import kotlinx.coroutines.CancellationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener

// el PUT de wasichai deja vacío todo campo escribible que el cuerpo no trae, y el portal (Fase 2.3) no manda los derivados:
// después de un cambio sin la marca, cada derivado que tenía valor y quedó vacío vuelve a su valor, con la marca y el
// motivo de abajo. un null explícito no llega aquí: es un cambio, y DerivedFieldsGuard lo rechazó con un 400. corre antes
// que los listeners que recalculan (AreaListener), que después escriben su valor nuevo. antes de devolver un derivado se
// lee lo guardado: si otra escritura ya le dio valor entre el PUT y este listener, ese valor no se pisa
@Component
@Order(100)
class DerivedFieldsKeeper(
    private val catalog: ModelCatalog,
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.UPDATED || WriteMark.present()) return
        val before = change.before ?: return
        val after = change.after ?: return
        if (after.keys.any { it.startsWith("rel:") }) return
        val lost = catalog.derivedFields(change.objectName).filter { before[it] != null && after.containsKey(it) && after[it] == null }
        if (lost.isEmpty()) return
        restore(
            log,
            "${change.objectName} ${change.recordId}",
            lost,
            before,
            stored = { records.get(change.objectName, change.recordId).attributes },
            patch = { values -> records.patch(change.objectName, change.recordId, values, REASON) }
        )
    }

    companion object {
        const val REASON = "derivado: se conserva lo que el PUT no trajo"

        // devuelve de `lost` solo lo que sigue vacío en lo guardado (`stored` se lee en este momento). un fallo se registra y
        // no rompe la escritura que ya se hizo; la cancelación de la corrutina no es un fallo: se relanza
        suspend fun restore(
            log: Logger,
            label: String,
            lost: Collection<String>,
            before: Map<String, Any?>,
            stored: suspend () -> Map<String, Any?>,
            patch: suspend (Map<String, Any?>) -> Unit
        ) {
            try {
                val current = stored()
                val values = lost.filter { current[it] == null }.associateWith { before[it] }
                if (values.isNotEmpty()) patch(values)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("DerivedFieldsKeeper: no se devolvieron {} de {}: {}", lost, label, e.message)
            }
        }
    }
}
