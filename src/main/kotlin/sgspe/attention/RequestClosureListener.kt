package sgspe.attention

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.ConfidentialRelease
import sgspe.common.Criteria
import sgspe.common.Records
import sgspe.common.TenantModel
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.core.platform.ChangeOrigin
import java.util.UUID

// RequestClosureListener (RN-GRV-001, ASM-068, workflows.md WF-E3): en la transacción de `close` de WF-D aplica close_accepted
// (transición de comando, con la marca) a la solicitud aceptada que originó el compromiso, con el motivo «RN-GRV-001:
// compromiso <código> cerrado»; si no puede, lanza y el cierre se revierte. es una acción del sistema, no de quien cierra: la
// solicitud puede ser confidencial y quien cierra no leerla (decisión 4 del plan 4.2a). wasichai no deja ser la plataforma
// dentro de una petición (RecordService.asPlatform lanza) ni aplicar una transición sin usuario, así que corre como quien
// cierra, pero dentro de ConfidentialRelease, con el que ConfidentialAttentionScope admite además las solicitudes de este
// compromiso, y solo ellas, mientras dura el bloque; y con el origen SOURCE en la auditoría (ChangeOrigin), que distingue el
// cierre automático de uno a mano. vive en su propio archivo: es el único que abre ConfidentialRelease (ArchitectureTest).
// los listeners y las guardas que dispara close_accepted dentro del bloque también leen como quien cierra con la
// liberación puesta, acotada a las solicitudes de este compromiso (decisión 4). un tenant sin el modelo de F4 no tiene
// stakeholder_request: el cierre de sus compromisos (WF-D de F3) no busca solicitudes (TenantModel)
@Component
@Order(130)
class RequestClosureListener(
    private val records: Records,
    private val tenant: TenantModel
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Acceptances.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED || change.transition != CLOSE) return
        if (!tenant.has(change.organizationId, Attention.REQUEST)) return
        val code = Values.text((change.after ?: change.before).orEmpty()["code"]) ?: change.recordId.toString()
        ConfidentialRelease.within(Attention.REQUEST, AttentionRules.CREATED_COMMITMENT, change.recordId) {
            ChangeOrigin.within(SOURCE) {
                val requests =
                    records.all(
                        Attention.REQUEST,
                        filters = mapOf(AttentionRules.CREATED_COMMITMENT to change.recordId.toString()),
                        criteria = listOf(Criteria.inState(listOf(ACCEPTED)))
                    )
                for (request in requests) {
                    records.transition(Attention.REQUEST, UUID.fromString(request.id), CLOSE_ACCEPTED, "RN-GRV-001: compromiso $code cerrado")
                }
            }
        }
    }

    companion object {
        const val CLOSE = "close"
        const val ACCEPTED = "accepted"
        const val CLOSE_ACCEPTED = "close_accepted"

        // el origen de sus escrituras en la auditoría (WAS-ADR-0050): el cierre automático, no una acción de quien cierra
        const val SOURCE = "listener:request-closure"
    }
}
