package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
import java.util.UUID

// D9 de WF-D, pura (RN-COM-001, ASM-009): la evidencia mínima de una entrega según el tipo de recurso
object Fulfillment {
    const val MATERIAL = "MATERIAL"
    val MATERIAL_TYPES = listOf("ACTA DE ENTREGA", "FOTO")
    val NON_MATERIAL_TYPES = setOf("ACTA", "CARTA", "OTRO")

    // una entrega como la ve la regla: su conformidad y los tipos de las evidencias vinculadas que cuentan
    data class DeliveryEvidence(
        val conformityGiven: Boolean,
        val conformityBy: String?,
        val evidenceTypes: Set<String>
    )

    // los tipos de las evidencias vinculadas que cuentan para D9: solo las del proyecto del compromiso (P54). una evidencia de
    // otro proyecto, o sin proyecto, vinculada a la entrega no sostiene el cumplimiento
    fun countedTypes(
        project: UUID?,
        evidence: List<Map<String, Any?>>
    ): Set<String> {
        if (project == null) return emptySet()
        return evidence
            .filter { Records.uuid(it["project"]) == project }
            .mapNotNull { Values.text(it["evidence_type"]) }
            .toSet()
    }

    // sin tipo de recurso no se sabe qué evidencia pedir: falla cerrada (no cuenta como NO MATERIAL). D1 lo exige al enviar y
    // al aprobar, pero en ejecución el campo se puede vaciar
    fun resourceTypeFinding(resourceType: String?): Finding? =
        if (resourceType != null) {
            null
        } else {
            Finding(
                "FIELD_REQUIRED",
                "Registrar el cumplimiento exige el tipo de recurso del compromiso: decide la evidencia mínima (D9, ASM-009)",
                RESOURCE_TYPE
            )
        }

    // los tipos de evidencia que le faltan a una entrega con conformidad; vacío si la entrega basta
    fun missing(
        resourceType: String,
        delivery: DeliveryEvidence
    ): List<String> =
        if (resourceType == MATERIAL) {
            MATERIAL_TYPES.filter { it !in delivery.evidenceTypes }
        } else if (delivery.evidenceTypes.none { it in NON_MATERIAL_TYPES }) {
            listOf(NON_MATERIAL_TYPES.sorted().joinToString(" o "))
        } else {
            emptyList()
        }

    fun conforms(delivery: DeliveryEvidence): Boolean = delivery.conformityGiven && Values.text(delivery.conformityBy) != null

    // el hallazgo de D9 sobre todas las entregas del compromiso: pasa si alguna basta
    fun finding(
        resourceType: String,
        deliveries: List<DeliveryEvidence>
    ): Finding? {
        val conforming = deliveries.filter { conforms(it) }
        if (conforming.isEmpty()) {
            return Finding(
                "DELIVERY_REQUIRED",
                "Registrar el cumplimiento exige una entrega con conformidad del grupo de interés y quiénes la dieron (D9, RN-COM-001)",
                Commitments.DELIVERY
            )
        }
        if (conforming.any { missing(resourceType, it).isEmpty() }) return null
        val missing = conforming.minBy { missing(resourceType, it).size }.let { missing(resourceType, it) }
        return Finding(
            "EVIDENCE_REQUIRED",
            "A la entrega con conformidad le falta evidencia vinculada del proyecto: ${missing.joinToString(", ")} (D9, ASM-009)",
            Commitments.EVIDENCE_DELIVERY
        )
    }

    // D10: una dependencia bloqueante sigue abierta mientras el compromiso del que depende no se cumple ni se cancela
    val RESOLVED = Commitments.SETTLED

    const val RESOURCE_TYPE = "resource_type"
    const val REGISTER = "register_fulfillment"
    const val VERIFY = "verify"

    // las cancelaciones de WF-D (model/workflows/commitment.json)
    val CANCELS = setOf("cancel_draft", "cancel_pending_approval", "cancel_approved", "cancel_in_progress", "cancel_blocked")

