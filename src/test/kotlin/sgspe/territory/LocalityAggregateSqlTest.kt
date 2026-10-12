package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// los agregados por localidad de las capas de gestión (gis.md §2.2, RQ-GIS-015): cuentan y suman en la tabla del objeto,
// agrupan por localidad y nunca nombran un registro; el punto es el de la localidad; las localidades se cortan en el límite y
// sus cifras quedan enteras
class LocalityAggregateSqlTest {
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

    private fun render(build: (GateTables, (Any) -> String) -> String): Pair<String, List<Any>> {
        val values = mutableListOf<Any>()
        val sql =
            build(tables) { value ->
                values += value
                ":g${values.size - 1}"
            }
        return sql to values
    }

    @Test
    fun `cuenta por localidad y postura sin nombrar nunca un grupo de interes`() {
        val (sql, values) = render { t, bind -> LocalityAggregateSql.stakeholders(t, bind, emptyList(), true, null, 50) }
        assertTrue(
            sql.startsWith(
                "WITH k AS (SELECT s.\"locality\" AS zone, s.\"stance\"::text AS stance, count(*) AS n FROM <stakeholder> s " +
                    "WHERE s.\"locality\" IS NOT NULL GROUP BY 1, 2)"
            ),
            sql
        )
        assertFalse(sql.contains("s.\"id\""), sql)
        assertTrue(sql.contains("COALESCE(ST_Transform(l.\"location\", 4326), ST_PointOnSurface(ST_Transform(l.\"boundary\", 4326))) AS g"), sql)
        assertTrue(sql.contains("CASE WHEN l.\"location\" IS NOT NULL THEN 'location' ELSE 'boundary' END AS field"), sql)
        assertTrue(sql.endsWith("ORDER BY z.zone LIMIT :g0) y JOIN k ON k.zone = y.zone ORDER BY y.zone, k.stance NULLS LAST"), sql)
        assertEquals(listOf<Any>(50), values)
    }

    @Test
    fun `sin leer la postura solo cuenta el total`() {
        val (sql, _) = render { t, bind -> LocalityAggregateSql.stakeholders(t, bind, emptyList(), false, null, 50) }
        assertTrue(sql.contains("NULL::text AS stance"), sql)
        assertFalse(sql.contains("\"stance\""), sql)
    }

    @Test
    fun `el proyecto acota los grupos y el recuadro el punto de la localidad`() {
        val project = UUID.randomUUID()
        val (sql, values) = render { t, bind -> LocalityAggregateSql.stakeholders(t, bind, listOf(project), true, LayerBox(-78.6, -8.0, -78.4, -7.8), 10) }
        assertTrue(sql.contains("WHERE s.\"locality\" IS NOT NULL AND s.\"project\" = ANY(:g0) GROUP BY 1, 2"), sql)
        assertTrue(sql.contains("WHERE z.g IS NOT NULL AND ST_Intersects(z.g, ST_MakeEnvelope(:g1, :g2, :g3, :g4, 4326)) ORDER BY z.zone LIMIT :g5"), sql)
        assertEquals(listOf(project), (values[0] as Array<*>).toList())
        assertEquals(10, values[5])
    }
}
