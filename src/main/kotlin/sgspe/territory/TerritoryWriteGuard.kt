package sgspe.territory

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Transactions
import sgspe.common.WriteView
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// los miembros de una versión de un área de influencia que ya no está en draft (`approved` o `superseded`, WF-O) no
// cambian (RN-TER-003): una corrección es una versión nueva. vale para altas, cambios y bajas de un miembro, para el
// miembro que se mueve de versión: el camino real es el atributo `area_of_influence` de POST, PUT y DELETE, porque las
// relaciones del miembro son MANY_TO_ONE y la API de relaciones de wasichai (`/related/...`) solo vincula MANY_TO_MANY (400).
// por si un módulo o una versión posterior de wasichai vinculara un MANY_TO_ONE, también se miran los vínculos, que
// wasichai manda como dos UPDATE (uno por cada registro) con `rel:<relación>` en los atributos; esa rama es defensiva
// el estado se lee como quien llama (no se puede usar asPlatform dentro de una petición); si no puede leer el área no puede
// verificar que esté en draft, y es un 403 que lo dice y no un falso verde
@Component
@Order(GuardOrder.DOMAIN)
class TerritoryWriteGuard(
    private val records: Records,
    private val view: WriteView,
    private val transactions: Transactions
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind == RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.objectName != MEMBER && change.objectName != Areas.AREA_OF_INFLUENCE) return emptyList()
        val after = if (change.objectName == MEMBER && change.kind != RecordChangeKind.DELETED) view.after(change) else emptyMap()
        // el estado se lee con el candado de la versión, el mismo que toma su aprobación: una aprobación en curso termina antes
        // de que se lea el estado, así que un miembro no se cuela en una versión recién aprobada
        return check(change, after) { area -> transactions.locked(AreasOfInfluence.areaLock(area)) { records.find(Areas.AREA_OF_INFLUENCE, area)?.state } }
    }

    companion object {
        const val MEMBER = "area_of_influence_member"
        const val AREA_FIELD = "area_of_influence"

        // la relación del miembro con su área (database-model.md); hoy solo se cambia por el atributo, ver arriba
        const val AREA_RELATION = "aoi_member_area_of_influence"
        const val DRAFT = "draft"

        private const val LINK_PREFIX = "rel:"
        private val STATE_LABELS = mapOf("approved" to "aprobada", "superseded" to "reemplazada")

        // las áreas cuyos miembros toca la escritura: las de antes y las de después de un miembro, el destino de un vínculo
        // y el área misma cuando lo que se vincula o desvincula en ella son miembros
        internal fun areasOf(
            change: RecordWrite,
            after: Map<String, Any?>
        ): Set<UUID> =
            when (change.objectName) {
                MEMBER ->
                    setOfNotNull(
                        Records.uuid(change.before?.get(AREA_FIELD)),
                        Records.uuid(after[AREA_FIELD]),
                        Records.uuid(change.attributes?.get(LINK_PREFIX + AREA_RELATION))
                    )
                Areas.AREA_OF_INFLUENCE ->
                    if (change.attributes.orEmpty().containsKey(LINK_PREFIX + AREA_RELATION)) setOfNotNull(change.recordId) else emptySet()
                else -> emptySet()
            }

        // `after`: cómo queda el miembro (WriteView.after); `stateOf`: el estado de un área, null si no existe o no se ve
        internal suspend fun check(
            change: RecordWrite,
            after: Map<String, Any?>,
            stateOf: suspend (UUID) -> String?
        ): List<Finding> =
            areasOf(change, after).mapNotNull { area ->
                val state =
                    try {
                        stateOf(area)
                    } catch (_: ForbiddenException) {
                        return@mapNotNull Finding(
                            "OBJECT_NOT_READABLE",
                            "No tiene permiso para leer $AREA_FIELD, así que no se puede verificar que la versión esté en borrador",
                            AREA_FIELD,
                            status = HttpStatus.FORBIDDEN
                        )
                    }
                if (state == null || state == DRAFT) {
                    null
                } else {
                    Finding(
                        "FROZEN_MEMBERS",
                        "Los miembros de una versión ${STATE_LABELS[state] ?: state} no cambian: cree una versión nueva del área (RN-TER-003)",
                        AREA_FIELD
                    )
                }
            }
    }
}
