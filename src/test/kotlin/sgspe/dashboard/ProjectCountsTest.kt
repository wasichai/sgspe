package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

// B-03 del rediseño (wasichai/sgspe#161): las claves y las sumas de los conteos de un proyecto, las mismas que armaba
// useConteosDelProyecto del portal con una lectura size=1 por pestaña, clase y objeto
class ProjectCountsTest {
    private val project = UUID.randomUUID()

    // cada lectura pedida, como (objeto, filtros), y su total: 10 por objeto y 1 con el filtro de vencidos
    private val asked = mutableListOf<Pair<String, Map<String, String>>>()

    private fun counts(readable: (String) -> Boolean = { true }): Map<String, Long?> =
        runBlocking {
            ProjectCounts.compute(project, { readable(it) }) { objectName, filters ->
                asked += objectName to filters
                if ("deadline_status" in filters) 1L else 10L
            }
        }

    @Test
    fun `las claves son las de las pestañas del proyecto en el portal`() {
        assertEquals(
            listOf(
                "stakeholders:total",
                "engagements:total",
                "commitments:total",
                "commitments:accion",
                "attention:total",
                "attention:accion",
                "risks:total",
                "programs:total",
                "documents:total"
            ),
            counts().keys.toList()
        )
    }

    @Test
    fun `cada lectura lleva el proyecto y la de accion los vencidos`() {
        val result = counts()
        assertEquals(10L, result["stakeholders:total"])
        assertEquals(1L, result["commitments:accion"])
        assertEquals(
            mapOf("project" to project.toString(), "deadline_status" to "VENCIDO"),
            asked.first { it.first == "commitment" && it.second.size == 2 }.second
        )
        assertEquals(mapOf("project" to project.toString()), asked.first { it.first == "evidence" }.second)
    }

    @Test
    fun `atencion suma las tres QCS`() {
        val result = counts()
        assertEquals(30L, result["attention:total"])
        assertEquals(3L, result["attention:accion"])
    }

    @Test
    fun `un objeto que no se lee da null, nunca cero, y no se cuenta`() {
        val result = counts { it.substringBefore('.') != "social_risk" }
        assertNull(result["risks:total"])
        assertEquals(10L, result["programs:total"])
        assertEquals(emptyList<String>(), asked.filter { it.first == "social_risk" }.map { it.first })
    }

    @Test
    fun `atencion suma solo las QCS que se leen, y null si no lee ninguna`() {
        assertEquals(20L, counts { it.substringBefore('.') != "inquiry" }["attention:total"])
        assertNull(counts { it.substringBefore('.') !in setOf("grievance", "inquiry", "stakeholder_request") }["attention:total"])
    }

    @Test
    fun `sin el campo del filtro legible, la clave es null`() {
        val result = counts { it != "commitment.deadline_status" }
        assertEquals(10L, result["commitments:total"])
        assertNull(result["commitments:accion"])
    }
}
