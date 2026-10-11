package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.ModelCatalog
import java.util.UUID

// los lineamientos de apoyo (decisión 3, §3.4 de la especificación de F5; RN-INV-001, 002, 005 y 006), puros
class ContributionGuidelinesTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val education = UUID.randomUUID()
    private val schools = UUID.randomUUID()
    private val roads = UUID.randomUUID()

    private fun input(
        kinds: List<String?> = listOf("BIEN"),
        type: String? = "APOYO EN ESPECIE",
        priorities: Set<UUID> = setOf(education),
        lineage: List<UUID> = listOf(schools, education),
        justification: String? = null,
        project: Map<String, Any?> = emptyMap()
    ) = ContributionGuidelines.Input(project, priorities, lineage, kinds, type, justification)

    private fun found(input: ContributionGuidelines.Input) = ContributionGuidelines.evaluate(input).map { Triple(it.code, it.field, it.blocking) }

    @Test
    fun `un aporte de bienes con tema priorizado o descendiente cumple`() {
        assertEquals(emptyList<Triple<String, String?, Boolean>>(), found(input()))
        assertEquals(emptyList<Triple<String, String?, Boolean>>(), found(input(lineage = listOf(education))))
    }

    @Test
    fun `mano de obra y dinero en una donacion bloquean sin justificacion`() {
        assertEquals(listOf(Triple("PAID_LABOR", "contribution_item", true)), found(input(kinds = listOf("BIEN", "MANO DE OBRA"))))
        assertEquals(listOf(Triple("MONETARY_DONATION", "contribution_item", true)), found(input(kinds = listOf("DINERO"), type = "DONACION")))
        // dinero en un apoyo en especie no es una donación monetaria
        assertEquals(emptyList<Triple<String, String?, Boolean>>(), found(input(kinds = listOf("DINERO"))))
        assertEquals(HttpStatus.CONFLICT, ContributionGuidelines.evaluate(input(kinds = listOf("MANO DE OBRA"))).single().status)
    }

    @Test
    fun `un tema fuera de las prioridades o sin tema es PRIORITY_NOT_MET`() {
        assertEquals(listOf(Triple("PRIORITY_NOT_MET", "topic", true)), found(input(lineage = listOf(roads))))
        assertEquals(listOf(Triple("PRIORITY_NOT_MET", "topic", true)), found(input(lineage = emptyList())))
    }

    @Test
    fun `sin temas priorizados solo se advierte, aunque no haya justificacion`() {
        assertEquals(listOf(Triple("PRIORITIES_NOT_CONFIGURED", "topic", false)), found(input(priorities = emptySet(), lineage = emptyList())))
    }

    // Review Focus 4 y consecuencia aceptada 4: la justificación cubre todos los lineamientos incumplidos a la vez
    @Test
    fun `una sola justificacion cubre todos los lineamientos incumplidos`() {
        val all =
            input(kinds = listOf("MANO DE OBRA", "DINERO"), type = "DONACION", lineage = listOf(roads), justification = "Faena comunal acordada en asamblea.")
        assertEquals(
            listOf(
                Triple("PAID_LABOR", "contribution_item", false),
                Triple("MONETARY_DONATION", "contribution_item", false),
                Triple("PRIORITY_NOT_MET", "topic", false)
            ),
            found(all)
        )
    }

    // Review Focus 4: una política apagada no se evalúa, ni siquiera como advertencia
    @Test
    fun `una politica apagada no se evalua`() {
        val off =
            mapOf(
                ContributionGuidelines.NO_PAID_LABOR to false,
                ContributionGuidelines.NON_MONETARY to false,
                ContributionGuidelines.PRIORITY to false
            )
        assertEquals(
            emptyList<Triple<String, String?, Boolean>>(),
            found(input(kinds = listOf("MANO DE OBRA", "DINERO"), type = "DONACION", priorities = emptySet(), lineage = emptyList(), project = off))
        )
    }

    @Test
    fun `los valores que nombra la regla existen en el modelo`() {
        assertTrue(ContributionGuidelines.LABOR in catalog.enumOf(Contributions.ITEM, Contributions.ITEM_KIND))
        assertTrue(ContributionGuidelines.MONEY in catalog.enumOf(Contributions.ITEM, Contributions.ITEM_KIND))
        assertTrue(ContributionGuidelines.DONATION in catalog.enumOf(Contributions.CONTRIBUTION, Contributions.TYPE))
        assertTrue(catalog.manyToMany(Contributions.PRIORITY_TOPICS)?.let { it.source == "project" && it.target == Contributions.TOPIC } == true)
    }
}
