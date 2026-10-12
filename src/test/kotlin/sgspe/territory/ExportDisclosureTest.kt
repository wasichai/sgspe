package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import sgspe.privacy.Disclosure
import wasichai.core.common.WasichaiException

// la divulgación por capa de las exportaciones geográficas (especificación de F6 §3.5 y §2.2.8, ASM-124): PUBLICO solo
// capas públicas; EXTERNO suma las internas, los accesos y los predios sin partida; las confidenciales solo en INTERNO;
// land_right nunca
class ExportDisclosureTest {
    private val publicLayers = setOf("administrative_unit", "locality", "road_access", "community", "mining_concession", "school", "health_facility")
    private val internalLayers = setOf("community_sector", "environmental_feature", "project", "project_component", "area_of_influence", "land_parcel")

    private fun code(e: WasichaiException) = Triple(e.status.value(), e.violations.single().message, e.violations.single().field)

    @Test
    fun `solo las capas LYR-01 a LYR-16, nunca land_right ni las de gestion`() {
        assertEquals(publicLayers + internalLayers, ExportDisclosure.EXPORTABLE)
        assertFalse("land_right" in ExportDisclosure.EXPORTABLE)
        assertFalse("grievance" in ExportDisclosure.EXPORTABLE)
    }

    @Test
    fun `PUBLICO solo las capas publicas`() {
        assertTrue(publicLayers.all { ExportDisclosure.allows(it, Disclosure.PUBLICO) })
        assertTrue(internalLayers.none { ExportDisclosure.allows(it, Disclosure.PUBLICO) })
        val e = assertThrows<WasichaiException> { ExportDisclosure.check("land_parcel", Disclosure.PUBLICO, emptyMap()) }
        assertEquals(Triple(403, "DISCLOSURE_NOT_ALLOWED", "disclosureLevel"), code(e))
    }

