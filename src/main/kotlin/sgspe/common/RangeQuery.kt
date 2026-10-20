package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQueryContributor
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

// un rango de fechas como filtro de la lista genérica (D10 de la revisión de UX): wasichai filtra solo por igualdad, así que el
// calendario de interacciones leía todo el alcance con tope. ?range_field=a,b&range_from=&range_to= deja los registros cuyo
// primer campo con valor (COALESCE, en ese orden: held_at antes que scheduled_at) cae en [range_from, range_to); basta uno de los
// dos límites. los campos son todos DATE o todos DATETIME y deben estar en la definición que quien llama lee (no se filtra por lo
// que no se lee: sería un oráculo). un límite es AAAA-MM-DD o, en un DATETIME, un instante ISO-8601 con zona; un día en un
// DATETIME es su comienzo en Lima. los valores van ligados
@Component
class RangeQuery : RecordQueryContributor {
    override val parameters: Set<String> = setOf(FIELD, FROM, TO)

    override fun parse(params: Map<String, String>): RecordCriterion? {
        val names = names(params[FIELD])
        val from = params[FROM]?.trim()?.ifEmpty { null }
        val to = params[TO]?.trim()?.ifEmpty { null }
        if (names == null) {
            if (from != null || to != null) throw ValidationException("Rango inválido", FIELD, "range_from y range_to piden range_field")
            return null
        }
        if (from == null && to == null) throw ValidationException("Rango inválido", FROM, "range_field pide range_from o range_to")
        return RecordCriterion { definition, bind ->
            val fields = names.map { readable(definition, it, FIELD) }
            val type = fields.map { it.type }.toSet().singleOrNull()
            if (type != FieldType.DATE && type != FieldType.DATETIME) {
                throw ValidationException("Rango inválido", FIELD, "los campos deben ser todos DATE o todos DATETIME")
            }
            val start = from?.let { bound(it, type, FROM) }
            val end = to?.let { bound(it, type, TO) }
            if (start != null && end != null && start > end) throw ValidationException("Rango inválido", FROM, "no puede ser posterior a range_to")
            val value = fields.map { SqlIdentifier.quote(it.columnName) }.let { if (it.size == 1) it.single() else "COALESCE(${it.joinToString(", ")})" }
            listOfNotNull(start?.let { "$value >= ${bind(it.value)}" }, end?.let { "$value < ${bind(it.value)}" }).joinToString(" AND ")
        }
    }

    // un límite ya leído: el valor que se liga y su instante para compararlo con el otro
    private data class Bound(
        val value: Any,
        val instant: Long
    ) : Comparable<Bound> {
        override fun compareTo(other: Bound): Int = instant.compareTo(other.instant)
    }

    private fun bound(
        raw: String,
        type: FieldType,
        parameter: String
    ): Bound =
        try {
            if (type == FieldType.DATE) {
                LocalDate.parse(raw).let { Bound(it, it.toEpochDay() * DAY) }
            } else {
                val instant = if (raw.length == DAY_LENGTH) LocalDate.parse(raw).atStartOfDay(LIMA).toOffsetDateTime() else OffsetDateTime.parse(raw)
                instant.withOffsetSameInstant(ZoneOffset.UTC).let { Bound(it, it.toEpochSecond()) }
            }
        } catch (_: DateTimeParseException) {
            throw ValidationException(
                "Rango inválido",
                parameter,
                if (type ==
                    FieldType.DATE
                ) {
                    "debe ser una fecha AAAA-MM-DD"
                } else {
                    "debe ser una fecha AAAA-MM-DD o un instante ISO-8601 con zona"
                }
            )
        }

    companion object {
        const val FIELD = "range_field"
        const val FROM = "range_from"
        const val TO = "range_to"
        private const val DAY = 86_400L
        private const val DAY_LENGTH = 10
    }
}

// los campos vacíos como filtro de la lista genérica (D10 de la revisión de UX): ?empty=a,b deja los registros sin valor en todos
// esos campos (las tareas para tomar, sin assignee_user_id; lo que no tiene fecha). los campos deben estar en la definición que
// quien llama lee
@Component
class EmptyQuery : RecordQueryContributor {
    override val parameters: Set<String> = setOf(EMPTY)

    override fun parse(params: Map<String, String>): RecordCriterion? {
        val names = names(params[EMPTY]) ?: return null
        return RecordCriterion { definition, _ ->
            names.joinToString(" AND ") { "${SqlIdentifier.quote(readable(definition, it, EMPTY).columnName)} IS NULL" }
        }
    }

    companion object {
        const val EMPTY = "empty"
    }
}

// los nombres de una lista separada por comas; null si no vino o solo trae vacíos
private fun names(raw: String?): List<String>? =
    raw
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.ifEmpty { null }

private fun readable(
    definition: ObjectDefinition,
    name: String,
    parameter: String
): CustomField =
    definition.fields.firstOrNull { it.name == name }
        ?: throw ValidationException("Filtro inválido", parameter, "$name no es un campo de ${definition.obj.name} o no puede leerlo")
