package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
}
