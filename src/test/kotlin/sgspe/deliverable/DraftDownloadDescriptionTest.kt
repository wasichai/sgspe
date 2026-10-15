package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// revisión final del plan 8.2b, I4: la fila DESCARGA de un borrador nombra el archivo servido por su SHA-256 (el ETag) y,
// fuera de LISTO, no le atribuye la letra ni el instante del intento nuevo, porque los archivos siguen siendo los del último
// borrador LISTO
class DraftDownloadDescriptionTest {
    private val sha = "a".repeat(64)

    @Test
    fun `con LISTO nombra la letra, el intento y el SHA-256 del archivo servido`() {
        val text =
            DeliverableContent.draftDescription(
                mapOf(Deliverables.STATUS to Deliverables.READY, Deliverables.DRAFT_REVISION to "A", Deliverables.GENERATED_AT to "2026-10-15T15:00:00Z"),
                sha
            )
        assertEquals("borrador A del intento del 2026-10-15T15:00:00Z, archivo con SHA-256 $sha", text)
    }

    @Test
    fun `fuera de LISTO dice que el archivo es del ultimo borrador LISTO, sin la letra del intento nuevo`() {
        for (status in listOf(Deliverables.FAILED, Deliverables.RUNNING, Deliverables.QUEUED)) {
            val text =
                DeliverableContent.draftDescription(
                    mapOf(Deliverables.STATUS to status, Deliverables.DRAFT_REVISION to "B", Deliverables.GENERATED_AT to "2026-10-15T16:00:00Z"),
                    sha
                )
            assertFalse("borrador B" in text, text)
            assertTrue("último borrador LISTO" in text && status in text && sha in text, text)
        }
    }
}
