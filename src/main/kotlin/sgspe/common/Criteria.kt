package sgspe.common

import wasichai.core.data.RecordCriterion
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
    fun inState(states: Collection<String>): RecordCriterion = RecordCriterion { _, bind -> "workflow_state = ANY(${bind(states.toTypedArray())})" }

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

    private fun column(
        definition: wasichai.core.metadata.ObjectDefinition,
        field: String
    ): String = SqlIdentifier.quote(definition.fields.first { it.name == field }.columnName)
}