    // las evidencias de una entrega que D9 lee como mucho (las lecturas M2M son acotadas): más es un 400 TOO_MANY_RECORDS
    const val MAX_EVIDENCE = 500
}

// CommitmentWriteGuard, cumplimiento y verificación (workflows.md WF-D): D9 (register_fulfillment exige una entrega con
// conformidad y la evidencia de su tipo de recurso), D10 (ninguna dependencia bloqueante abierta, RF-COM-004) y D11 (verify
// exige la acción VERIFY y que quien verifica no sea el responsable, RN-COM-005). cada regla tiene su lugar desde
// CommitmentRuleOrder.FULFILLMENT (P49): primero el candado, después D9, D10 y D11. leen las entregas, sus evidencias y las
// dependencias como quien aplica la transición: sin lectura, OBJECT_NOT_READABLE; sin lectura de conformity_by (un campo
// restringido), FIELD_NOT_READABLE.
//
// el candado del compromiso (Reschedules.lock), el mismo de las entregas, las reprogramaciones y DELIVERY_FROZEN: lo toman
// register_fulfillment, antes de que D9 y D10 lean, y las cancel_*, que congelan las entregas (P55). así una entrega nueva, una
// corrección o un vínculo de evidencia que se quita no se cruzan con el cumplimiento ni con la cancelación. la guarda corre
// dentro de la transacción de WorkflowService.apply, así que el candado transaccional se une a ella y se suelta con su commit
// o su rollback (como el de la campaña en SupportPolicyGuard). la consulta en seco no lo toma
@Component
class FulfillmentLockRule(
    private val transactions: Transactions
) : CommitmentRule {
    override val order = CommitmentRuleOrder.FULFILLMENT

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (dryRun) return emptyList()
        val id = ApprovalRules.target(change, Fulfillment.CANCELS + Fulfillment.REGISTER) ?: return emptyList()
        transactions.locked(Reschedules.lock(id)) {}
        return emptyList()
    }
}

