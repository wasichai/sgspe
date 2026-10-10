package sgspe

import sgspe.common.PlatformOnlyFields

// los campos de model.json que el registro mínimo de una prueba (F2ApiTest.sample) nunca manda, porque una persona no los
// escribe: los derivados (los calcula sgspe y DerivedFieldsGuard los rechaza), las geometrías (van en su propia sección) y los
// que una guarda reserva a la plataforma
object SampleFields {
    // los campos que solo escribe la plataforma (sin usuario): una persona que los fija recibe 403. el modelo no los marca, así
    // que salen de la tabla de la guarda (PlatformOnlyFields) y SampleFieldsTest exige que existan: generator_key de task y,
    // desde F4, el de early_warning, que solo escribe JOB-04 (únicos en el modelo, así que la regla genérica los mandaría) y,
    // desde la ronda branchfix de 4.2b, early_warning_rule y detected_at de la alerta
    val PLATFORM_ONLY: Map<String, Set<String>> = PlatformOnlyFields.FIELDS.mapValues { it.value.keys }

    // `field`, una entrada de "fields" de model.json
    fun skipped(
        objectName: String,
        field: Map<String, Any?>
    ): Boolean = field["derived"] == true || field["type"] == "GEOMETRY" || field["name"] in PLATFORM_ONLY[objectName].orEmpty()
}
