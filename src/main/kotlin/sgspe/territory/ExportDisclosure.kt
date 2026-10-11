package sgspe.territory

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import sgspe.privacy.Disclosure
import wasichai.core.common.WasichaiException

// la divulgación por capa de las exportaciones geográficas (especificación de F6 §3.5 y §2.2.8, ASM-124, deliverables.md §9):
// - PUBLICO: solo los objetos de las capas públicas (LYR-01 a 05, 07 y 16);
// - EXTERNO: además community_sector, environmental_feature, project (ubicación y área de interés, con código y nombre), los
//   accesos de project_component y area_of_influence; land_parcel de tenencia PROPIEDAD, POSESION o COMUNAL. sin filtro, el
//   filtro fijo del objeto se aplica solo (nunca salen sondajes, plataformas ni campamento en EXTERNO); pedir algo fuera de él
//   es 403 DISCLOSURE_NOT_ALLOWED sobre el filtro;
// - INTERNO: todos, sin campos quitados más allá de los permisos de campo.
// fuera de INTERNO no salen las partidas registrales (registry_record de los predios; las de community) ni la procedencia de
// una importación (source_reference, import_batch_id). land_right no se exporta nunca: no está en EXPORTABLE. puro
object ExportDisclosure {
    const val COMPONENT = "project_component"
    const val PARCEL = "land_parcel"

    val PUBLIC: Set<String> = setOf("administrative_unit", "locality", "road_access", "community", "mining_concession", "school", "health_facility")

    val EXPORTABLE: Set<String> = PUBLIC + setOf("community_sector", "environmental_feature", "project", COMPONENT, "area_of_influence", PARCEL)

    private val EXTERNAL_FIXED: Map<String, Map<String, Set<String>>> =
        mapOf(
            COMPONENT to mapOf("component_type" to setOf("ACCESO")),
            PARCEL to mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL"))
        )

    // el nombre del filtro en el cuerpo, para el campo del 403
    private val FILTER_FIELDS = mapOf("component_type" to "filters.componentType", "tenure_type" to "filters.tenureType")

    private val REGISTRY: Map<String, Set<String>> =
        mapOf(PARCEL to setOf("registry_record"), "community" to setOf("legal_entity_record", "property_record", "parent_record", "title_reference"))
    private val PROVENANCE = setOf("source_reference", "import_batch_id")
    private val PROJECT_OUTSIDE = setOf("code", "name")

    fun allows(
        objectName: String,
        level: Disclosure
    ): Boolean =
        when (level) {
            Disclosure.INTERNO, Disclosure.EXTERNO -> objectName in EXPORTABLE
            Disclosure.PUBLICO -> objectName in PUBLIC
        }

    fun check(
        objectName: String,
        level: Disclosure,
        requested: Map<String, Set<String>>
    ): Map<String, Set<String>> {
        if (!allows(objectName, level)) throw notAllowed("$objectName no se exporta en ${level.name}", "disclosureLevel")
        if (level != Disclosure.EXTERNO) return requested
        val out = LinkedHashMap(requested)
        for ((field, allowed) in EXTERNAL_FIXED[objectName].orEmpty()) {
            val asked = requested[field]
            if (asked != null && !allowed.containsAll(asked)) {
                throw notAllowed("En EXTERNO, $objectName solo sale con ${allowed.joinToString(", ")}", FILTER_FIELDS.getValue(field))
            }
            out[field] = asked ?: allowed
        }
        return out
    }

    fun columns(
        objectName: String,
        level: Disclosure,
        candidates: List<String>
    ): List<String> =
        when {
            level == Disclosure.INTERNO -> candidates
            objectName == "project" -> candidates.filter { it in PROJECT_OUTSIDE }
            else -> candidates.filter { it !in REGISTRY[objectName].orEmpty() && it !in PROVENANCE }
        }

    private fun notAllowed(
        message: String,
        field: String
    ): WasichaiException = Finding("DISCLOSURE_NOT_ALLOWED", message, field, status = HttpStatus.FORBIDDEN).toCodedException()
}
