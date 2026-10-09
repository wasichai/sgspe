package sgspe.agreement

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// lo que exige llegar a active, puro (WF-I, RF-AGR-001 a RF-AGR-003)
object AgreementActivation {
    val LAND_TYPES = setOf("PERMISO DE USO DE TERRENO", "SERVIDUMBRE")
    const val ASSEMBLY_AUTHORIZATION = "AUTORIZACION DE ASAMBLEA"
    const val ASSEMBLY = "ASAMBLEA"
    val REQUIRED_DATES = listOf("signed_date", "effective_date")

    // los hallazgos con lo ya leído: los predios vinculados y el tipo de la interacción de asamblea
    fun findings(
        attributes: Map<String, Any?>,
        landParcels: Int,
        assemblyType: String?
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        REQUIRED_DATES.firstOrNull { Values.text(attributes[it]) == null }?.let {
            findings += Finding("FIELD_REQUIRED", "Falta $it: un acuerdo vigente tiene fecha de firma y de inicio", it)
        }
        val type = Values.text(attributes["agreement_type"])
        if (type in LAND_TYPES) {
            if (Values.text(attributes["community"]) == null) {
                findings += Finding("FIELD_REQUIRED", "Un permiso de uso de terreno o una servidumbre nombra a la comunidad titular (RF-AGR-002)", "community")
            }
            if (landParcels == 0) {
                findings += Finding("LAND_PARCEL_REQUIRED", "Vincule los predios afectados antes de registrar la vigencia (RF-AGR-002)", LAND_PARCELS)
            }
        }
        if (type == ASSEMBLY_AUTHORIZATION && assemblyType != ASSEMBLY) {
            findings +=
                Finding(
                    "ASSEMBLY_REQUIRED",
                    "Una autorización de asamblea apunta a la interacción de tipo ASAMBLEA que la otorgó (RF-AGR-003)",
                    "assembly_engagement"
                )
        }
        return findings
    }

    const val LAND_PARCELS = "agreement_land_parcel"
}

// AgreementWriteGuard (sgspe.agreement, WF-I): activate, activate_from_draft y resume, las transiciones que llegan a active,
// exigen las fechas, los predios y la comunidad de un permiso de uso de terreno o una servidumbre, y la asamblea de una
// autorización de asamblea. lee los predios vinculados y la interacción como quien aplica la transición: sin lectura,
// OBJECT_NOT_READABLE (P12)
@Component
@Order(GuardOrder.DOMAIN)
class AgreementWriteGuard(
    private val records: Records
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != AGREEMENT || change.kind != RecordChangeKind.TRANSITIONED || change.transition !in TO_ACTIVE) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        return try {
            val type = Values.text(before["agreement_type"])
            val parcels = if (type in AgreementActivation.LAND_TYPES) records.related(AGREEMENT, id, AgreementActivation.LAND_PARCELS).size else 0
            val assembly =
                Records.uuid(before["assembly_engagement"])?.let { engagementType(it) }
            AgreementActivation.findings(before, parcels, assembly)
        } catch (_: ForbiddenException) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "Registrar la vigencia exige leer los predios y la interacción de asamblea del acuerdo",
                    AgreementActivation.LAND_PARCELS,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun engagementType(id: UUID): String? = Values.text(records.find("engagement", id)?.attributes?.get("engagement_type"))

    companion object {
        const val AGREEMENT = "agreement"
        val TO_ACTIVE = setOf("activate", "activate_from_draft", "resume")
    }
}
