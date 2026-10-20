package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// ?range_field=&range_from=&range_to= y ?empty= sobre la lista genérica (D10 de la revisión de UX): el rango es [desde, hasta)
// sobre el primer campo con valor; un campo vacío es IS NULL. los valores van ligados y los campos salen de la definición que
// quien llama lee
class RangeQueryTest {
    private val range = RangeQuery()
    private val empty = EmptyQuery()

    private fun field(
        name: String,
        type: FieldType
    ) = CustomField(UUID.randomUUID(), UUID.randomUUID(), name, name, type, "c_$name", false, false, null, null, 0, null, null, emptyMap(), true, true)

    private val engagement =
        ObjectDefinition(
            CustomObject(UUID.randomUUID(), UUID.randomUUID(), "engagement", "engagement", "engagement", null, true, "engagement_tbl", null, null),
            listOf(
                field("held_at", FieldType.DATETIME),
                field("scheduled_at", FieldType.DATETIME),
                field("due_date", FieldType.DATE),
                field("title", FieldType.TEXT)
            )
        )

    private fun condition(criterion: wasichai.core.data.RecordCriterion): Pair<String, List<Any>> {
        val bound = mutableListOf<Any>()
        val sql =
            criterion.condition(engagement) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        return sql to bound
    }

    @Test
    fun `sin range_field ni empty no agrega nada`() {
        assertNull(range.parse(mapOf("project" to "p1")))
        assertNull(empty.parse(mapOf("empty" to " , ")))
        assertEquals(setOf("range_field", "range_from", "range_to"), range.parameters)
        assertEquals(setOf("empty"), empty.parameters)
    }

    @Test
    fun `un rango de fechas es desde incluido y hasta excluido`() {
        val (sql, bound) = condition(range.parse(mapOf("range_field" to "due_date", "range_from" to "2026-10-01", "range_to" to "2026-11-01"))!!)
        assertEquals("\"c_due_date\" >= :c0 AND \"c_due_date\" < :c1", sql)
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), bound)
    }

    @Test
    fun `sobre varios campos cuenta el primero con valor, y un dia en un DATETIME es su comienzo en Lima`() {
        val (sql, bound) = condition(range.parse(mapOf("range_field" to "held_at,scheduled_at", "range_from" to "2026-10-01"))!!)
        assertEquals("COALESCE(\"c_held_at\", \"c_scheduled_at\") >= :c0", sql)
        assertEquals(listOf(OffsetDateTime.of(2026, 10, 1, 5, 0, 0, 0, ZoneOffset.UTC)), bound)
        val (_, instante) = condition(range.parse(mapOf("range_field" to "held_at", "range_to" to "2026-10-01T10:00:00-05:00"))!!)
        assertEquals(listOf(OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC)), instante)
    }

    @Test
    fun `un rango sin campo, sin limites, al reves o sobre un campo que no es fecha es 400`() {
        assertEquals("range_field", assertThrows<ValidationException> { range.parse(mapOf("range_from" to "2026-10-01")) }.violations.single().field)
        assertEquals("range_from", assertThrows<ValidationException> { range.parse(mapOf("range_field" to "due_date")) }.violations.single().field)
        val reves = range.parse(mapOf("range_field" to "due_date", "range_from" to "2026-11-01", "range_to" to "2026-10-01"))!!
        assertEquals("range_from", assertThrows<ValidationException> { condition(reves) }.violations.single().field)
        val texto = range.parse(mapOf("range_field" to "title", "range_from" to "2026-10-01"))!!
        assertEquals("range_field", assertThrows<ValidationException> { condition(texto) }.violations.single().field)
        val mezcla = range.parse(mapOf("range_field" to "due_date,held_at", "range_from" to "2026-10-01"))!!
        assertEquals("range_field", assertThrows<ValidationException> { condition(mezcla) }.violations.single().field)
        val ilegible = range.parse(mapOf("range_field" to "secreto", "range_from" to "2026-10-01"))!!
        assertEquals("range_field", assertThrows<ValidationException> { condition(ilegible) }.violations.single().field)
        val mala = range.parse(mapOf("range_field" to "due_date", "range_from" to "1 de octubre"))!!
        assertEquals("range_from", assertThrows<ValidationException> { condition(mala) }.violations.single().field)
    }

    @Test
    fun `empty deja los registros con esos campos sin valor`() {
        val (sql, bound) = condition(empty.parse(mapOf("empty" to "held_at, scheduled_at"))!!)
        assertEquals("\"c_held_at\" IS NULL AND \"c_scheduled_at\" IS NULL", sql)
        assertEquals(emptyList<Any>(), bound)
        val ilegible = empty.parse(mapOf("empty" to "secreto"))!!
        assertEquals("empty", assertThrows<ValidationException> { condition(ilegible) }.violations.single().field)
    }
}
