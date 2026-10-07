package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ModelCatalog
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// role_name de project_assignment nombra un rol declarado de model/roles.json, cargado como configuración (ASM-070): de
// él dependen los destinatarios por rol. ni ADMIN ni los dos mapeados a ADMIN son roles de proyecto
@Component
@Order(GuardOrder.ASSIGNMENT_ROLE)
class AssignmentRoleGuard(
    private val catalog: ModelCatalog,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Assignments.OBJECT) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val role = Values.text(view.after(change)["role_name"]) ?: return emptyList()
        if (role in catalog.declaredRoles) return emptyList()
        return listOf(
            Finding(
                code = "UNKNOWN_ROLE",
                message = "role_name debe ser uno de los roles declarados: ${catalog.declaredRoles.sorted().joinToString(", ")}",
                field = "role_name",
                status = HttpStatus.BAD_REQUEST
            )
        )
    }
}
