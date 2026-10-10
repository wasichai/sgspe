package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// las tablas de F4 de GeneratorKeyGuard y DependentsDeleteGuard nombran objetos y campos por texto: si el modelo los renombra,
// la guarda dejaría de vigilarlos en silencio
class GuardTablesF4Test {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `cada objeto con generator_key lo declara unico en el modelo`() {
        for (objectName in GeneratorKeyGuard.OBJECTS) {
            val field = catalog.objectOrNull(objectName)?.fields?.get(JobTasks.GENERATOR_KEY)
            assertEquals("TEXT", field?.type, objectName)
        }
    }

    // la relación de la alerta con su regla es opcional: el catálogo no la vigila y por eso va en PRESERVED
    @Test
    fun `las relaciones que DependentsDeleteGuard preserva existen y son opcionales`() {
        for ((target, links) in DependentsDeleteGuard.PRESERVED) {
            for ((source, field) in links) {
                assertEquals(target, catalog.manyToOneFields(source).toMap()[field], "$source.$field")
                assertTrue(!catalog.isRequiredRelation(source, field) && (source to field) !in catalog.guardedDependents(target), "$source.$field")
            }
        }
    }
}
