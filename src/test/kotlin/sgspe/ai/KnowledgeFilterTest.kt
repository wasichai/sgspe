package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

// el cuarto filtro de la recuperación (ai-architecture.md §13.3, paso 4): solo los fragmentos de evidencias que la persona relee,
// descarga con la clasificación de hoy, siguen en el proyecto o en el tenant y no están reemplazadas; en el orden de la base
class KnowledgeFilterTest {
    private val project = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val elsewhere = UUID.fromString("00000000-0000-0000-0000-0000000000a2")

    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private fun hit(
        n: Int,
        index: Int = 0,
        classification: String = "INTERNA"
    ) = ChunkHit(id(n), index, project, classification, "fragmento $n.$index", 1.0 / (n + index))

    private fun evidence(
        classification: String? = "INTERNA",
        project: UUID? = this.project
    ): Map<String, Any?> = mapOf("classification" to classification, "project" to project?.toString())

    @Test
    fun `solo pasan los fragmentos de evidencias que la persona relee`() {
        val admitted = KnowledgeFilter.admit(listOf(hit(1), hit(2), hit(1, 1)), mapOf(id(1) to evidence()), project, false, emptySet(), 5)
        assertEquals(listOf(id(1) to 0, id(1) to 1), admitted.map { it.evidenceId to it.chunkIndex })
        assertEquals("fragmento 1.0", admitted.first().content)
    }

    @Test
    fun `la clasificacion de hoy manda, y sin poder leerla la evidencia cuenta como restringida`() {
        val evidences =
            mapOf(
                id(1) to evidence("CONFIDENCIAL"),
                id(2) to evidence("DATOS PERSONALES"),
                id(3) to evidence(null),
                id(4) to mapOf("project" to project.toString()),
                id(5) to evidence("PUBLICA")
            )
        val hits = (1..5).map { hit(it) }
        assertEquals(listOf(id(5)), KnowledgeFilter.admit(hits, evidences, project, false, emptySet(), 5).map { it.evidenceId })
        assertEquals((1..5).map { id(it) }, KnowledgeFilter.admit(hits, evidences, project, true, emptySet(), 5).map { it.evidenceId })
    }

    @Test
    fun `una evidencia que cambio de proyecto no pasa y una del tenant si`() {
        val evidences = mapOf(id(1) to evidence(project = elsewhere), id(2) to evidence(project = null))
        assertEquals(listOf(id(2)), KnowledgeFilter.admit(listOf(hit(1), hit(2)), evidences, project, true, emptySet(), 5).map { it.evidenceId })
    }

    @Test
    fun `una evidencia reemplazada no pasa`() {
        val evidences = mapOf(id(1) to evidence(), id(2) to evidence())
        assertEquals(listOf(id(2)), KnowledgeFilter.admit(listOf(hit(1), hit(2)), evidences, project, false, setOf(id(1)), 5).map { it.evidenceId })
    }

    @Test
    fun `los primeros que pasan, hasta el tope, en el orden de la base`() {
        val evidences = (1..8).associate { id(it) to evidence() }
        val admitted = KnowledgeFilter.admit((1..8).map { hit(it) }, evidences - id(2), project, false, emptySet(), 5)
        assertEquals(listOf(1, 3, 4, 5, 6).map { id(it) }, admitted.map { it.evidenceId })
    }

    @Test
    fun `las clasificaciones que no se piden a la base`() {
        assertEquals(setOf("CONFIDENCIAL", "DATOS PERSONALES"), KnowledgeFilter.excluded(downloadsRestricted = false))
        assertEquals(emptySet<String>(), KnowledgeFilter.excluded(downloadsRestricted = true))
    }
}
