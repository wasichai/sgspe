package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordCriterion
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import wasichai.gis.GEOMETRY
import wasichai.gis.SRID_COLUMN
import java.util.UUID

// los filtros por zona (especificación de F6 §2.2 elección 4 y §3.1): la zona ya leída como quien llama viaja enlazada como
// GeoJSON, nunca como subconsulta a otra tabla; la relación territorial y la geometría se unen por OR; lo que quien llama no
// lee (la definición que entrega wasichai no lo trae) no filtra, y sin nada que filtrar no pasa ningún registro
class ZoneCriterionTest {
    private val locality = UUID.randomUUID()
    private val sector = UUID.randomUUID()
    private val square = mapOf("type" to "Polygon", "coordinates" to listOf(listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0), listOf(0.0, 0.0))))

    private fun definition(vararg fields: Pair<String, Int?>): ObjectDefinition {
        val objectId = UUID.randomUUID()
        return ObjectDefinition(
            CustomObject(objectId, UUID.randomUUID(), "commitment", "commitment", "commitment", null, true, "commitment_tbl", null, null),
            fields.mapIndexed { i, (field, srid) ->
                CustomField(
                    id = UUID.randomUUID(),
                    objectId = objectId,
                    name = field,
                    label = field,
                    type = if (srid == null) FieldType.TEXT else GEOMETRY,
                    columnName = "c_$field",
                    required = false,
                    unique = false,
                    defaultValue = null,
                    description = null,
                    position = i,
                    enumOptions = null,
                    relationTargetObjectId = null,
                    attributes = if (srid == null) emptyMap() else mapOf(SRID_COLUMN to srid),
                    visible = true,
                    editable = true
                )
            }
        )
    }

    // la condición con sus valores, como los enlazaría wasichai (:c0, :c1, …)
    private fun render(
        criterion: RecordCriterion,
        definition: ObjectDefinition
    ): Pair<String, List<Any>> {
        val values = mutableListOf<Any>()
        val sql =
            criterion.condition(definition) { value ->
                values += value
                ":c${values.size - 1}"
            }
        return sql to values
    }

    @Test
    fun `la relacion territorial y la geometria se unen por OR, con la zona enlazada`() {
        val zone = Zone(mapOf("locality" to setOf(locality, sector), "community" to emptySet()), square)
        val (sql, values) =
            render(
                ZoneCriterion.of(listOf("locality", "community"), listOf("location"), zone),
                definition(
                    "locality" to null,
                    "community" to null,
                    "location" to 4326
                )
            )
        assertEquals("\"c_locality\" = ANY(:c0) OR ST_Intersects(\"c_location\", ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:c1), 4326), 4326))", sql)
        assertEquals(setOf(locality, sector), (values[0] as Array<*>).toSet())
        assertTrue((values[1] as String).contains("\"Polygon\""))
    }

    @Test
    fun `la zona se transforma al SRID del campo, nunca se supone 4326`() {
        val (sql, _) = render(ZoneCriterion.of(emptyList(), listOf("location"), Zone(emptyMap(), square)), definition("location" to 32718))
        assertEquals("ST_Intersects(\"c_location\", ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:c0), 4326), 32718))", sql)
    }

    @Test
    fun `una zona sin geometria filtra solo por relacion`() {
        val zone = Zone(mapOf("locality" to setOf(locality)), null)
        val (sql, _) = render(ZoneCriterion.of(listOf("locality"), listOf("location"), zone), definition("locality" to null, "location" to 4326))
        assertEquals("\"c_locality\" = ANY(:c0)", sql)
    }

    @Test
    fun `lo que quien llama no lee no filtra y sin nada que filtrar no pasa ninguno`() {
        val zone = Zone(mapOf("locality" to setOf(locality), "community" to setOf(UUID.randomUUID())), square)
        // la definición filtrada no trae locality ni location: queda community
        val (sql, _) = render(ZoneCriterion.of(listOf("locality", "community"), listOf("location"), zone), definition("community" to null))
        assertEquals("\"c_community\" = ANY(:c0)", sql)
        val (nothing, values) = render(ZoneCriterion.of(listOf("locality"), listOf("location"), zone), definition("title" to null))
        assertEquals("FALSE", nothing)
        assertTrue(values.isEmpty())
    }

    @Test
    fun `within prefiltra con el indice en grados y mide en metros en el CRS de trabajo`() {
        val (sql, values) = render(ZoneCriterion.within(listOf("location", "boundary"), square, 500, 32717), definition("location" to 4326, "boundary" to 4326))
        val ref = "ST_SetSRID(ST_GeomFromGeoJSON(:c0), 4326)"
        assertTrue(sql.startsWith("(\"c_location\" && ST_Transform(ST_Expand($ref, :c1), 4326) AND ST_DWithin(ST_Transform(\"c_location\", :c2), "), sql)
        assertTrue(sql.contains(") OR (\"c_boundary\" && "), sql)
        assertEquals(0.005, values[1])
        assertEquals(32717, values[2])
        assertEquals(500.0, values[4])
    }

    @Test
    fun `notTrue deja pasar falso o vacio y falla cerrado sin la columna`() {
        assertEquals("\"c_superseded\" IS NOT TRUE", render(ZoneCriterion.notTrue("superseded"), definition("superseded" to null)).first)
        assertEquals("FALSE", render(ZoneCriterion.notTrue("superseded"), definition("code" to null)).first)
    }

    @Test
    fun `los objetos de una zona, el grupo de interes nunca por geometria y cada fecha con su tipo`() {
        val stakeholder = ZoneTargets.ALL.getValue("stakeholder")
        assertEquals(emptyList<String>(), stakeholder.geometries)
        assertNull(stakeholder.period)
        assertEquals(listOf("locality", "area_of_influence"), stakeholder.relations)
        assertEquals(PeriodField("agreed_date", instant = false), ZoneTargets.ALL.getValue("commitment").period)
        assertEquals(PeriodField("received_at", instant = true), ZoneTargets.ALL.getValue("grievance").period)
        assertNull(ZoneTargets.ALL.getValue("social_risk").period)
        assertTrue(ZoneTargets.ALL.values.all { "locality" in it.relations })
        assertEquals(
            setOf("stakeholder", "commitment", "social_risk", "grievance", "inquiry", "stakeholder_request", "engagement", "incident", "early_warning_signal"),
            ZoneTargets.ALL.keys
        )
    }
}
