package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// el SQL de las consultas de cercanía (casos 7, 12 y 13 de gis.md §5.3): prefiltro con el índice en 4326 y la envolvente
// ampliada, medida exacta en el working_crs; el área de una concesión sobre el tenant, en la zona de su centroide
class ProximitySqlTest {
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

    private class Bound {
        val values = mutableListOf<Any>()

        fun bind(value: Any): String {
            values += value
            return ":g${values.size - 1}"
        }
    }

    @Test
    fun `los elementos sensibles se prefiltran en grados y se miden en el CRS de trabajo`() {
        val bound = Bound()
        val sql = ProjectProximityQueries.sensitiveSql(tables, bound::bind, ProjectFrame(UUID.randomUUID(), 32717), BigDecimal("1000"), 50)
        assertTrue(sql.contains("f.\"boundary\" && ST_Transform(ST_Expand(COALESCE(ST_Transform(c.\"footprint\", 4326), "), sql)
        assertTrue(sql.contains("f.\"location\" && ST_Transform(ST_Expand("), sql)
        assertTrue(sql.contains("ST_DWithin(ST_Transform(COALESCE("), sql)
        assertTrue(sql.contains("ORDER BY distance_m, 2, 5, 1, 4"), sql)
        assertTrue(32717 in bound.values)
        assertTrue(0.01 in bound.values)
        assertEquals(ProjectProximityQueries.SENSITIVE, (bound.values.first { it is Array<*> } as Array<*>).toList())
    }

    @Test
    fun `la referencia junta el area de interes y los componentes vigentes`() {
        val bound = Bound()
        val project = UUID.randomUUID()
        val sql = ProjectProximityQueries.reference(tables, bound::bind, project)
        assertTrue(sql.startsWith("WITH ref AS (SELECT ST_Collect(s.g) AS g FROM ("), sql)
        assertTrue(sql.contains("ST_Transform(p.\"interest_area\", 4326)"), sql)
        assertTrue(sql.contains("c.\"superseded\" IS NOT TRUE"), sql)
        assertEquals(listOf<Any>(project, project), bound.values)
    }

    @Test
    fun `el area de una concesion sobre una comunidad va en la zona del centroide`() {
        val sql = ProjectProximityQueries.concessionSql(tables, Bound()::bind, listOf(UUID.randomUUID()), ProjectProximityQueries.TERRITORIES.first(), 10)
        assertTrue(sql.contains("IN (32717, 32718, 32719)"), sql)
        assertTrue(sql.contains("ORDER BY 2, 3, 1, 5, 4"), sql)
        assertTrue(
            sql.contains(
                "JOIN <community> x ON ((ST_Intersects(ST_Transform(k.\"boundary\", 4326), x.\"territory\") AND NOT ST_Touches(ST_Transform(k.\"boundary\", 4326), x.\"territory\")))"
            ),
            sql
        )
    }

    @Test
    fun `los eventos y las localidades se prefiltran sobre la columna desnuda`() {
        val project = ProjectFrame(UUID.randomUUID(), 32717)
        val event = ProjectProximityQueries.eventSql(tables, Bound()::bind, project, BigDecimal("1000"), 50, "incident", "code")
        assertTrue(event.contains("e.\"location\" && ST_Transform(ST_Expand(ref.g, "), event)
        assertTrue(event.contains("e.\"location\" IS NULL"), event)
        assertTrue(event.contains("l.\"boundary\" && ST_Transform(ST_Expand(ref.g, "), event)
        assertTrue(event.contains("UNION ALL"), event)
        assertTrue(event.contains("ORDER BY distance_m, id"), event)
        val place = ProjectProximityQueries.localitySql(tables, Bound()::bind, project, BigDecimal("1000"), 50)
        assertTrue(place.contains("l.\"location\" && ST_Transform(ST_Expand(ref.g, "), place)
        assertTrue(place.contains("ORDER BY distance_m, id"), place)
    }
}