    @Test
    fun `EXTERNO sin filtro de componentes deja solo ACCESO`() {
        assertEquals(mapOf("component_type" to setOf("ACCESO")), ExportDisclosure.check("project_component", Disclosure.EXTERNO, emptyMap()))
        assertEquals(
            mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL")),
            ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, emptyMap())
        )
    }

    @Test
    fun `EXTERNO con un filtro vacio aplica el filtro fijo`() {
        assertEquals(
            mapOf("component_type" to setOf("ACCESO")),
            ExportDisclosure.check("project_component", Disclosure.EXTERNO, mapOf("component_type" to emptySet()))
        )
        assertEquals(
            mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL")),
            ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, mapOf("tenure_type" to emptySet()))
        )
    }

    @Test
    fun `EXTERNO con sondajes o con predios de la empresa es 403 sobre el filtro`() {
        val drill =
            assertThrows<WasichaiException> { ExportDisclosure.check("project_component", Disclosure.EXTERNO, mapOf("component_type" to setOf("SONDAJE"))) }
        assertEquals(Triple(403, "DISCLOSURE_NOT_ALLOWED", "filters.componentType"), code(drill))
        val company =
            assertThrows<WasichaiException> {
                ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, mapOf("tenure_type" to setOf("COMUNAL", "PROPIEDAD DE LA EMPRESA")))
            }
        assertEquals(Triple(403, "DISCLOSURE_NOT_ALLOWED", "filters.tenureType"), code(company))
        assertEquals(
            mapOf("tenure_type" to setOf("COMUNAL")),
            ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, mapOf("tenure_type" to setOf("COMUNAL")))
        )
    }

    @Test
    fun `INTERNO exporta lo pedido sin filtro fijo`() {
        val drill = mapOf("component_type" to setOf("SONDAJE", "PLATAFORMA", "CAMPAMENTO"))
        assertEquals(drill, ExportDisclosure.check("project_component", Disclosure.INTERNO, drill))
        assertEquals(emptyMap<String, Set<String>>(), ExportDisclosure.check("land_parcel", Disclosure.INTERNO, emptyMap()))
    }

    @Test
    fun `fuera de INTERNO no salen partidas ni procedencia y el proyecto solo con codigo y nombre`() {
        val parcel =
            listOf("code", "tenure_type", "registry_record", "registered_area_ha", "calculated_area_ha", "source_crs", "source_reference", "import_batch_id")
        assertEquals(
            listOf("code", "tenure_type", "registered_area_ha", "calculated_area_ha", "source_crs"),
            ExportDisclosure.columns("land_parcel", Disclosure.EXTERNO, parcel)
        )
        assertEquals(parcel, ExportDisclosure.columns("land_parcel", Disclosure.INTERNO, parcel))
        val community = listOf("name", "legal_entity_record", "property_record", "parent_record", "title_reference", "registered_area_ha", "code")
        assertEquals(listOf("name", "registered_area_ha", "code"), ExportDisclosure.columns("community", Disclosure.PUBLICO, community))
        assertEquals(
            listOf("code", "name"),
            ExportDisclosure.columns("project", Disclosure.EXTERNO, listOf("code", "name", "description", "working_crs", "policy_no_paid_labor"))
        )
    }

    // los campos no geométricos (y las relaciones MANY_TO_ONE) que una exportación puede sacar de cada capa, según
    // model/model.json: un campo nuevo en una capa exportable rompe esta prueba y obliga a decidir su nivel de divulgación
    // (ExportDisclosure.columns) antes de que salga en un archivo
    @Test
    fun `los campos exportables de cada capa estan fijados`() {
        val catalog = ModelCatalog.fromClasspath()
        // "project" no va aquí: fuera de INTERNO solo salen code y name (PROJECT_OUTSIDE), así que un campo nuevo
        // de project no exige una decisión de divulgación
        assertEquals(EXPORTED_FIELDS.keys, ExportDisclosure.EXPORTABLE - "project")
        for ((objectName, fields) in EXPORTED_FIELDS) {
            assertEquals(fields, TerritoryExports.columnsOf(catalog, objectName).toSet(), objectName)
        }
    }

    private companion object {
        val EXPORTED_FIELDS: Map<String, Set<String>> =
            mapOf(
                "administrative_unit" to setOf("ubigeo_code", "name", "level", "parent_unit"),
                "locality" to
                    setOf(
                        "name",
                        "alternative_names",
                        "locality_type",
                        "populated_center_code",
                        "recognition_status",
                        "history",
                        "parent_locality",
                        "administrative_unit"
                    ),
                "road_access" to setOf("road_type", "road_condition", "transport_means", "travel_time", "locality", "connects_to"),
                "community" to
                    setOf(
                        "name",
                        "code",
                        "source_crs",
                        "source_reference",
                        "import_batch_id",
                        "alternative_names",
                        "community_type",
                        "legal_entity_record",
                        "property_record",
                        "parent_record",
                        "independized",
                        "title_reference",
                        "title_issuer_as_written",
                        "title_issuer_normalized",
                        "registered_area_ha",
                        "calculated_area_ha",
                        "registry_records_unverified",
                        "recognized_as_indigenous",
                        "indigenous_people",
                        "organic_status",
                        "board_registered",
                        "registered_members_count",
                        "has_member_roll",
                        "administrative_unit"
                    ),
                "mining_concession" to
                    setOf(
                        "code",
                        "name",
                        "source_crs",
                        "source_reference",
                        "import_batch_id",
                        "registered_area_ha",
                        "calculated_area_ha",
                        "holder_organization"
                    ),
                "school" to setOf("name", "education_level", "school_status", "locality"),
                "health_facility" to setOf("name", "facility_type", "facility_status", "locality"),
                "community_sector" to setOf("name", "delegates_count", "community"),
                "environmental_feature" to
                    setOf(
                        "name",
                        "code",
                        "source_crs",
                        "source_reference",
                        "import_batch_id",
                        "feature_type",
                        "legal_basis",
                        "declaring_authority",
                        "declaration_date",
                        "administrative_unit"
                    ),
                "project_component" to
                    setOf(
                        "code",
                        "component_type",
                        "drill_type",
                        "component_status",
                        "version_number",
                        "superseded",
                        "source_crs",
                        "source_easting",
                        "source_northing",
                        "source_elevation",
                        "azimuth",
                        "dip",
                        "depth_m",
                        "source_reference",
                        "import_batch_id",
                        "project",
                        "exploration_campaign",
                        "previous_version"
                    ),
                "area_of_influence" to setOf("area_type", "version_number", "criteria", "defined_date", "calculated_area_ha", "project", "study"),
                "land_parcel" to
                    setOf(
                        "code",
                        "tenure_type",
                        "registry_record",
                        "registered_area_ha",
                        "calculated_area_ha",
                        "source_crs",
                        "source_reference",
                        "import_batch_id",
                        "community",
                        "locality"
                    )
            )
    }
}
