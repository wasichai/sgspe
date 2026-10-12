package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.scope.ProjectScope
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

// el catálogo de las capas de gestión contra model/model.json (gis.md §2.2 y §2.4): cada geometría, relación y fecha que
// nombra existe con su tipo, cada fuente se acota por proyecto, nada se exporta (ASM-124) y las capas son exactamente
// LYR-17 a LYR-24
class ManagementLayerCatalogTest {
    private val model: JsonNode = JsonMapper.builder().build().readTree(File("model/model.json"))

    private val sources = ManagementLayerCatalog.CATALOG.values.flatMap { it.sources }

    // el tipo de un campo propio del objeto (GEOMETRY, DATE, DATETIME, ENUM, DECIMAL…), o null si no lo tiene
    private fun type(
        objectName: String,
        field: String
    ): String? =
        model["objects"]
            .values()
            .firstOrNull { it["name"].asString() == objectName }
            ?.get("fields")
            ?.values()
            ?.firstOrNull { it["name"].asString() == field }
            ?.get("type")
            ?.asString()

    // el objeto al que apunta la relación de muchos a uno `field` del objeto, o null si no la tiene
    private fun target(
        objectName: String,
        field: String
    ): String? =
        model["relationships"]
            .values()
            .firstOrNull { it["source"].asString() == objectName && it.get("fieldName")?.asString() == field }
            ?.get("target")
            ?.asString()

    @Test
    fun `el catalogo trae exactamente LYR-17 a LYR-24 y no pisa las capas de F2`() {
        assertEquals((17..24).map { "LYR-$it" }.toSet(), ManagementLayerCatalog.CATALOG.keys)
        assertTrue(ManagementLayerCatalog.CATALOG.keys.none { it in Layers.CATALOG })
    }

    @Test
    fun `cada geometria, relacion y fecha de una fuente existe en el modelo con su tipo`() {
        for (source in sources) {
            val o = source.objectName
            source.geometry?.let { assertEquals("GEOMETRY", type(o, it), "$o.$it") }
            for (relation in source.relations) {
                val placement = Placements.BY_RELATION.getValue(relation)
                assertEquals(placement.objectName, target(o, relation), "$o.$relation")
                placement.geometries.forEach { assertEquals("GEOMETRY", type(placement.objectName, it), "${placement.objectName}.$it") }
            }
            when (val period = source.period) {
                is LayerPeriod.Moment -> assertEquals("DATETIME", type(o, period.field), "$o.${period.field}")
                is LayerPeriod.Day -> assertEquals("DATE", type(o, period.field), "$o.${period.field}")
                is LayerPeriod.Span -> period.fields.forEach { assertEquals("DATE", type(o, it), "$o.$it") }
                null -> Unit
            }
            assertTrue(source.geometry != null || source.relations.isNotEmpty(), "$o no tiene nada que dibujar")
        }
    }

    @Test
    fun `los agregados leen campos que existen y un grupo de interes no tiene comunidad`() {
        assertEquals("locality", target("stakeholder", "locality"))
        assertEquals("ENUM", type("stakeholder", "stance"))
        assertNull(target("stakeholder", "community"))
        assertEquals("locality", target("social_contribution", "locality"))
        assertEquals("ENUM", type("social_contribution", "currency"))
        assertEquals("DECIMAL", type("social_contribution", "total_amount"))
        assertEquals("social_contribution", target("delivery", "social_contribution"))
        assertEquals("DATE", type("delivery", "delivery_date"))
    }

    @Test
    fun `ningun objeto propio de una capa de gestion se exporta (ASM-124)`() {
        val own = sources.map { it.objectName } + listOf("stakeholder", "social_contribution")
        assertEquals(emptyList<String>(), own.filter { it in ExportDisclosure.EXPORTABLE })
    }

    @Test
    fun `cada fuente se acota por proyecto, una raiz por su campo y un hijo por un padre que es raiz`() {
        for (source in sources) {
            val o = source.objectName
            val parents = ManagementLayerCatalog.parents(o)
            if (parents.isEmpty()) {
                assertTrue(o in ProjectScope.ROOTS, o)
                assertEquals("project", target(o, "project"), o)
            } else {
                parents.forEach { (field, parent) ->
                    assertTrue(parent in ProjectScope.ROOTS, "$o -> $parent")
                    assertEquals(parent, target(o, field), "$o.$field")
                }
            }
        }
    }

    @Test
    fun `los objetos de cada capa suman los destinos de sus relaciones y los de su agregado`() {
        assertEquals(listOf("commitment", "locality", "community"), ManagementLayerCatalog.CATALOG.getValue("LYR-19").objects)
        assertEquals(listOf("stakeholder", "locality"), ManagementLayerCatalog.CATALOG.getValue("LYR-17").objects)
        assertEquals(
            listOf("social_risk", "locality", "community", "area_of_influence"),
            ManagementLayerCatalog.CATALOG.getValue("LYR-21").objects
        )
        assertEquals(
            listOf("social_initiative", "locality", "community", "delivery", "social_contribution"),
            ManagementLayerCatalog.CATALOG.getValue("LYR-23").objects
        )
    }
}
