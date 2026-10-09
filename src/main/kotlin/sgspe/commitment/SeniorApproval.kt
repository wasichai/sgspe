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

    // por qué se exige: el origen (una mesa de diálogo) o un solicitante POLITICO. el origen se informa primero
    enum class Cause { ORIGIN, STAKEHOLDER }

    fun cause(
        policy: Boolean,
        originType: String?,
        stakeholderGroups: Collection<String?>
    ): Cause? =
        when {
            !policy -> null
            originType == DIALOGUE -> Cause.ORIGIN
            POLITICAL in stakeholderGroups -> Cause.STAKEHOLDER
            else -> null
        }

    fun required(
        policy: Boolean,
        originType: String?,
        stakeholderGroups: Collection<String?>
    ): Boolean = cause(policy, originType, stakeholderGroups) != null
}

// lee lo que la regla necesita: la política del proyecto, el tipo de la interacción de origen y el grupo de influencia de los
// solicitantes (commitment_stakeholder), como quien llama o como la plataforma dentro de un trabajo. falla cerrada: un proyecto
// o un origen que no existen o no se leen (sin READ o fuera del alcance) son NotReadable, nunca un «no exige nivel superior»
@Component
class SeniorApprovalReader(
    private val records: Records
) {
    suspend fun required(
        commitmentId: UUID,
        attributes: Map<String, Any?>
    ): Boolean = cause(commitmentId, attributes) != null

    // la causa, o null si no se exige; los solicitantes solo se leen si el origen no decide
    suspend fun cause(
        commitmentId: UUID,
        attributes: Map<String, Any?>
    ): SeniorApproval.Cause? {
        val policy = ProjectParameters.policy(records.projectOf(attributes), "policy_senior_approval_for_dialogue")
        if (!policy) return null
        val type =
            Values.text(
                Records
                    .uuid(attributes["origin_engagement"])
                    ?.let { records.readable("engagement", it) }
                    ?.attributes
                    ?.get("engagement_type")
            )
        if (type == SeniorApproval.DIALOGUE) return SeniorApproval.Cause.ORIGIN
        val groups = records.stakeholdersOf(commitmentId).map { Values.text(it.attributes["influence_group"]) }
        return SeniorApproval.cause(true, type, groups)
    }
}
