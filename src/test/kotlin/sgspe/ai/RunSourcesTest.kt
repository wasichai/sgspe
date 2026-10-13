package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import sgspe.common.Records
import sgspe.dashboard.Dashboards
import sgspe.dashboard.IndicatorValue
import sgspe.evidence.EvidenceContent
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.time.Instant

// lo puro de las fuentes de las corridas: los valores restringidos de los asistentes de una interacción (security.md §7.9) y la
// línea de un indicador de AI-04 (dashboards.md §8: lo que no se ve llega como «no disponible», nunca como cero)
class RunSourcesTest {
    private fun participant(attributes: Map<String, Any?>) = RecordResponse("p", Instant.EPOCH, Instant.EPOCH, attributes, null, emptyMap())

    @Test
    fun `los nombres, telefonos y correos de los asistentes son restringidos`() {
        val restricted =
            RunSources.restricted(
                listOf(
                    participant(mapOf("participant_name" to "Rosa Ccori Ejemplo", "phone" to "987654321", "email" to "rosa@sgspe.test")),
                    participant(mapOf("participant_name" to " ", "represented_group" to "Junta de usuarios"))
                )
            )
        assertEquals(
            listOf(
                RestrictedValue(Marker.PERSONA, "Rosa Ccori Ejemplo"),
                RestrictedValue(Marker.TELEFONO, "987654321"),
                RestrictedValue(Marker.CORREO, "rosa@sgspe.test")
            ),
            restricted
        )
    }

    @Test
    fun `un indicador se dice con su valor y su unidad, o no disponible`() {
        assertEquals("- KPI-COM-03 Compromisos vencidos: 4", RunSources.indicatorLine(IndicatorValue("KPI-COM-03", "Compromisos vencidos", true, 4)))
        assertEquals("- KPI-COM-04 Cumplimiento: 82.5 %", RunSources.indicatorLine(IndicatorValue("KPI-COM-04", "Cumplimiento", true, 82.5, unit = "%")))
        assertEquals(
            "- KPI-STK-03 Postura: no disponible",
            RunSources.indicatorLine(IndicatorValue("KPI-STK-03", "Postura", false, unavailableReason = "FIELD_NOT_READABLE"))
        )
    }

    // un registro que la persona lee; las demás piezas fallan como las de verdad
    private fun sources(
        attributes: Map<String, Any?>,
        evidence: EvidenceContent = mock(EvidenceContent::class.java) { throw AssertionError("no se abre el archivo") },
        dashboards: Dashboards = mock(Dashboards::class.java) { throw AssertionError("no se leen tableros") }
    ) = RunSources(
        mock(Records::class.java) { invocation ->
            if (invocation.method.name ==
                "get"
            ) {
                RecordResponse(RunFixtures.PROJECT.toString(), Instant.EPOCH, Instant.EPOCH, attributes, null, emptyMap())
            } else {
                emptyList<Any>()
            }
        },
        evidence,
        dashboards,
        TextExtraction(),
        RunFixtures.properties()
    )

    @Test
    fun `una evidencia sin archivo es 422 AI_NO_TEXT, no el 404 del archivo`() {
        val noFile = mock(EvidenceContent::class.java) { throw NotFoundException("Archivo no encontrado") }
        val refused =
            assertThrows<WasichaiException> {
                runBlocking { sources(mapOf("project" to RunFixtures.PROJECT.toString()), evidence = noFile).evidenceText(RunFixtures.EVIDENCE) }
            }
        assertEquals(422, refused.status.value())
        assertEquals("target" to "AI_NO_TEXT", refused.violations.single().let { it.field to it.message })
    }

    @Test
    fun `un tablero que no se puede leer llega como no disponible, no se calla`() {
        val failing = mock(Dashboards::class.java) { throw NotFoundException("Tablero no disponible") }
        val source = runBlocking { sources(mapOf("code" to "P-01", "name" to "Proyecto Ejemplo"), dashboards = failing).projectSummary(RunFixtures.PROJECT) }
        for (board in RunSources.SUMMARY_DASHBOARDS) assertTrue("- Tablero $board: no disponible" in source.text.lines(), source.text)
    }
}
