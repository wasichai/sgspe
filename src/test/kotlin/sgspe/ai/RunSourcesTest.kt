package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.dashboard.IndicatorValue
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
}
