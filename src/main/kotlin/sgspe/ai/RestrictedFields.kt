package sgspe.ai

// los campos restringidos de RQ-SEC-010 (security.md §7.9), cada uno con el marcador tipado que lo reemplaza antes de un modelo:
// una sola fuente para el asistente (AI-05) y los agentes propios (especificación de F7 §2.2.2, ASM-130). RestrictedFieldsTest
// la compara con §7.9 y con model/roles.json. contact_value puede ser teléfono, correo o dirección: [CONTACTO]; la postura, la
// religión, el proceso electoral y sus razones: [DATO_SENSIBLE]; la cita de una evidencia: [CITA]
object RestrictedFields {
    val FIELDS: Map<String, Map<String, Marker>> =
        mapOf(
            "stakeholder" to mapOf("stance" to Marker.DATO_SENSIBLE),
            "stakeholder_assessment" to
                mapOf(
                    "stance" to Marker.DATO_SENSIBLE,
                    "stance_reasons" to Marker.DATO_SENSIBLE,
                    "concerns" to Marker.DATO_SENSIBLE,
                    "expectations" to Marker.DATO_SENSIBLE
                ),
            "locality_profile" to mapOf("religion" to Marker.DATO_SENSIBLE, "electoral_process" to Marker.DATO_SENSIBLE),
            "person" to mapOf("id_document_number" to Marker.DOCUMENTO, "given_names" to Marker.PERSONA, "surnames" to Marker.PERSONA),
            "contact_method" to mapOf("contact_value" to Marker.CONTACTO),
            "engagement_participant" to mapOf("participant_name" to Marker.PERSONA, "phone" to Marker.TELEFONO, "email" to Marker.CORREO),
            "commitment" to mapOf("beneficiaries_detail" to Marker.PERSONA),
            "delivery" to mapOf("recipients" to Marker.PERSONA, "conformity_by" to Marker.PERSONA),
            "land_parcel" to mapOf("registry_record" to Marker.DOCUMENTO),
            "land_right" to mapOf("holder_person" to Marker.PERSONA),
            "grievance" to mapOf("person" to Marker.PERSONA),
            "inquiry" to mapOf("person" to Marker.PERSONA),
            "stakeholder_request" to mapOf("person" to Marker.PERSONA),
            "ai_suggestion" to mapOf("evidence_quote" to Marker.CITA),
            "commitment_candidate" to mapOf("evidence_quote" to Marker.CITA)
        )

    // los campos restringidos del objeto (nombre como lo escribe wasichai: sin espacios y en minúsculas), o ninguno
    fun of(objectName: String?): Map<String, Marker> = objectName?.let { FIELDS[it.trim().lowercase()] }.orEmpty()
}
