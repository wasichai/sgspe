package sgspe.scope

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.ObjectDefinitions
import sgspe.common.ProjectReach
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordReadScope
import wasichai.core.identity.AuthenticatedUser
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
// las definiciones de la condición salen del memo de la petición sobre la caché de wasichai (ObjectDefinitions.current,
// security.md §9.7)
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
        definitions: ObjectDefinitions,
        catalog: ModelCatalog,
        schemas: WasichaiSchemas,
        clock: Clock
    ) : this(reach, catalog, schemas, clock, { organization, name -> definitions.current(organization, name) })

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

    // las tablas y columnas del grupo, de la definición del objeto leído (la de wasichai, de esta lectura), de la de
    // project_assignment y, en un hijo, de las de cada salto de la cadena de cada padre posible. estas salen de
    // ObjectDefinitions.current (la de la petición; wasichai la guarda hasta wasichai.metadata.cache.ttl y, en otro nodo,
    // puede llegar con ese atraso): una vieja tras un apply.py solo deja la condición incompleta o acotada por menos
    // padres, nunca más registros. si una relación es opcional lo dice el modelo, nunca una lista aparte
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
                val parents = ProjectScope.CHILDREN.getValue(objectName)
                val several = parents.size > 1
                val paths =
                    parents.mapNotNull { (field, _) ->
                        val column = column(definition, field)
                        // con varios padres, uno cuya columna el objeto no tiene es una relación que el tenant todavía no tiene (un
                        // tenant de F4 no tiene delivery.social_contribution): ningún registro lo trae y se acota por los demás.
                        // con un único padre, la columna faltante deja la condición incompleta (falla cerrado)
                        if (column == null && several) null else ParentPath(column, hops(organization, objectName, field))
                    }
                ReadColumns(
                    null,
                    assignments,
                    optional = !several && !catalog.isRequiredRelation(objectName, parents.single().first),
                    parents = paths
                )
            }
            else -> ReadColumns(null, assignments)
        }
    }

    // la tabla de cada salto de la cadena de `child` por `field` y su columna hacia el salto siguiente (en la raíz, `project`);
    // una cadena que no llega a una raíz da una lista vacía y la condición queda incompleta
    private suspend fun hops(
        organization: UUID,
        child: String,
        field: String
    ): List<Hop> {
        val chain = ProjectScope.chain(child, field)
        return chain.mapIndexed { index, (_, parent) ->
            val definition = load(organization, parent)
            val next = if (index == chain.lastIndex) PROJECT_FIELD else chain[index + 1].first
            Hop(schemas.dataTable(definition.obj.physicalTable), column(definition, next))
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
