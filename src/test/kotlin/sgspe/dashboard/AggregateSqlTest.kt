package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.Attention
import sgspe.territory.GateTables
import sgspe.territory.SpatialGate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// el SQL de los conteos del tablero executive (plan 8.2a-bis) sin base: cada objeto como la tabla de la compuerta, los
// proyectos y el territorio en el WHERE, un campo ilegible como NULL (nunca declarado), la regla de conteo de las QCS en la
// base, los límites de Lima enlazados y solo SELECT o WITH
class AggregateSqlTest {
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
            ) = error("sin geometrías")
        }

    private class Bound {
        val values = mutableListOf<Any>()

        fun bind(value: Any): String {
            values += value
            return ":g${values.size - 1}"
        }
    }

    private val p1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val p2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val locality = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val community = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    private fun <T : Any> render(query: AggregateQuery<T>): Pair<String, List<Any>> {
        val bound = Bound()
        val sql = query.sql(tables, bound::bind)
        SpatialGate.requireReadOnly(sql)
        return sql to bound.values
    }

    @Test
    fun `el territorio casa por localidad o comunidad, una columna ilegible no casa y sin nada nada casa`() {
        val bound = Bound()
        val both = DashboardData.Territory(setOf(locality.toString()), setOf(community.toString()), null)
        assertEquals(
            "COALESCE(x.\"locality\" = ANY(:g0) OR x.\"community\" = ANY(:g1), FALSE)",
            AggregateSql.inTerritory("x.\"locality\"", "x.\"community\"", both, bound::bind)
        )
        assertEquals("COALESCE(x.\"community\" = ANY(:g2), FALSE)", AggregateSql.inTerritory(null, "x.\"community\"", both, bound::bind))
        assertEquals("FALSE", AggregateSql.inTerritory(null, null, both, bound::bind))
        assertEquals("FALSE", AggregateSql.inTerritory("x.\"locality\"", "x.\"community\"", DashboardData.Territory(emptySet(), emptySet(), null), bound::bind))
    }

    @Test
    fun `sin ambito tile y own son TRUE y con AIDS own es el territorio de cada proyecto`() {
        val source = AggregateSource("social_risk", setOf("project", "locality", "community"))
        val plain = AggregateSql.where(tables, Bound()::bind, source, AggregateFilter(listOf(p1, p2), emptyList()))
        assertEquals("TRUE", plain.tile)
        assertEquals("TRUE", plain.own)
        assertEquals("x.\"project\" = ANY(:g0) AND ((TRUE) OR (TRUE))", plain.condition)
        val own1 = DashboardData.Territory(setOf(locality.toString()), emptySet(), null)
        val own2 = DashboardData.Territory(emptySet(), setOf(community.toString()), null)
        val all = DashboardData.Territory(own1.localities, own2.communities, null)
        val scoped =
            AggregateSql.where(
                tables,
                Bound()::bind,
                source,
                AggregateFilter(listOf(p1, p2), emptyList(), ScopeTerritories(all, mapOf(p2 to own2, p1 to own1)))
            )
        assertTrue(scoped.tile.startsWith("COALESCE(x.\"locality\" = ANY("), scoped.tile)
        assertTrue(scoped.own.startsWith("(x.\"project\" = :g"), scoped.own)
        assertEquals(2, Regex("""\(x\."project" = :g\d+ AND COALESCE""").findAll(scoped.own).count(), scoped.own)
        assertTrue(scoped.condition.endsWith("AND ((${scoped.tile}) OR (${scoped.own}))"), scoped.condition)
    }

    @Test
    fun `las QCS cuentan con la regla de reclasificacion de los tres objetos y sin campos ilegibles`() {
        // inquiry no deja leer su vínculo con la queja ni su closed_date; nadie lee confidential
        val sources =
            Attention.OBJECTS.associateWith { type ->
                val links =
                    Attention.RECLASSIFIED_FROM.getValue(type).keys - (if (type == Attention.INQUIRY) setOf("reclassified_from_grievance") else emptySet())
                AggregateSource(
                    type,
                    setOf("project", "locality", "community", Attention.RECEIVED_AT) + links +
                        (if (type == Attention.INQUIRY) emptySet() else setOf("closed_date"))
                )
            }
        val query =
            AggregateSql.attention(
                sources,
                AggregateFilter(listOf(p1), emptyList()),
                EngagementKpis.months(LocalDate.parse("2026-09-30")),
                excludeConfidential = true
            )
        val (sql, values) = render(query)
        assertTrue(sql.startsWith("WITH q AS (SELECT "), sql)
        assertEquals(3, Regex("FROM <(grievance|inquiry|stakeholder_request)> x WHERE").findAll(sql).count(), sql)
        assertTrue(sql.contains("CAST(NULL AS uuid) AS from_grievance"), sql)
        assertTrue(sql.contains("LEFT JOIN (SELECT DISTINCT type, id FROM pointed WHERE tile) g ON g.type = q.type AND g.id = q.id "), sql)
        assertTrue(
            sql.contains(
                "LEFT JOIN (SELECT DISTINCT type, id, project FROM pointed WHERE own) o ON o.type = q.type AND o.id = q.id AND o.project = q.project)"
            ),
            sql
        )
        assertFalse(sql.contains("confidential"), sql)
        assertTrue(sql.endsWith("GROUP BY project, type, state, tile, own, gone, gone_own, received, closed"), sql)
        // solo la queja lleva los meses de la serie: de octubre de 2025 a septiembre de 2026, en Lima
        assertTrue(values.contains(OffsetDateTime.parse("2025-10-01T05:00Z")), values.toString())
        assertTrue(values.contains(OffsetDateTime.parse("2026-10-01T05:00Z")), values.toString())
        assertTrue(values.contains(LocalDate.parse("2025-10-01")) && values.contains(LocalDate.parse("2026-09-30")), values.toString())
        assertTrue("America/Lima" in values)
        assertEquals(sources.values.map { it.read() }, query.reads)
        assertFalse(
            query.reads
                .single { it.objectName == Attention.INQUIRY }
                .fields
                .contains("closed_date")
        )
    }

    @Test
    fun `excludeConfidential deja fuera las confidenciales solo si se lee la marca`() {
        val sources =
            Attention.OBJECTS.associateWith {
                AggregateSource(it, setOf("project", Attention.RECEIVED_AT, Attention.CONFIDENTIAL) + Attention.RECLASSIFIED_FROM.getValue(it).keys)
            }
        val months = EngagementKpis.months(LocalDate.parse("2026-09-30"))
        val (excluded, _) = render(AggregateSql.attention(sources, AggregateFilter(listOf(p1), emptyList()), months, excludeConfidential = true))
        assertEquals(3, Regex("""x\."confidential" IS NOT TRUE""").findAll(excluded).count(), excluded)
        val (kept, _) = render(AggregateSql.attention(sources, AggregateFilter(listOf(p1), emptyList()), months, excludeConfidential = false))
        assertFalse(kept.contains("IS NOT TRUE"), kept)
    }

    @Test
    fun `las interacciones van desde el primer dia del periodo o de la serie hasta el fin del periodo en Lima`() {
        val source = AggregateSource("engagement", setOf("project", "held_at", "engagement_type"))
        val query =
            AggregateSql.held(
                source,
                AggregateFilter(listOf(p1), emptyList()),
                setOf("held", "closed"),
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2026-09-30"),
                LocalDate.parse("2025-10-01")
            )
        val (sql, values) = render(query)
        assertTrue(sql.contains("CAST(x.\"engagement_type\" AS text) AS type"), sql)
        assertTrue(sql.endsWith("GROUP BY 1, 2, 3, 4, 5, 6"), sql)
        assertTrue(values.contains(OffsetDateTime.parse("2024-01-01T05:00Z")), values.toString())
        assertTrue(values.contains(OffsetDateTime.parse("2026-10-01T05:00Z")), values.toString())
        assertFalse(values.contains(OffsetDateTime.parse("2025-10-01T05:00Z")), values.toString())
        assertTrue(values.any { it is Array<*> && it.toList() == listOf("closed", "held") }, values.toString())
        // sin leer engagement_type, el tipo sale NULL y no se declara
        val blind = AggregateSource("engagement", setOf("project", "held_at"))
        assertTrue(
            render(
                AggregateSql.held(
                    blind,
                    AggregateFilter(listOf(p1), emptyList()),
                    setOf("held"),
                    LocalDate.parse("2026-09-01"),
                    LocalDate.parse("2026-09-30"),
                    LocalDate.parse("2025-10-01")
                )
            ).first.contains("CAST(NULL AS text) AS type")
        )
    }

    @Test
    fun `un conteo agrupado agrupa por proyecto, sus claves, tile y own`() {
        val source = AggregateSource("early_warning", setOf("project", "severity"))
        val query =
            AggregateSql.grouped(
                "warnings",
                source,
                AggregateFilter(listOf(p1), emptyList()),
                listOf("severity" to "text", SpatialGate.STATE to "text")
            ) { bind ->
                listOf("x.\"workflow_state\" = ANY(${bind(arrayOf("detected"))})")
            }
        val (sql, _) = render(query)
        assertTrue(
            sql.startsWith(
                "SELECT x.\"project\" AS project, CAST(x.\"severity\" AS text) AS k0, CAST(x.\"workflow_state\" AS text) AS k1, TRUE AS tile, TRUE AS own, count(*) AS n FROM <early_warning> x WHERE "
            ),
            sql
        )
        assertTrue(sql.endsWith(" AND x.\"workflow_state\" = ANY(:g1) GROUP BY 1, 2, 3, 4, 5"), sql)
        assertEquals("warnings", query.key)
    }
}
