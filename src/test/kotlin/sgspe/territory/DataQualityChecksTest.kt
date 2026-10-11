package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// las cinco revisiones de gis.md §8 (especificación de F6 §3.6): qué objetos y campos lee cada una y su SQL sobre las tablas
// de la compuerta, con todos los valores enlazados. GateTables es un doble que nombra tablas y columnas de prueba
class DataQualityChecksTest {
    private val binds = mutableListOf<Any>()
    private val bind: (Any) -> String = {
        binds += it
        "\$${binds.size}"
    }
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "(SELECT * FROM t_$objectName)"

            override fun column(
                objectName: String,
                field: String
            ) = "\"c_$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 4326
        }
    private val envelope = ImportProperties()

    @Test
    fun `los objetos de cada revision`() {
        assertEquals(
            listOf("grievance", "inquiry", "stakeholder_request", "commitment", "social_risk", "incident"),
            DataQualityChecks.objects(QualityCheck.NO_TERRITORY)
        )
        assertEquals(listOf("locality", "community"), DataQualityChecks.objects(QualityCheck.NO_GEOMETRY))
        assertEquals(Areas.GEOMETRY.keys.toList(), DataQualityChecks.objects(QualityCheck.STALE_AREA))
        assertTrue("land_parcel" in DataQualityChecks.objects(QualityCheck.INVALID))
        assertFalse("land_right" in DataQualityChecks.objects(QualityCheck.INVALID))
    }

    @Test
    fun `los campos que lee cada revision, con el proyecto si se filtra`() {
        assertEquals(setOf("code", "locality", "community", "location"), DataQualityChecks.fields(QualityCheck.NO_TERRITORY, "grievance", byProject = false))
        assertTrue("project" in DataQualityChecks.fields(QualityCheck.NO_TERRITORY, "grievance", byProject = true))
        assertEquals(setOf("name", "territory"), DataQualityChecks.fields(QualityCheck.NO_GEOMETRY, "community", byProject = true))
        assertEquals(setOf("code", "boundary", "calculated_area_ha"), DataQualityChecks.fields(QualityCheck.STALE_AREA, "land_parcel", byProject = false))
        assertTrue(DataQualityChecks.needsProject(QualityCheck.OUT_OF_ZONE, listOf("project_component")))
        assertFalse(DataQualityChecks.needsProject(QualityCheck.OUT_OF_ZONE, listOf("locality")))
    }

    @Test
    fun `el SQL de geometrias invalidas usa las tablas de la compuerta y enlaza todo`() {
        val project = UUID.randomUUID()
        val sql = DataQualityChecks.sql(tables, bind, QualityCheck.INVALID, listOf("land_parcel", "grievance"), project, envelope, 51, 50)
        assertTrue(sql.contains("FROM (SELECT * FROM t_land_parcel) t"), sql)
        assertTrue(sql.contains("NOT ST_IsValid(t.\"c_boundary\")"), sql)
        assertTrue(sql.contains(" UNION ALL "), sql)
        assertTrue(sql.contains("t.\"c_project\" = "), sql)
        assertFalse(sql.contains(project.toString()), "el proyecto va enlazado")
        assertTrue(binds.containsAll(listOf("land_parcel", "grievance", project, 51, 50)), binds.toString())
    }

    @Test
    fun `fuera de zona une el proyecto y enlaza la envolvente`() {
        val sql = DataQualityChecks.sql(tables, bind, QualityCheck.OUT_OF_ZONE, listOf("project_component"), null, envelope, 11, 0)
        assertTrue(sql.contains("LEFT JOIN (SELECT * FROM t_project) p ON p.id = t.\"c_project\""), sql)
        assertTrue(binds.containsAll(listOf(envelope.minLongitude, envelope.maxLongitude, envelope.minLatitude, envelope.maxLatitude)), binds.toString())
    }

    @Test
    fun `el detalle de cada revision en espanol`() {
        fun raw(
            reason: String? = null,
            zone: Int? = null,
            expected: Int? = null,
            stored: BigDecimal? = null,
            computed: BigDecimal? = null
        ) = QualityRaw("land_parcel", UUID.randomUUID(), "PRD-01", "boundary", reason, zone, expected, stored, computed)
        assertEquals("autointersección", DataQualityChecks.detail(QualityCheck.INVALID, raw(reason = "Self-intersection[-78.5 -7.9]")))
        assertEquals("zona 18S; el proyecto trabaja en 17S", DataQualityChecks.detail(QualityCheck.OUT_OF_ZONE, raw(zone = 32718, expected = 32717)))
        assertEquals("fuera de la región esperada (envolvente del Perú)", DataQualityChecks.detail(QualityCheck.OUT_OF_ZONE, raw(zone = 32718)))
        assertEquals(
            "guardada 1.23 ha; recalculada 9.00 ha",
            DataQualityChecks.detail(QualityCheck.STALE_AREA, raw(stored = BigDecimal("1.23"), computed = BigDecimal("9.00")))
        )
        assertEquals("sin área calculada; recalculada 9.00 ha", DataQualityChecks.detail(QualityCheck.STALE_AREA, raw(computed = BigDecimal("9.00"))))
    }
}
