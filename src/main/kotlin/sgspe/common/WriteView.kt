package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AccessPolicy
import wasichai.core.identity.CurrentUser
import wasichai.core.identity.FieldAccess
import wasichai.core.metadata.MetadataService

// cómo queda un registro después de una escritura que una guarda está por dejar pasar. el PUT de wasichai reemplaza el
// registro: cada campo que el escritor puede escribir toma lo que manda, y uno que falta queda vacío; los que sus roles
// bloquean conservan lo guardado (RecordService y PhysicalTableRecordStore de wasichai 0.3.2). un alta es lo que manda;
// una transición, un vínculo o una baja no cambian ningún campo
@Component
class WriteView(
    private val metadata: MetadataService,
    private val access: AccessPolicy,
    private val currentUser: CurrentUser
) {
    suspend fun after(change: RecordWrite): Map<String, Any?> {
        val before = change.before.orEmpty()
        return when {
            change.kind == RecordChangeKind.CREATED -> change.attributes.orEmpty()
            change.kind != RecordChangeKind.UPDATED || change.isLink() -> before
            else -> {
                val definition = metadata.loadDefinition(change.organizationId, change.objectName)
                val fieldAccess = fieldAccess(change)
                val attributes = change.attributes.orEmpty()
                definition.fields
                    .filter { it.type.name != "GEOMETRY" }
                    .associate { field ->
                        val writes = field.editable && fieldAccess.canWrite(field.id)
                        field.name to if (writes) attributes[field.name] else before[field.name]
                    }
            }
        }
    }

    // los campos cuyo valor cambia con la escritura. en un alta, los que llegan con valor
    suspend fun changed(
        change: RecordWrite,
        after: Map<String, Any?>? = null
    ): Set<String> {
        val after = after ?: after(change)
        if (change.kind == RecordChangeKind.CREATED) return after.filterValues { Values.text(it) != null }.keys
        val before = change.before.orEmpty()
        return (after.keys + before.keys).filter { !Values.same(after[it], before[it]) }.toSet()
    }

    private suspend fun fieldAccess(change: RecordWrite): FieldAccess {
        if (change.userId == null) return FieldAccess.FULL
        return access.fieldAccess(currentUser.require(), change.objectId)
    }
}
