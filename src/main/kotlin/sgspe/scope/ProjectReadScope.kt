package sgspe.scope

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.ProjectReach
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordReadScope
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.core.platform.WasichaiSchemas
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// el RecordReadScope del alcance por proyecto (WAS-ADR-0048, ADR-0029, ASM-106, security.md §9.7): wasichai lo pide para cada
// persona o cuenta de servicio que no es ADMIN (nunca para la plataforma ni las automatizaciones) y aplica la condición de
// ProjectReadCondition en listas, totales, detalle, relacionados, destinos de RELATION, historial, auditoría, transiciones,
// features GIS, archivos y agente; fuera de ella el registro no existe (404, o el 400 de una RELATION). los maestros del tenant
// no se acotan y no cuestan consultas; quien tiene READ_ALL_PROJECTS sobre project (ProjectReach) lee todo. se compone por
// AND con ConfidentialAttentionScope (§9.6). falla cerrado: un objeto sin clasificar, una definición sin sus columnas, o una
// consulta de permisos o de definiciones que falla, no admiten ningún registro, con una advertencia sin datos del registro
@Component
class ProjectReadScope internal constructor(
    private val reach: ProjectReach,
    private val catalog: ModelCatalog,
    private val schemas: WasichaiSchemas,
    private val clock: Clock,
    private val load: suspend (UUID, String) -> ObjectDefinition
) : RecordReadScope {
    @Autowired
    constructor(
        reach: ProjectReach,
        metadata: MetadataService,
        catalog: ModelCatalog,
        schemas: WasichaiSchemas,
        clock: Clock
    ) : this(reach, catalog, schemas, clock, { organization, name -> metadata.loadDefinition(organization, name) })

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun criterion(
        caller: AuthenticatedUser,
        definition: ObjectDefinition
    ): RecordCriterion? {
        val objectName = definition.obj.name
        val group = ProjectScope.readGroup(objectName)
        if (group == ReadGroup.TENANT) return null
        if (group == ReadGroup.UNCLASSIFIED) {
            log.warn("ProjectReadScope: {} no está clasificado en ProjectScope; no se admite ningún registro", objectName)
            return NOTHING
        }
        val columns =
            try {
                if (reach.readsAll(caller)) return null
                columns(caller.organizationId, objectName, group, definition)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("ProjectReadScope: no se resolvieron los permisos o las definiciones de {}; no se admite ningún registro", objectName, e)
                return NOTHING
            }
        if (!ProjectReadCondition.complete(group, columns)) {
            log.warn("ProjectReadScope: a {} o a sus tablas de alcance les falta una columna; no se admite ningún registro", objectName)
            return NOTHING
        }
        val today = LocalDate.now(clock.withZone(LIMA))
        return RecordCriterion { _, bind ->
            ProjectReadCondition.condition(group, columns, caller.organizationId, caller.userId, today, bind) ?: ProjectReadCondition.NOTHING
        }
    }

    // las tablas y columnas del grupo, de la definición del objeto leído, de la de project_assignment y, en un hijo, de la de su
    // padre (wasichai las tiene en caché); si una relación es opcional lo dice el modelo, nunca una lista aparte
    private suspend fun columns(
        organization: UUID,
        objectName: String,
        group: ReadGroup,
        definition: ObjectDefinition
    ): ReadColumns {
        val assignments = assignmentColumns(load(organization, ProjectScope.ASSIGNMENT))
        return when (group) {
            ReadGroup.PROJECT -> ReadColumns(ProjectReadCondition.ID_COLUMN, assignments)
            ReadGroup.ASSIGNMENT -> ReadColumns(column(definition, PROJECT_FIELD), assignments, member = column(definition, MEMBER))
            ReadGroup.ROOT ->
                ReadColumns(column(definition, PROJECT_FIELD), assignments, optional = !catalog.isRequiredRelation(objectName, PROJECT_FIELD))
            ReadGroup.CHILD -> {
                val (field, parentName) = ProjectScope.CHILDREN.getValue(objectName)
                val parent = load(organization, parentName)
                ReadColumns(
                    column(definition, field),
                    assignments,
                    optional = !catalog.isRequiredRelation(objectName, field),
                    parent = column(parent, PROJECT_FIELD)?.let { ParentColumns(schemas.dataTable(parent.obj.physicalTable), it) }
                )
            }
            else -> ReadColumns(null, assignments)
        }
    }

    private fun assignmentColumns(definition: ObjectDefinition): AssignmentColumns? =
        AssignmentColumns(
            schemas.dataTable(definition.obj.physicalTable),
            column(definition, PROJECT_FIELD) ?: return null,
            column(definition, MEMBER) ?: return null,
            column(definition, "active") ?: return null,
            column(definition, "start_date") ?: return null,
            column(definition, "end_date") ?: return null
        )

    private fun column(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    companion object {
        private const val PROJECT_FIELD = "project"
        private const val MEMBER = "member_user_id"
        private val NOTHING = RecordCriterion { _, _ -> ProjectReadCondition.NOTHING }
    }
}
