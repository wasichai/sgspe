package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// GET /api/sgspe/deliverable-types (api.md §4.12), sin base: los tipos registrados en un orden estable, con sus secciones por
// orden y lo que cada una declara, para que el portal no copie nada del backend
class DeliverableTypeCatalogTest {
    private fun type(
        name: String,
        vararg sections: SectionSpec
    ) = object : DeliverableType {
        override val name = name
        override val version = 3
        override val sections = sections.toList()
        override val reads = emptyMap<String, Set<String>>()

        override suspend fun missingParameters(
            deliverable: Map<String, Any?>,
            project: Map<String, Any?>
        ) = emptyList<String>()

        override suspend fun assemble(
            input: AssemblyInput,
            source: ReportSource
        ): Assembled = error("no se ensambla")
    }

    @Test
    fun `cada tipo sale con su version y sus secciones por orden, y los tipos por nombre`() {
        val b = SectionSpec("b", "Segunda", 2, "NARRATIVA", false, "Guía b", true)
        val a = SectionSpec("a", "Primera", 1, "PORTADA", true, "", false)
        val listed = DeliverableTypeCatalog.compose(listOf(type("ZETA", b, a), type("ALFA")))
        assertEquals(listOf("ALFA", "ZETA"), listed.map { it.type })
        assertEquals(
            DeliverableTypeView(
                "ZETA",
                3,
                listOf(
                    SectionView("a", "Primera", 1, "PORTADA", required = true, assisted = false, guide = ""),
                    SectionView("b", "Segunda", 2, "NARRATIVA", required = false, assisted = true, guide = "Guía b")
                )
            ),
            listed[1]
        )
    }

    @Test
    fun `los tipos de F8 salen con T1 de 52 secciones y sus secciones con IA`() {
        val listed = DeliverableTypeCatalog.compose(listOf(FieldReportType(), SocialDiagnosisType()))
        assertEquals(listOf(SocialDiagnosisType.NAME, FieldReportType.NAME), listed.map { it.type })
        val t1 = listed.first()
        assertEquals(listOf(2, 2), listed.map { it.version })
        assertEquals(52, t1.sections.size)
        assertEquals((1..52).toList(), t1.sections.map { it.order })
        assertEquals(SocialDiagnosisType().sections.filter { it.assisted }.map { it.key }, t1.sections.filter { it.assisted }.map { it.key })
        assertEquals(FieldReportType().sections.size, listed[1].sections.size)
    }
}
