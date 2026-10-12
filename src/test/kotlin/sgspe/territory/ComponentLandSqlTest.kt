package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// la consulta de KPI-TER-04 y de summary (especificación de F6 §3.2): los componentes vigentes de los proyectos que cruzan
// territorio comunal o un predio, cada lado en el SRID del campo con que se cruza, y una fila más que el tope
class ComponentLandSqlTest {
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
            ) = if (objectName == TerritoryNames.COMMUNITY) 4326 else 32718
        }

    @Test
    fun `cruza las versiones vigentes con el territorio comunal y con los predios`() {
        val values = mutableListOf<Any>()
        val sql =
            ComponentLand.sql(tables, { value ->
                values += value
                ":g${values.size - 1}"
            }, listOf(UUID.randomUUID()), 10)
        assertTrue(sql.contains("FROM <project_component> c JOIN <community> m ON (ST_Intersects(COALESCE(ST_Transform(c.\"footprint\", 4326), "), sql)
        assertTrue(sql.contains("FROM <project_component> c JOIN <land_parcel> p ON (ST_Intersects(COALESCE(ST_Transform(c.\"footprint\", 32718), "), sql)
        assertEquals(2, Regex("AND NOT ST_Touches\\(").findAll(sql).count(), sql)
        assertTrue(sql.contains("c.\"superseded\" IS NOT TRUE"), sql)
        assertTrue(sql.contains(" UNION ALL "), sql)
        assertTrue(sql.endsWith("LIMIT :g${values.size - 1}"), sql)
        assertEquals(11, values.last())
        assertTrue(ComponentLand.COMMUNAL in values)
    }

    @Test
    fun `un predio sin tenencia sale como SIN TENENCIA y no como nulo`() {
        assertEquals("SIN TENENCIA", ComponentLand.tenureOf(null))
        assertEquals("PROPIEDAD", ComponentLand.tenureOf("PROPIEDAD"))
    }
}
