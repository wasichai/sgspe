package sgspe.scope

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Records
import wasichai.core.data.RecordCriterion
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// la edición de lo propio (security.md §5.5, RQ-SEC-008, ASM-069), compartida por EditAnyGuard y por la emisión manual de un
// documento (IssueAccessByScope, ruling P62): en un objeto que declara EDIT_ANY, quien no tiene esa acción solo toca los
// registros que creó (created_by). sin quien llama se niega (falla cerrado). decide con la acción y created_by, nunca con
// nombres de rol
object OwnRecords {
    const val EDIT_ANY = "EDIT_ANY"

    // pura: null si pasa. `holdsEditAny` y `createdIt` se preguntan solo si hacen falta, en ese orden
    suspend fun refusal(
        objectName: String,
        declaresEditAny: Boolean,
        user: AuthenticatedUser?,
        holdsEditAny: suspend (AuthenticatedUser) -> Boolean,
        createdIt: suspend (AuthenticatedUser) -> Boolean
    ): Finding? {
        if (!declaresEditAny) return null
        if (user != null && (holdsEditAny(user) || createdIt(user))) return null
        return Finding(
            code = "NOT_OWN_RECORD",
            message = "Solo puedes cambiar, mover, vincular, borrar o emitir documentos de los registros de $objectName que creaste: te falta EDIT_ANY",
            status = HttpStatus.FORBIDDEN
        )
    }

    // created_by es una columna de sistema de wasichai (database-model.md §2.4): se compara en la base, con RecordService
    // como quien llama, sin leer la tabla física
    suspend fun createdBy(
        records: Records,
        objectName: String,
        recordId: UUID,
        userId: UUID
    ): Boolean = records.count(objectName, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(recordId)) > 0
}
