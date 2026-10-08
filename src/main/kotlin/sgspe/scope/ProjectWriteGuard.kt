package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las reglas de WF-A (workflows.md, RF-PRJ-003): llegar a `active` (approve, y resume desde suspended) exige el CRS de
// trabajo y al menos una project_party CLIENTE u OPERADOR, salvo que policy_activation_preconditions esté en falso
// (ASM-062); approve exige además la acción declarada APPROVE sobre project, la doble llave de RF-WFL-002
@Component
@Order(GuardOrder.DOMAIN)
class ProjectWriteGuard(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != "project" || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition !in TO_ACTIVE) return emptyList()
        val findings = mutableListOf<Finding>()
        if (transition == APPROVE_TRANSITION && !holdsApprove(change)) {
            findings += Finding("ACTION_REQUIRED", "Aprobar un proyecto exige la acción APPROVE sobre project", status = HttpStatus.FORBIDDEN)
        }
        val project = change.before.orEmpty()
        if (ProjectParameters.policy(project, "policy_activation_preconditions")) {
            if (Values.text(project["working_crs"]) == null) {
                findings += Finding("WORKING_CRS_REQUIRED", "El proyecto necesita su CRS de trabajo (working_crs) para pasar a activo", "working_crs")
            }
            partyFinding(change)?.let { findings += it }
        }
        return findings
    }

    // sin userId (un trabajo, una automatización) no hay doble llave que comprobar; con userId la escritura es de una
    // persona y, si no se la puede identificar, se niega (falla cerrado, como ProjectScopeGuard)
    private suspend fun holdsApprove(change: RecordWrite): Boolean {
        if (change.userId == null) return true
        val user = callers.userOrNull() ?: return false
        return permissions.has(user, APPROVE, "project")
    }

    // las partes se leen como quien llama, en seco y al escribir por igual. quien no puede leerlas (un rol sin READ sobre
    // project_party) no puede verificar la precondición: es un bloqueo 403 que lo dice, no un falso verde ni un 403 mudo
    private suspend fun partyFinding(change: RecordWrite): Finding? {
        val project = change.recordId
        val parties =
            try {
                if (project == null) emptyList() else records.all("project_party", filters = mapOf("project" to project.toString()))
            } catch (_: ForbiddenException) {
                return Finding(
                    "OBJECT_NOT_READABLE",
                    "No tiene permiso para leer project_party, así que no se puede verificar la parte CLIENTE u OPERADOR",
                    "project_party",
                    status = HttpStatus.FORBIDDEN
                )
            }
        if (parties.any { Values.text(it.attributes["party_role"]) in ACTIVATING_ROLES }) return null
        return Finding("PARTY_REQUIRED", "El proyecto necesita una parte CLIENTE u OPERADOR para pasar a activo", "project_party")
    }

    companion object {
        const val APPROVE = "APPROVE"
        const val APPROVE_TRANSITION = "approve"
        val TO_ACTIVE = setOf(APPROVE_TRANSITION, "resume")
        val ACTIVATING_ROLES = setOf("CLIENTE", "OPERADOR")
    }
}
