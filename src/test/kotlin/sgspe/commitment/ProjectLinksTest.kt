package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.util.UUID

// P54: un compromiso solo referencia registros de su proyecto; los campos salen del modelo, sin base
class ProjectLinksTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `los campos vinculados con alcance de proyecto salen del modelo`() {
        val fields = ProjectLinks.fields(ModelCatalog.fromClasspath()).map { it.first }
        assertTrue(fields.containsAll(listOf("exploration_campaign", "origin_engagement", "agreement")), fields.toString())
        // el propio alcance no es un vínculo, ni lo que no es de un proyecto (localidad, tema, comunidad)
        assertFalse("project" in fields)
        assertTrue(listOf("locality", "topic", "community").none { it in fields }, fields.toString())
    }

    @Test
    fun `un destino de otro proyecto, o sin proyecto, es una discrepancia`() {
        assertEquals(emptyList<String>(), ProjectLinks.mismatches(a, mapOf("agreement" to a, "exploration_campaign" to a)))
        assertEquals(listOf("exploration_campaign"), ProjectLinks.mismatches(a, mapOf("agreement" to a, "exploration_campaign" to b)))
        assertEquals(listOf("agreement"), ProjectLinks.mismatches(a, mapOf("agreement" to null)))
        assertEquals(emptyList<String>(), ProjectLinks.mismatches(null, mapOf("agreement" to b)))
    }

    @Test
    fun `un destino que puede ser del tenant pasa sin proyecto, pero no con el de otro`() {
        val targets = mapOf("source_evidence" to ("evidence" to null), "engagement" to ("engagement" to null))
        assertEquals(listOf("engagement"), ProjectLinks.mismatches(a, targets, setOf("evidence")))
        assertEquals(listOf("source_evidence"), ProjectLinks.mismatches(a, mapOf("source_evidence" to ("evidence" to b)), setOf("evidence")))
        assertEquals(listOf("source_evidence", "engagement"), ProjectLinks.mismatches(a, targets, emptySet()))
    }
}