// D9: una entrega del compromiso con conformidad (conformity_given y conformity_by) y, vinculada a ella, la evidencia de su tipo
// de recurso, del mismo proyecto que el compromiso (P54)
@Component
class FulfillmentEvidenceRule(
    private val records: Records,
    private val metadata: MetadataService
) : CommitmentRule {
    override val order = CommitmentRuleOrder.FULFILLMENT + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ApprovalRules.target(change, setOf(Fulfillment.REGISTER)) ?: return emptyList()
        val before = change.before.orEmpty()
        val resourceType = Values.text(before[Fulfillment.RESOURCE_TYPE])
        if (resourceType == null) return listOfNotNull(Fulfillment.resourceTypeFinding(null))
        val deliveries =
            readAs(Commitments.DELIVERY) {
                records.all(
                    Commitments.DELIVERY,
                    filters = mapOf("commitment" to id.toString()),
                    max = CommitmentDeliveries.MAX_DELIVERIES,
                    field = Commitments.DELIVERY
                )
            }
        if (deliveries.isNotEmpty() && !Readability(metadata).canRead("${Commitments.DELIVERY}.$CONFORMITY_BY")) {
            return listOf(
                Finding(
                    "FIELD_NOT_READABLE",
                    "Registrar el cumplimiento exige leer quiénes dieron la conformidad de la entrega",
                    "${Commitments.DELIVERY}.$CONFORMITY_BY",
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
        val project = Records.uuid(before["project"])
        // solo una entrega con conformidad puede sostener el cumplimiento: las demás no leen sus evidencias. se leen de una en
        // una, con el candado del compromiso tomado, y la primera que basta corta la lectura
        val rows = mutableListOf<Fulfillment.DeliveryEvidence>()
        for (delivery in deliveries) {
            val row =
                Fulfillment.DeliveryEvidence(
                    Values.bool(delivery.attributes["conformity_given"]) == true,
                    Values.text(delivery.attributes[CONFORMITY_BY]),
                    emptySet()
                )
            if (!Fulfillment.conforms(row)) continue
            val evidence =
                readAs(CommitmentDeliveries.EVIDENCE) {
                    records.related(
                        Commitments.DELIVERY,
                        UUID.fromString(delivery.id),
                        Commitments.EVIDENCE_DELIVERY,
                        max = Fulfillment.MAX_EVIDENCE,
                        field = Commitments.EVIDENCE_DELIVERY
                    )
                }
            val counted = row.copy(evidenceTypes = Fulfillment.countedTypes(project, evidence.map { it.attributes }))
            rows += counted
            if (Fulfillment.missing(resourceType, counted).isEmpty()) break
        }
        return listOfNotNull(Fulfillment.finding(resourceType, rows))
    }

    companion object {
        const val CONFORMITY_BY = "conformity_by"
    }
}

// D10: ninguna dependencia bloqueante abierta (RF-COM-004). un compromiso del que depende que quien registra no lee no pasa en
// silencio como si estuviera resuelto: es NotReadable (P12)
@Component
class BlockingDependencyRule(
    private val records: Records
) : CommitmentRule {
    override val order = CommitmentRuleOrder.FULFILLMENT + 2

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ApprovalRules.target(change, setOf(Fulfillment.REGISTER)) ?: return emptyList()
        val blocking =
            readAs(Commitments.DEPENDENCY) {
                records.all(
                    Commitments.DEPENDENCY,
                    filters = mapOf("commitment" to id.toString()),
                    max = CommitmentRecordRules.MAX_GRAPH,
                    field = Commitments.DEPENDENCY
                )
            }.filter { Values.bool(it.attributes["blocking"]) == true }
                .mapNotNull { Records.uuid(it.attributes["depends_on"]) }
                .distinct()
        if (blocking.isEmpty()) return emptyList()
        val read = readAs(Commitments.COMMITMENT) { records.all(Commitments.COMMITMENT, ids = blocking) }
        if (read.size < blocking.size) throw NotReadable(Commitments.COMMITMENT)
        val open = read.count { it.state !in Fulfillment.RESOLVED }
        if (open == 0) return emptyList()
        return listOf(
            Finding(
                "BLOCKING_DEPENDENCY",
                "El compromiso depende de $open compromiso(s) todavía abiertos con una dependencia bloqueante (D10, RF-COM-004)",
                Commitments.DEPENDENCY
            )
        )
    }
}

// D11: verify exige la acción VERIFY sobre commitment y que quien verifica no sea el responsable (RN-COM-005). decide con
// permisos y datos, nunca con nombres de rol
@Component
class VerificationRule(
    private val callers: Callers,
    private val permissions: Permissions
) : CommitmentRule {
    override val order = CommitmentRuleOrder.FULFILLMENT + 3

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        ApprovalRules.target(change, setOf(Fulfillment.VERIFY)) ?: return emptyList()
        val user = callers.userOrNull()
        val findings = mutableListOf<Finding>()
        if (user == null || !permissions.has(user, VERIFY, Commitments.COMMITMENT)) {
            findings +=
                Finding(
                    "ACTION_REQUIRED",
                    "Verificar un compromiso exige la acción VERIFY sobre commitment (D11)",
                    ApprovalRules.RECORD,
                    status = HttpStatus.FORBIDDEN
                )
            if (!dryRun) return findings
        }
        if (user == null || Values.text(change.before.orEmpty()["owner_user_id"]) == user.userId.toString()) {
            findings +=
                Finding(
                    "SEGREGATION_OF_DUTIES",
                    "Quien verifica un compromiso no puede ser su responsable (D11, RN-COM-005)",
                    "owner_user_id",
                    status = HttpStatus.FORBIDDEN
                )
        }
        return findings
    }

    companion object {
        const val VERIFY = "VERIFY"
    }
}
