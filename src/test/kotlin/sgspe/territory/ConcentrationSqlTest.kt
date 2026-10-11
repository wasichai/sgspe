package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// la concentración por localidad, nunca por punto (caso 4 y 6 de gis.md §5.3, consecuencia 4 de la especificación de F6): cada
// evento cuenta en su localidad o, sin ella, en su comunidad; los que no tienen ninguna no cuentan; el periodo en instantes
// de Lima y el proyecto solo si se pidió
class ConcentrationSqlTest {
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "<$objectName>"

            override fun column(
                objectName: String,
                field: String
            ) = "\"$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 4326
        }

    private fun render(projects: List<UUID>): Pair<String, List<Any>> {
        val values = mutableListOf<Any>()
        val period = Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        val sql =
            TerritoryAggregates.concentrationSql(tables, { value ->
                values += value
                ":g${values.size - 1}"
            }, "grievance", "received_at", projects, period, 100)
        return sql to values
    }

    @Test
    fun `agrupa por la localidad o la comunidad del evento y deja fuera los que no tienen territorio`() {
        val (sql, values) = render(emptyList())
        assertTrue(sql.contains("CASE WHEN e.\"locality\" IS NOT NULL THEN 'locality' ELSE 'community' END AS kind"), sql)
        assertTrue(sql.contains("AND (e.\"locality\" IS NOT NULL OR e.\"community\" IS NOT NULL) GROUP BY 1, 2"), sql)
        assertFalse(sql.contains("e.\"project\""), sql)
        assertEquals(OffsetDateTime.parse("2026-10-01T05:00Z"), values[0])
        assertEquals(OffsetDateTime.parse("2026-11-01T05:00Z"), values[1])
        assertEquals(101, values.last())
    }

    @Test
    fun `con proyectos acota por ellos`() {
        val project = UUID.randomUUID()
        val (sql, values) = render(listOf(project))
        assertTrue(sql.contains("AND e.\"project\" = ANY(:g2)"), sql)
        assertEquals(listOf(project), (values[2] as Array<*>).toList())
    }
}
