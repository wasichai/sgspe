package sgspe.common

import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// condiciones de lista que los filtros de igualdad de wasichai no expresan (RecordCriterion, WAS-ADR-0025): rangos,
// conjuntos y el estado de workflow. los valores van enlazados; las columnas salen de la metadata del objeto, salvo las
// de sistema (updated_at, workflow_state, database-model.md §2.4), que wasichai nombra siempre igual
object Criteria {
    fun updatedSince(instant: Instant): RecordCriterion =
        RecordCriterion { _, bind -> "updated_at >= ${bind(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))}" }

    // workflow_state en un conjunto: la columna que agrega wasichai-workflow al asociar el workflow (WAS-ADR-0013)
    // sobre un objeto sin workflow no hay columna: es un 400 que lo dice y no un error de SQL
    fun inState(states: Collection<String>): RecordCriterion =
        RecordCriterion { definition, bind ->
            if (definition.obj.name !in WORKFLOW_OBJECTS) {
                throw ValidationException("Estado inválido", "state", "${definition.obj.name} no tiene workflow")
            }
            "workflow_state = ANY(${bind(states.toTypedArray())})"
        }

    // workflow_state en un conjunto, o fuera de él con `field` todavía lleno: lo que un trabajo debe calcular más lo que
    // debe vaciar (JOB-01, deadline_status y validity_status), sin releer cada hora los registros cerrados que ya vació
    fun inStateOrFilled(
        states: Collection<String>,
        field: String
    ): RecordCriterion {
        val state = inState(states)
        return RecordCriterion { definition, bind -> "(${state.condition(definition, bind)} OR ${column(definition, field)} IS NOT NULL)" }
    }

    fun anyOf(
        field: String,
        ids: Collection<UUID>
    ): RecordCriterion = RecordCriterion { definition, bind -> "${column(definition, field)} = ANY(${bind(ids.toTypedArray())})" }

    fun textIn(
        field: String,
        values: Collection<String>
    ): RecordCriterion = RecordCriterion { definition, bind -> "${column(definition, field)} = ANY(${bind(values.toTypedArray())})" }

    // un DATETIME entre dos instantes, el segundo excluido
    fun between(
        field: String,
        from: Instant,
        until: Instant
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val column = column(definition, field)
            "$column >= ${bind(OffsetDateTime.ofInstant(from, ZoneOffset.UTC))} AND $column < ${bind(OffsetDateTime.ofInstant(until, ZoneOffset.UTC))}"
        }

    // una fecha entre dos días, ambos incluidos (gancho de los filtros por periodo de F3)
    fun dateBetween(
        field: String,
        from: LocalDate,
        to: LocalDate
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val column = column(definition, field)
            "$column >= ${bind(from)} AND $column <= ${bind(to)}"
        }

    // el campo debe existir y ser legible por quien llama: wasichai entrega a los criterios la definición ya filtrada, así que
    // no se puede filtrar por una columna que no se lee (sería un oráculo sobre su valor)
    private fun column(
        definition: ObjectDefinition,
        field: String
    ): String {
        val found =
            definition.fields.firstOrNull { it.name == field }
                ?: throw ValidationException("Filtro inválido", field, "no es un campo de ${definition.obj.name} o no puede leerlo")
        return SqlIdentifier.quote(found.columnName)
    }

    // los objetos con workflow salen del modelo empaquetado, una sola vez
    private val WORKFLOW_OBJECTS: Set<String> by lazy { ModelCatalog.fromClasspath().workflowObjects }
}
