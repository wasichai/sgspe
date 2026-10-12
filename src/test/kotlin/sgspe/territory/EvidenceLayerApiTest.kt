package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID
import kotlin.random.Random

// LYR-24 (gis.md §2.2, RF-DOC-003, UPS-24): las evidencias en su ubicación, sin el descriptor del archivo (la descarga pasa
// por EvidenceContent con DOWNLOAD_RESTRICTED) y con __downloadRestricted; el periodo por captured_at
class EvidenceLayerApiTest : F6QueryApiTest() {
    private fun evidenceAt(
        project: UUID,
        classification: String,
        location: Map<String, Any?>
    ): String {
        val attributes =
            mapOf(
                "file" to stagedFile("evidence", "file"),
                "evidence_type" to "FOTO",
                "classification" to classification,
                "project" to project.toString(),
                "captured_at" to "2026-10-03T15:00:00Z"
            )
        val response =
            exchange(
                "POST",
                "/api/sgspe/evidence",
                mapOf("attributes" to attributes, "geometries" to mapOf("location" to location), "links" to emptyList<Any>())
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["id"].asString()
    }

    @Test
    fun `LYR-24 ubica las evidencias sin el nombre ni la huella del archivo y dice cuales tienen la descarga restringida`() {
        val y = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)
        val project = runBlocking { utmProject() }
        val restricted = evidenceAt(project, "CONFIDENCIAL", at(0.0, y))
        val open = evidenceAt(project, "INTERNA", at(100.0, y))
        val sha = record("evidence", restricted)["attributes"]["file"]["sha256"].asString()
        for (account in listOf(token, member("ANALYST", project.toString()).token)) {
            val raw = send("GET", "/api/sgspe/territory/layers/LYR-24?project=$project", null, HttpStatus.OK, account)
            val features = tree(raw)["features"].values().associateBy { it["properties"]["__id"].asString() }
            assertEquals(setOf(restricted, open), features.keys)
            assertTrue(features.getValue(restricted)["properties"]["__downloadRestricted"].asBoolean())
            assertFalse(features.getValue(open)["properties"]["__downloadRestricted"].asBoolean())
            assertTrue(features.values.none { it["properties"].has("file") }, raw)
            assertFalse(raw.contains("prueba.pdf") || raw.contains(sha), raw)
            assertEquals("CONFIDENCIAL", features.getValue(restricted)["properties"]["classification"].asString())
        }
        assertEquals(2, get("/api/sgspe/territory/layers/LYR-24?project=$project&periodStart=2026-10-01&periodEnd=2026-10-31")["features"].size())
        assertEquals(0, get("/api/sgspe/territory/layers/LYR-24?project=$project&periodStart=2026-11-01&periodEnd=2026-11-30")["features"].size())
    }
}
