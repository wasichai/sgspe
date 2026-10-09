package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.JobTasks
import sgspe.common.ModelCatalog

// el registro mínimo de las pruebas de integración (F2ApiTest.sample) nunca manda lo que una persona no puede escribir: los
// derivados, las geometrías y los campos que solo escribe la plataforma. generator_key de task es único en el modelo, así que
// la regla genérica lo mandaba y GeneratorKeyGuard (Tarea 18) dejaba a la matriz de permisos con 403 en task
class SampleFieldsTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun field(
        name: String,
        type: String = "TEXT",
        derived: Boolean = false
    ): Map<String, Any?> = mapOf("name" to name, "type" to type, "derived" to derived)

    @Test
    fun `generator_key de task no entra en el registro minimo, y el titulo si`() {
        assertTrue(SampleFields.skipped(JobTasks.TASK, field(JobTasks.GENERATOR_KEY)))
        assertFalse(SampleFields.skipped(JobTasks.TASK, field("title")))
        // en otro objeto, un campo con el mismo nombre no es de la plataforma
        assertFalse(SampleFields.skipped("project", field(JobTasks.GENERATOR_KEY)))
    }

    @Test
    fun `los derivados y las geometrias tampoco entran`() {
        assertTrue(SampleFields.skipped("task", field("deadline_status", "ENUM", derived = true)))
        assertTrue(SampleFields.skipped("area_of_influence", field("geometry", "GEOMETRY")))
    }

    // cada campo reservado a la plataforma existe en model.json y no es derivado: si fuera derivado ya lo saltaría la otra regla,
    // y un nombre que el modelo no tiene no protegería nada
    @Test
    fun `los campos de la plataforma existen en el modelo`() {
        assertEquals(mapOf(JobTasks.TASK to setOf(JobTasks.GENERATOR_KEY)), SampleFields.PLATFORM_ONLY)
        for ((objectName, fields) in SampleFields.PLATFORM_ONLY) {
            val obj = catalog.objectOrNull(objectName)
            for (name in fields) {
                val modelField = obj?.fields?.get(name)
                assertTrue(modelField != null, "$objectName.$name no está en model.json")
                assertFalse(modelField!!.derived, "$objectName.$name es derivado")
            }
        }
    }
}
