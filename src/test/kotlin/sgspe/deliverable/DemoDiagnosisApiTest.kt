package sgspe.deliverable

import kotlinx.coroutines.reactive.awaitSingle
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import wasichai.files.FileService
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

// el conjunto de demostración de model/demo/ (épica #102): load_demo.py lo carga por REST como el admin y los usuarios
// ficticios, una segunda corrida no crea nada, y el T1 que sale de él llega a LISTO con las tablas numeradas y las cifras del
// conjunto. con SGSPE_DEMO_DOCX_OUT, la prueba deja en esa ruta el DOCX interno del borrador (para revisarlo a mano)
class DemoDiagnosisApiTest : F8ApiTest() {
    @Autowired
    private lateinit var fileService: FileService

    // los bytes de un campo FILE del entregable, leídos como el admin, como la plataforma los guardó
    private fun download(
        deliverable: String,
        field: String
    ): ByteArray =
        asUser(admin()) {
            val download = fileService.open(Deliverables.DELIVERABLE, UUID.fromString(deliverable), field)
            val joined = DataBufferUtils.join(download.content).awaitSingle()
            ByteArray(joined.readableByteCount()).also { joined.read(it) }.also { DataBufferUtils.release(joined) }
        }

    // cuatro registros de una colección a la vez: por el túnel, una carga en serie no entra en SCRIPT_TIMEOUT_SECONDS
    private val demo = listOf("model/load_demo.py", "--users-password", PASSWORD, "--workers", "4")

    @Test
    fun `el conjunto de demostracion carga dos veces y su T1 llega a LISTO`() {
        val indicators = runScript(listOf("model/load_indicators.py"))
        assertEquals(0, indicators.exit, "load_indicators.py\n${indicators.output}")
        val load = runScript(demo)
        assertEquals(0, load.exit, "primera carga\n${load.output}")
        val again = runScript(demo)
        assertEquals(0, again.exit, "segunda carga\n${again.output}")
        assertTrue(again.output.contains("done: 0 created"), "la segunda carga creó registros\n${again.output}")

        val found =
            tree(send("GET", "/api/objects/deliverable/records?document_code=DEMO-T1-001", null, HttpStatus.OK))["content"].values().toList()
        assertEquals(1, found.size, "entregables DEMO-T1-001: ${found.size}\n${load.output}")
        val deliverable = found.single()["id"].asString()
        val requested = requestDraft(deliverable)
        assertEquals(HttpStatus.ACCEPTED, requested.status, requested.body)
        generate()
        val stored = record("deliverable", deliverable)["attributes"]
        assertEquals(Deliverables.READY, stored[Deliverables.STATUS].asString(), stored.toString())

        val bytes = download(deliverable, Deliverables.DRAFT_INTERNAL_DOCX)
        System.getenv("SGSPE_DEMO_DOCX_OUT")?.takeIf { it.isNotBlank() }?.let { Files.write(Path.of(it), bytes) }
        XWPFDocument(ByteArrayInputStream(bytes)).use { docx ->
            val text = docx.paragraphs.joinToString("\n") { it.text }
            assertTrue(docx.tables.size >= 30, "tablas: ${docx.tables.size}")
            assertTrue(text.contains("Caserío Huaylla Alto"), "sin la localidad del AIDS en el texto")
            // la narrativa humana de la introducción llega al documento
            assertTrue(text.contains("El presente documento constituye el diagnóstico social"), "sin la narrativa de la introducción")
            // Tabla 3.1: las 72 entrevistas del estudio, sumadas por localidad (la entrevista es la penúltima celda de cada fila)
            val fieldwork =
                docx.tables.firstOrNull { t ->
                    t.rows
                        .first()
                        .tableCells
                        .map { it.text }
                        .containsAll(listOf("Localidad", "Entrevistas", "Fichas de observación"))
                }
            assertNotNull(fieldwork, "sin la tabla de entrevistas y fichas")
            val rows = fieldwork!!.rows.drop(1)
            val interviews =
                rows.sumOf { r ->
                    r.tableCells[r.tableCells.size - 2]
                        .text
                        .trim()
                        .toInt()
                }
            val sheets =
                rows.sumOf { r ->
                    r.tableCells
                        .last()
                        .text
                        .trim()
                        .toInt()
                }
            assertEquals(72, interviews, rows.joinToString("\n") { r -> r.tableCells.joinToString(" | ") { it.text } })
            assertEquals(4, sheets)
        }
    }
}
