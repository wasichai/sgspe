package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import sgspe.F6QueryApiTest
import kotlin.random.Random

// el tope de elementos de las capas de gestión (sgspe.territory.layers.max-features, bajado a 5): se comparte entre las
// fuentes en su orden y entre los registros con geometría propia y los que caen a su localidad, sin repetir elementos, y
// `truncated` solo es verdadero si había más
@TestPropertySource(properties = ["sgspe.territory.layers.max-features=5"])
class ManagementLayersCapApiTest : F6QueryApiTest() {
    @Test
    fun `el tope se comparte entre las fuentes y las caidas a la localidad, sin repetir elementos`() {
        val y = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)
        val crowded = runBlocking { utmProject() }
        val exact = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        val placed =
            createWith(
                "incident",
                mapOf(
                    "project" to crowded.toString(),
                    "code" to uniqueName("IN-"),
                    "incident_type" to "BLOQUEO DE VIA",
                    "occurred_at" to "2026-10-06T13:00:00Z",
                    "place" to "Acceso a la plataforma",
                    "description" to "Bloqueo del acceso.",
                    "immediate_response" to "Diálogo en el lugar.",
                    "consequences" to "Dos horas sin acceso."
                ),
                mapOf("location" to at(100.0, y))
            )
        val incidents = listOf(placed) + (1..2).map { incident(crowded.toString(), attributes = mapOf("locality" to place.toString())) }
        repeat(3) { warningSignal(crowded.toString(), attributes = mapOf("locality" to place.toString())) }
        repeat(2) { incident(exact.toString(), attributes = mapOf("locality" to place.toString())) }
        repeat(3) { warningSignal(exact.toString(), attributes = mapOf("locality" to place.toString())) }

        val over = get("/api/sgspe/territory/layers/LYR-22?project=$crowded")
        val ids = over["features"].values().map { it["id"].asString() }
        assertEquals(5, ids.size)
        assertEquals(5, ids.toSet().size, "elementos repetidos: $ids")
        assertEquals(true, over["truncated"].asBoolean())
        // las fuentes van en su orden: los tres incidentes primero
        assertEquals(
            incidents.toSet(),
            over["features"]
                .values()
                .take(3)
                .map { it["properties"]["__id"].asString() }
                .toSet()
        )

        val fits = get("/api/sgspe/territory/layers/LYR-22?project=$exact")
        assertEquals(5, fits["features"].size())
        assertEquals(false, fits["truncated"].asBoolean())
    }
}
