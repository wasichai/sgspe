package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQueryContributor

// el estado del workflow como filtro de la lista genérica (D10 de la revisión de UX): wasichai filtra solo por igualdad de un
// campo y workflow_state no es un campo, así que una vista como «Abiertos» no se paginaba en el servidor. ?state=a,b deja los
// registros en esos estados y ?state_not=a,b los que no están en ellos (también los que no tienen estado). los estados se separan
// por comas, porque la lista genérica lee un solo valor por parámetro. se validan contra model/workflows: un objeto sin workflow
// no tiene la columna y un estado que el workflow no declara es un error de quien llama, no una lista vacía. los valores van
// ligados; el alcance de lectura de quien llama se aplica igual (ProjectReadScope)
@Component
class StateQuery(
    private val catalog: ModelCatalog
) : RecordQueryContributor {
    override val parameters: Set<String> = setOf(STATE, STATE_NOT)

    override fun parse(params: Map<String, String>): RecordCriterion? {
        val inside = states(params[STATE])
        val outside = states(params[STATE_NOT])
        if (inside == null && outside == null) return null
        return RecordCriterion { definition, bind ->
            val objectName = definition.obj.name
            val known = catalog.states(objectName).toSet()
            if (objectName !in catalog.workflowObjects) {
                throw ValidationException("Estado inválido", if (inside != null) STATE else STATE_NOT, "$objectName no tiene workflow")
            }
            for ((parameter, values) in listOf(STATE to inside, STATE_NOT to outside)) {
                values?.firstOrNull { it !in known }?.let {
                    throw ValidationException("Estado inválido", parameter, "$it no es un estado de $objectName")
                }
            }
            // workflow_state es la columna de sistema de wasichai-workflow (WAS-ADR-0013), como en Criteria.inState
            listOfNotNull(
                inside?.let { "workflow_state = ANY(${bind(it.toTypedArray())})" },
                outside?.let { "(workflow_state IS NULL OR workflow_state <> ALL(${bind(it.toTypedArray())}))" }
            ).joinToString(" AND ")
        }
    }

    // null si no vino o solo trae vacíos
    private fun states(raw: String?): List<String>? =
        raw
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.ifEmpty { null }

    companion object {
        const val STATE = "state"
        const val STATE_NOT = "state_not"
    }
}
