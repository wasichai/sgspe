package sgspe

import sgspe.common.JobTasks

// los campos de model.json que el registro mínimo de una prueba (F2ApiTest.sample) nunca manda, porque una persona no los
// escribe: los derivados (los calcula sgspe y DerivedFieldsGuard los rechaza), las geometrías (van en su propia sección) y los
// que una guarda reserva a la plataforma
object SampleFields {
    // los campos que solo escribe la plataforma (sin usuario): una persona que los fija recibe 403. el modelo no los marca, así
    // que la lista vive aquí y SampleFieldsTest exige que existan. hoy, generator_key de task (GeneratorKeyGuard): es único en
    // el modelo, así que la regla genérica lo mandaría
    val PLATFORM_ONLY: Map<String, Set<String>> = mapOf(JobTasks.TASK to setOf(JobTasks.GENERATOR_KEY))

    // `field`, una entrada de "fields" de model.json
    fun skipped(
        objectName: String,
        field: Map<String, Any?>
    ): Boolean = field["derived"] == true || field["type"] == "GEOMETRY" || field["name"] in PLATFORM_ONLY[objectName].orEmpty()
}
