package sgspe.common

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
import wasichai.core.platform.SqlIdentifier
import wasichai.core.platform.WasichaiSchemas

// un registro del que otros dependen no se borra (P13): wasichai declara cada relación obligatoria NOT NULL con ON DELETE
// SET NULL, así que borrar el padre de un hijo obligatorio falla en la base con un 500. aquí se vuelve un 409 que nombra
// el primer dependiente. rige para todos, ADMIN incluido, porque la base lo rechazaría igual.
// la comprobación es de la base y no de quien borra: con Records solo vería lo que sus permisos le dejan leer (y
// RecordService.asPlatform no se puede usar dentro de una petición). por eso es un SELECT 1 … LIMIT 1 por relación sobre la
// tabla del tenant (organization_id de la escritura), con la tabla y la columna que dice la metadata del objeto, los
// identificadores entrecomillados y los valores enlazados, como hace Criteria
@Component
@Order(GuardOrder.DEPENDENTS)
class DependentsDeleteGuard(
    private val catalog: ModelCatalog,
    private val metadata: MetadataService,
    private val schemas: WasichaiSchemas,
    private val db: DatabaseClient
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = change.recordId
        if (change.kind != RecordChangeKind.DELETED || id == null) return emptyList()
        val first =
            catalog.requiredDependents(change.objectName).firstOrNull { (source, field) ->
                referenced(change, source, field)
            } ?: return emptyList()
        return listOf(
            Finding(
                code = "HAS_DEPENDENTS",
                message = "No se puede borrar ${change.objectName}: ${first.first} todavía depende de él por ${first.second}",
                field = "${first.first}.${first.second}",
                status = HttpStatus.CONFLICT
            )
        )
    }

    // si alguna fila del tenant de `source` apunta, por la columna de `field`, al registro que se borra
    private suspend fun referenced(
        change: RecordWrite,
        source: String,
        field: String
    ): Boolean {
        val definition =
            try {
                metadata.loadDefinition(change.organizationId, source)
            } catch (_: NotFoundException) {
                return false
            }
        val column = definition.fields.firstOrNull { it.name == field }?.columnName ?: return false
        val table = schemas.dataTable(definition.obj.physicalTable)
        return db
            .sql("SELECT 1 AS one FROM $table WHERE organization_id = :organizationId AND ${SqlIdentifier.quote(column)} = :id LIMIT 1")
            .bind("organizationId", change.organizationId)
            .bind("id", change.recordId!!)
            .fetch()
            .first()
            .awaitSingleOrNull() != null
    }
}
