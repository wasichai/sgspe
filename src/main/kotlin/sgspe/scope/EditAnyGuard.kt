package sgspe.scope

import org.springframework.core.annotation.Order
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
import wasichai.core.data.RecordWrite

// la edición de lo propio sin own_records_only (security.md §5.5, RQ-SEC-008, ASM-069): en los objetos que declaran
// EDIT_ANY (model/model.json), a quien no tiene esa acción se le rechaza cambiar, transicionar, vincular o borrar un
// registro que creó otra persona (created_by). crear no es cambiar: agregar participantes u observaciones a lo ajeno
// pasa. la escritura con la marca de sgspe que solo cambia derivados (los conteos de AttendanceCountsListener, que
// escribe como quien agregó el participante) también pasa: no es una edición de la persona. la regla (la acción EDIT_ANY o
// created_by) es OwnRecords, la misma que pide la emisión manual de un documento (ruling P62); nunca compara nombres de rol
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
        val objectName = change.objectName
        // con userId la escritura es de una persona: si no se la puede identificar, OwnRecords la niega (falla cerrado)
        val user = callers.userOrNull()
        val refusal =
            OwnRecords.refusal(
                objectName,
                declaresEditAny = true,
                user = user,
                // la escritura de sgspe que solo cambia derivados cuenta como si tuviera EDIT_ANY: no es una edición de la persona
                holdsEditAny = { caller ->
                    permissions.has(caller, EDIT_ANY, objectName) ||
                        (!dryRun && WriteMark.present() && change.kind == RecordChangeKind.UPDATED && onlyDerived(change))
                },
                createdIt = { caller -> OwnRecords.createdBy(records, objectName, recordId, caller.userId) }
            )
        return listOfNotNull(refusal)
    }

    // una escritura de sgspe que no vincula y cuyos únicos cambios son derivados
    private suspend fun onlyDerived(change: RecordWrite): Boolean {
        if (change.isLink()) return false
        val derived = catalog.derivedFields(change.objectName)
        return view.changed(change).all { it in derived }
    }

    companion object {
        const val EDIT_ANY = OwnRecords.EDIT_ANY
    }
}
