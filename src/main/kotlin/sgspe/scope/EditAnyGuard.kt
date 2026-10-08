package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// la edición de lo propio sin own_records_only (security.md §5.5, RQ-SEC-008, ASM-069): en los objetos que declaran
// EDIT_ANY (model/model.json), a quien no tiene esa acción se le rechaza cambiar, transicionar, vincular o borrar un
// registro que creó otra persona (created_by). crear no es cambiar: agregar participantes u observaciones a lo ajeno
// pasa. la escritura con la marca de sgspe que solo cambia derivados (los conteos de AttendanceCountsListener, que
// escribe como quien agregó el participante) también pasa: no es una edición de la persona. decide con
// Permissions.has(user, "EDIT_ANY", objeto) y created_by, nunca con nombres de rol
@Component
@Order(GuardOrder.EDIT_ANY)
class EditAnyGuard(
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val permissions: Permissions,
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind == RecordChangeKind.CREATED || change.userId == null) return emptyList()
        if (change.objectName !in catalog.objectsDeclaring(EDIT_ANY)) return emptyList()
        val recordId = change.recordId ?: return emptyList()
        // con userId la escritura es de una persona: si no se la puede identificar, se niega (falla cerrado)
        val user = callers.userOrNull()
        if (user != null) {
            if (permissions.has(user, EDIT_ANY, change.objectName)) return emptyList()
            if (!dryRun && WriteMark.present() && change.kind == RecordChangeKind.UPDATED && onlyDerived(change)) return emptyList()
            if (createdBy(change.objectName, recordId, user.userId)) return emptyList()
        }
        return listOf(
            Finding(
                code = "NOT_OWN_RECORD",
                message = "Solo puedes cambiar, mover, vincular o borrar los registros de ${change.objectName} que creaste: te falta EDIT_ANY",
                status = HttpStatus.FORBIDDEN
            )
        )
    }

    // una escritura de sgspe que no vincula y cuyos únicos cambios son derivados
    private suspend fun onlyDerived(change: RecordWrite): Boolean {
        if (change.isLink()) return false
        val derived = catalog.derivedFields(change.objectName)
        return view.changed(change).all { it in derived }
    }

    // created_by es una columna de sistema de wasichai (database-model.md §2.4): se compara en la base, con RecordService
    // como quien llama, sin leer la tabla física
    private suspend fun createdBy(
        objectName: String,
        recordId: UUID,
        userId: UUID
    ): Boolean = records.count(objectName, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(recordId)) > 0

    companion object {
        const val EDIT_ANY = "EDIT_ANY"
    }
}
