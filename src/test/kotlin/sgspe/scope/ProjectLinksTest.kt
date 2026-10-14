package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import java.util.UUID

// P54 y la regla «mismo proyecto» de F4: un registro solo referencia registros de su proyecto; los campos salen del modelo,
// sin base
class ProjectLinksTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `los campos vinculados con alcance de proyecto salen del modelo`() {
        val fields = ProjectLinks.fieldsOf(catalog, "commitment").map { it.first }
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

    // F4: la queja y el riesgo de origen de un compromiso, y las relaciones de las QCS hacia objetos con proyecto
    @Test
    fun `los campos de F4 con alcance de proyecto salen del modelo`() {
        assertTrue(ProjectLinks.fieldsOf(catalog, "commitment").map { it.first }.containsAll(listOf("origin_grievance", "social_risk")))
        val grievance = ProjectLinks.fieldsOf(catalog, "grievance").toMap()
        assertEquals(
            mapOf(
                "stakeholder" to "stakeholder",
                "origin_engagement" to "engagement",
                "origin_question" to "engagement_question",
                "social_risk" to "social_risk",
                "incident" to "incident",
                "duplicate_of" to "grievance",
                "reclassified_from_inquiry" to "inquiry",
                "reclassified_from_request" to "stakeholder_request"
            ),
            grievance
        )
        assertTrue("created_commitment" in ProjectLinks.fieldsOf(catalog, "stakeholder_request").toMap())
        assertEquals("early_warning_rule", ProjectLinks.fieldsOf(catalog, "early_warning").toMap()["early_warning_rule"])
    }

    @Test
    fun `una regla de alerta del tenant no es de otro proyecto`() {
        assertEquals(
            setOf("export_log", "evidence", "early_warning_rule", "ai_suggestion", "ai_interaction", "indicator_measurement"),
            ProjectLinks.tenantCapable(catalog)
        )
        val targets = mapOf("early_warning_rule" to ("early_warning_rule" to null))
        assertEquals(emptyList<String>(), ProjectLinks.mismatches(a, targets, ProjectLinks.tenantCapable(catalog)))
    }

    // los vínculos M2M que juzga ProjectLinkGuard existen y salen de un objeto de F4 hacia stakeholder
    @Test
    fun `los vinculos de ProjectLinkGuard existen en el modelo`() {
        for (name in ProjectLinkGuard.LINKS) {
            val relationship = catalog.manyToMany(name)
            assertEquals("stakeholder", relationship?.target, name)
            assertTrue(relationship!!.source in ProjectLinkGuard.OBJECTS, name)
        }
        assertEquals(emptySet<String>(), ProjectLinkGuard.OBJECTS - catalog.objects.keys)
    }

    // el proyecto de un destino (linkedProject, sin base): el suyo en una raíz; en un hijo, el de la raíz a la que llega su
    // padre presente, leído como quien escribe (un salto que no se lee es NotReadable); sin padre, ninguno
    @Test
    fun `el proyecto de un destino hijo sube por su padre, y un salto ilegible es NotReadable`() {
        val clause = UUID.randomUUID()
        val agreement = UUID.randomUUID()
        val read: suspend (String, UUID) -> Map<String, Any?> = { name, id ->
            if (id == agreement) mapOf("project" to a.toString()) else throw NotReadable(name)
        }
        runBlocking {
            assertEquals(a, ProjectLinks.projectOf("agreement_clause", mapOf("agreement" to agreement.toString()), read))
            assertEquals(b, ProjectLinks.projectOf("agreement", mapOf("project" to b.toString()), read))
            assertNull(ProjectLinks.projectOf("agreement_clause", mapOf("agreement" to null), read))
        }
        assertThrows<NotReadable> { runBlocking { ProjectLinks.projectOf("agreement_clause", mapOf("agreement" to clause.toString()), read) } }
    }

    // Review Focus 4: la medición sube por su indicador; la entrega, por el padre que tenga; un salto ilegible es NotReadable
    @Test
    fun `el proyecto de un destino de F5 sube la cadena desde el padre que tenga`() {
        val indicator = UUID.randomUUID()
        val program = UUID.randomUUID()
        val contribution = UUID.randomUUID()
        val read: suspend (String, UUID) -> Map<String, Any?> = { name, id ->
            when (id) {
                indicator -> mapOf("social_program" to program.toString())
                program -> mapOf("project" to a.toString())
                contribution -> mapOf("project" to b.toString())
                else -> throw NotReadable(name)
            }
        }
        runBlocking {
            assertEquals(a, ProjectLinks.projectOf("program_measurement", mapOf("program_indicator" to indicator.toString()), read))
            assertEquals(b, ProjectLinks.projectOf("delivery", mapOf("commitment" to null, "social_contribution" to contribution.toString()), read))
            assertNull(ProjectLinks.projectOf("delivery", mapOf("commitment" to null, "social_contribution" to null), read))
        }
        assertThrows<NotReadable> {
            runBlocking { ProjectLinks.projectOf("program_measurement", mapOf("program_indicator" to UUID.randomUUID().toString()), read) }
        }
    }
}
