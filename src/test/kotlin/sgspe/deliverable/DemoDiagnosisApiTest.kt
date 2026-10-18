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

    // cuatro registros de una colección a la vez: por el túnel, una carga en serie no entra en SCRIPT_TIMEOUT_SECONDS. el
    // cargador solo respeta las dependencias declaradas con @clave (docs/develop/README.md §5): una regla de core entre
    // registros de una misma colección que el conjunto no nombra quedaría en carrera y pediría --workers 1. unos 22 minutos
    // por el túnel (1357 s el 2026-10-18)
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
            // #80: la ubicación sale de las unidades del conjunto (090000, 090400 y 090410, con el formato de load_ubigeo.py) y 4.2 va por microcuenca
            assertTrue(text.contains("Ubicación: distrito de SAN JUAN, provincia de CASTROVIRREYNA, región HUANCAVELICA"), "sin la ubicación")
            assertTrue(text.contains("Apoyos y oposición: "), "sin los apoyos y la oposición")
            for (title in listOf("Miembros del AIIS en la Microcuenca Ichuyacu", "Miembros del AIIS en la Microcuenca Pallcamayo", "Otros miembros del AIIS")) {
                assertTrue(text.contains(title), "sin la tabla «$title» de 4.2")
            }
            // Tabla 3.1: las 72 entrevistas y las 4 fichas del estudio, sumadas por localidad. cada columna se busca por su
            // encabezado y se cuenta desde la derecha: una fila que sigue una celda combinada de «Ámbito» puede traer una
            // celda menos a la izquierda
            val fieldwork =
                docx.tables.firstOrNull { t ->
                    t.rows
                        .first()
                        .tableCells
                        .map { it.text }
                        .containsAll(listOf("Localidad", "Entrevistas", "Fichas de observación"))
                }
            assertNotNull(fieldwork, "sin la tabla de entrevistas y fichas")
            val header =
                fieldwork!!
                    .rows
                    .first()
                    .tableCells
                    .map { it.text }
            val rows = fieldwork.rows.drop(1)

            fun total(column: String): Int {
                val fromRight = header.size - header.indexOf(column)
                return rows.sumOf { r ->
                    r.tableCells[r.tableCells.size - fromRight]
                        .text
                        .trim()
                        .toInt()
                }
            }
            val interviews = total("Entrevistas")
            val sheets = total("Fichas de observación")
            assertEquals(72, interviews, rows.joinToString("\n") { r -> r.tableCells.joinToString(" | ") { it.text } })
            assertEquals(4, sheets)
        }
    }
}
