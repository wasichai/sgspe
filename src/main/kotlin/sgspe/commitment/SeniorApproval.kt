package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import java.util.UUID

// RN-ENG-002 (ASM-067): un compromiso nacido de una mesa de diálogo o pedido por un grupo de interés del grupo POLITICO
// requiere aprobación de nivel superior, si la política del proyecto está activa. la guarda (D5), los avisos y el derivado
// requires_senior_approval la evalúan en el momento con esta misma regla; la guarda nunca lee el derivado
object SeniorApproval {
    const val DIALOGUE = "MESA DE DIALOGO"
    const val POLITICAL = "POLITICO"

    fun required(
        policy: Boolean,
        originType: String?,
        stakeholderGroups: Collection<String?>
    ): Boolean = policy && (originType == DIALOGUE || POLITICAL in stakeholderGroups)
}

// lee lo que la regla necesita: la política del proyecto, el tipo de la interacción de origen y el grupo de influencia de los
// solicitantes (commitment_stakeholder), como quien llama o como la plataforma dentro de un trabajo. falla cerrada: un proyecto
// o un origen que no se leen (sin READ o fuera del alcance) son NotReadable, nunca un «no exige nivel superior»
@Component
class SeniorApprovalReader(
    private val records: Records
) {
    suspend fun required(
        commitmentId: UUID,
        attributes: Map<String, Any?>
    ): Boolean {
        val project = Records.uuid(attributes["project"])?.let { find("project", it) }.orEmpty()
        val policy = ProjectParameters.policy(project, "policy_senior_approval_for_dialogue")
        if (!policy) return false
        val type = Records.uuid(attributes["origin_engagement"])?.let { find("engagement", it) }?.get("engagement_type")
        if (Values.text(type) == SeniorApproval.DIALOGUE) return true
        val groups =
            readAs("stakeholder") { records.related(Commitments.COMMITMENT, commitmentId, Commitments.STAKEHOLDERS) }
                .map { Values.text(it.attributes["influence_group"]) }
        return SeniorApproval.required(true, Values.text(type), groups)
    }

    // los atributos de un registro referido; null (fuera del alcance) es NotReadable
    private suspend fun find(
        objectName: String,
        id: UUID
    ): Map<String, Any?> = readAs(objectName) { records.find(objectName, id) }?.attributes ?: throw NotReadable(objectName)
}
