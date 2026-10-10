package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.common.CommandTransitionGuard
import sgspe.common.ModelCatalog

// la transición de reclasificación de cada estado y los campos que se copian (api.md §4.6), sin base
class ReclassificationsTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `cada estado con reclasificacion tiene su transicion de comando`() {
        assertEquals("close_reclassified_acknowledged", Reclassifications.closing(catalog, "grievance", "acknowledged"))
        assertEquals("close_reclassified", Reclassifications.closing(catalog, "inquiry", "received"))
        assertEquals("close_reclassified_under_evaluation", Reclassifications.closing(catalog, "stakeholder_request", "under_evaluation"))
        assertNull(Reclassifications.closing(catalog, "grievance", "in_action"))
        assertNull(Reclassifications.closing(catalog, "inquiry", "answered"))
        for (objectName in listOf("grievance", "inquiry", "stakeholder_request")) {
            val names =
                catalog
                    .transitions(objectName)
                    .filter { it.name.startsWith(Reclassifications.CLOSE_PREFIX) }
                    .map { it.name }
                    .toSet()
            assertEquals(
                names,
                CommandTransitionGuard.COMMANDS[objectName]
                    .orEmpty()
                    .keys
                    .filter { it.startsWith(Reclassifications.CLOSE_PREFIX) }
                    .toSet()
            )
        }
    }

    @Test
    fun `los campos copiados existen en los tres objetos`() {
        for (objectName in listOf("grievance", "inquiry", "stakeholder_request")) {
            val fields = catalog.objectOrNull(objectName)!!.fields.keys + catalog.manyToOneFields(objectName).map { it.first }
            assertEquals(emptyList<String>(), Reclassifications.COPIED.filter { it !in fields }, objectName)
        }
    }
}
