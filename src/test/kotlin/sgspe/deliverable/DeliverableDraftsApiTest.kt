package sgspe.deliverable

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import sgspe.privacy.Block
import sgspe.privacy.CanonicalJson
import wasichai.files.FileService
import wasichai.files.StoredFileRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// especificación de F8 §3.3 y §3.5, §5 (DeliverableDraftsApiTest), deliverables.md §8.2 y §13.3 («Almacenamiento»): el pedido,
// JOB-05 de punta a punta contra wasichai real con los dobles de PDF y mapas, y los siete archivos adjuntos como la plataforma
// (PlatformDraftFiles, UPS-31 resuelta en wasichai 1.3.0)
class DeliverableDraftsApiTest : F8ApiTest() {
    @Autowired
    private lateinit var fileService: FileService

    @Autowired
    private lateinit var storedFiles: StoredFileRepository

    private fun codes(response: Response): List<String> = tree(response.body)["errors"].values().map { it["message"].asString() }

    private fun draft(id: String) = tree(send("GET", "/api/sgspe/deliverables/$id/draft", null, HttpStatus.OK))

    @Test
    fun `un pedido fuera de draft u observed es 409 y sin parametros es 422 por campo`() {
        val project = testProject()
        val bare = deliverable(project)
        val missing = requestDraft(bare)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, missing.status, missing.body)
        assertEquals(setOf("community", "project.working_crs"), tree(missing.body)["errors"].values().map { it["field"].asString() }.toSet())
        assertTrue(codes(missing).all { it == "DELIVERABLE_PARAMETERS_MISSING" })
        val approved = approvedDeliverable(project)
        val late = requestDraft(approved)
        assertEquals(HttpStatus.CONFLICT, late.status, late.body)
        assertEquals(listOf("STATE_MISMATCH"), codes(late))
    }

    // plan 8.2c (decisión 15; Review Focus 5): T1 se genera desde este plan; exige estudio, AIDS y CRS de trabajo, y el AIDS
    // aprobado
    @Test
    fun `un T1 sin estudio o con un AIDS sin aprobar es 422 por campo`() {
        val bare = requestDraft(deliverable(testProject(), SOCIAL_DIAGNOSIS))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, bare.status, bare.body)
        assertEquals(setOf("study", "area_of_influence", "project.working_crs"), tree(bare.body)["errors"].values().map { it["field"].asString() }.toSet())
        assertTrue(codes(bare).all { it == "DELIVERABLE_PARAMETERS_MISSING" })
        val scenario = reportScenario()
        val draftArea =
            createWith(
                "area_of_influence",
                mapOf("project" to scenario.project, "area_type" to "AIDS", "version_number" to 2),
                mapOf("boundary" to sgspe.SyntheticGeo.multiSquare(sgspe.SyntheticGeo.ORIGIN.first, sgspe.SyntheticGeo.ORIGIN.second, 5000.0))
            )
        val pending = requestDraft(deliverable(scenario.project, SOCIAL_DIAGNOSIS, mapOf("study" to scenario.study, "area_of_influence" to draftArea)))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, pending.status, pending.body)
        assertEquals(listOf("area_of_influence"), tree(pending.body)["errors"].values().map { it["field"].asString() })
        assertEquals(HttpStatus.ACCEPTED, requestDraft(scenario.socialDiagnosis).status)
    }

    @Test
    fun `JOB-05 deja LISTO con los siete archivos y un borrador nuevo los reemplaza con la letra siguiente`() {
        val id = requestedFieldReport()
        assertTrue(draft(id)["pending"].asBoolean())
        assertTrue(generate() >= 1)
        val first = record("deliverable", id)["attributes"]
        assertEquals(Deliverables.READY, first[Deliverables.STATUS].asString(), "$first")
        assertEquals("A", first[Deliverables.DRAFT_REVISION].asString())
        for (field in Deliverables.DRAFT_FILES) assertTrue(first[field]?.has("sha256") == true, field)
        assertEquals(first[Deliverables.MODEL_SHA256].asString(), first[Deliverables.DRAFT_MODEL]["sha256"].asString())
        val status = draft(id)
        assertEquals(false, status["pending"].asBoolean())
        assertEquals(
            first[Deliverables.CANDIDATE_EXTERNAL_PDF]["sha256"].asString(),
            status["levels"]
                .values()
                .single {
                    it["level"].asString() == "EXTERNO"
                }["candidateSha256"]
                .asString()
        )
        assertTrue(fakePdf.requests.any { it.pdfa } && fakePdf.requests.any { !it.pdfa })
        assertEquals(HttpStatus.ACCEPTED, requestDraft(id).status)
        generate()
        val second = record("deliverable", id)["attributes"]
        assertEquals("B", second[Deliverables.DRAFT_REVISION].asString())
        assertNotEquals(first[Deliverables.DRAFT_INTERNAL_PDF]["id"].asString(), second[Deliverables.DRAFT_INTERNAL_PDF]["id"].asString())
    }

    @Test
    fun `sin conversor queda FALLIDO con PDF_CONVERTER_UNAVAILABLE y un mapa fallido deja LISTO con MAP_FAILED`() {
        val failed = requestedFieldReport()
        fakePdf.unavailable = true
        try {
            generate()
        } finally {
            fakePdf.unavailable = false
        }
        assertEquals(Deliverables.FAILED, record("deliverable", failed)["attributes"][Deliverables.STATUS].asString())
        assertEquals(listOf(Deliverables.PDF_CONVERTER_UNAVAILABLE), draft(failed)["warnings"].values().map { it["code"].asString() })
        val mapless = requestedFieldReport()
        fakeMaps.failing = true
        try {
            generate()
        } finally {
            fakeMaps.failing = false
        }
        assertEquals(Deliverables.READY, record("deliverable", mapless)["attributes"][Deliverables.STATUS].asString())
        val warnings = draft(mapless)["warnings"].values().map { it["code"].asString() to it["section"]?.asString() }
        assertTrue(Deliverables.MAP_FAILED to "portada" in warnings, "$warnings")
    }

    // revisión final del plan 8.2b, I3: un StackOverflowError o un OutOfMemoryError del intento no lo dejan EN PROCESO para
    // siempre: queda FALLIDO con GENERATION_FAILED y la vuelta siguiente no lo retoma
    @Test
    fun `un error de la maquina virtual deja FALLIDO con GENERATION_FAILED y no se reintenta`() {
        for (error in listOf(StackOverflowError(), OutOfMemoryError("doble"))) {
            val failed = requestedFieldReport()
            fakePdf.error = error
            try {
                generate()
            } finally {
                fakePdf.error = null
            }
            assertEquals(Deliverables.FAILED, record("deliverable", failed)["attributes"][Deliverables.STATUS].asString())
            assertEquals(listOf(Deliverables.GENERATION_FAILED), draft(failed)["warnings"].values().map { it["code"].asString() })
            assertFalse(draft(failed)["pending"].asBoolean())
        }
    }

    @Test
    fun `un pedido EN PROCESO viejo se retoma y uno reciente no`() {
        val recent = requestedFieldReport()
        platformUpdate("deliverable", recent, mapOf(Deliverables.STATUS to Deliverables.RUNNING, Deliverables.GENERATED_AT to Instant.now().toString()))
        val old = requestedFieldReport()
        platformUpdate(
            "deliverable",
            old,
            mapOf(
                Deliverables.STATUS to Deliverables.RUNNING,
                Deliverables.GENERATED_AT to Instant.now().minus(20, ChronoUnit.MINUTES).toString()
            )
        )
        generate()
        assertEquals(Deliverables.RUNNING, record("deliverable", recent)["attributes"][Deliverables.STATUS].asString())
        assertEquals(Deliverables.READY, record("deliverable", old)["attributes"][Deliverables.STATUS].asString())
    }

    @Test
    fun `nadie escribe draft_ ni current_version por la API generica, y reenviar los descriptores leidos no es cambiarlos`() {
        val id = deliverable(testProject())
        readyDraft(id)
        val resent = edit("deliverable", id, mapOf("title" to "Informe de campo y avance corregido"), headers = withReason("título"))
        assertEquals(HttpStatus.OK, resent.status, resent.body)
        assertEquals(Deliverables.READY, record("deliverable", id)["attributes"][Deliverables.STATUS].asString())
        val other = stage(admin(), "deliverable", Deliverables.DRAFT_INTERNAL_PDF, PDF, "otro.pdf", "application/pdf")
        val swapped = edit("deliverable", id, mapOf(Deliverables.DRAFT_INTERNAL_PDF to other), headers = withReason("cambio"))
        assertEquals(HttpStatus.BAD_REQUEST, swapped.status, swapped.body)
        assertEquals(listOf("DERIVED_FIELD"), codes(swapped))
        val status = edit("deliverable", id, mapOf(Deliverables.STATUS to Deliverables.FAILED), headers = withReason("cambio"))
        assertEquals(listOf("DERIVED_FIELD"), codes(status))
        assertEquals(UUID.fromString(id).toString(), record("deliverable", id)["id"].asString())
    }

    @Test
    fun `una plantilla inexistente es 422 al pedir, y si falta al generar deja FALLIDO una sola vez`() {
        val scenario = reportScenario()
        platformUpdate("deliverable", scenario.fieldReport, mapOf(Deliverables.TEMPLATE_KEY to "no-existe"))
        val refused = requestDraft(scenario.fieldReport)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, refused.status, refused.body)
        assertEquals(listOf(Deliverables.TEMPLATE_UNAVAILABLE), codes(refused))
        platformUpdate("deliverable", scenario.fieldReport, mapOf(Deliverables.TEMPLATE_KEY to null))
        val id = requestedFieldReport(scenario)
        platformUpdate("deliverable", id, mapOf(Deliverables.TEMPLATE_KEY to "no-existe"))
        generate()
        val failed = record("deliverable", id)
        assertEquals(Deliverables.FAILED, failed["attributes"][Deliverables.STATUS].asString())
        assertEquals(listOf(Deliverables.TEMPLATE_UNAVAILABLE), draft(id)["warnings"].values().map { it["code"].asString() })
        assertEquals(false, draft(id)["pending"].asBoolean())
        generate()
        assertEquals(failed["updatedAt"].asString(), record("deliverable", id)["updatedAt"].asString())
    }

    // plan 8.2c (Review Focus 3): T1 con los dobles de PDF y mapas, contra wasichai real: la Tabla 9.5 con el lector de la
    // plataforma, el capítulo 5 con el perfil del escenario y los dos mapas pedidos con su encuadre
    @Test
    fun `un T1 sale LISTO con sus secciones, su indice y sus dos mapas pedidos`() {
        val scenario = reportScenario()
        assertEquals(HttpStatus.ACCEPTED, requestDraft(scenario.socialDiagnosis).status)
        generate()
        val attributes = record("deliverable", scenario.socialDiagnosis)["attributes"]
        assertEquals(Deliverables.READY, attributes[Deliverables.STATUS].asString(), "$attributes")
        val bytes =
            asUser(admin()) {
                val download = fileService.open(Deliverables.DELIVERABLE, UUID.fromString(scenario.socialDiagnosis), Deliverables.DRAFT_MODEL)
                val joined = DataBufferUtils.join(download.content).awaitSingle()
                ByteArray(joined.readableByteCount()).also { joined.read(it) }.also { DataBufferUtils.release(joined) }
            }
        val model = CanonicalJson.read(bytes)
        assertEquals(SocialDiagnosisType().sections.map { it.key }, model.sections.map { it.key })
        assertTrue(
            model.sections
                .single { it.key == "indice" }
                .blocks
                .first() is Block.Toc
        )
        val population =
            model.sections
                .single { it.key == "poblacion" }
                .blocks
                .filterIsInstance<Block.Table>()
                .first()
        assertEquals(
            listOf("Caserío Ejemplo Alto", "120"),
            population.rows
                .single()
                .take(2)
                .map { it.text }
        )
        assertEquals(
            13,
            model.sections
                .single { it.key == "monitoreo" }
                .blocks
                .filterIsInstance<Block.Table>()
                .single()
                .rows.size
        )
        // un pedido por mapa, en el orden del documento, cada uno con su encuadre (#83: no basta con el conjunto de claves)
        val maps = fakeMaps.requests.filter { it.project.toString() == scenario.project }
        assertEquals(listOf(DiagnosisStudy.AREAS_MAP, DiagnosisAnnexes.ANNEX_MAP), maps.map { it.key })
        val areas = maps.single { it.key == DiagnosisStudy.AREAS_MAP }
        assertEquals(MapFrameKind.AREAS, areas.frame)
        assertTrue(areas.areas.isNotEmpty() && !areas.landscape, "$areas")
        val annex = maps.single { it.key == DiagnosisAnnexes.ANNEX_MAP }
        assertEquals(MapFrameKind.INTEREST_AND_CONCESSIONS, annex.frame)
        assertTrue(annex.landscape)
    }

    // menor del plan 8.2b: lo que sube JOB-05 lo sube la plataforma (UPS-31): sin persona y con la corrida que lo adjuntó
    @Test
    fun `los siete archivos del borrador los sube la plataforma, sin persona y con su corrida`() {
        val id = requestedFieldReport()
        generate()
        val attributes = record("deliverable", id)["attributes"]
        for (field in Deliverables.DRAFT_FILES) {
            val stored = runBlocking { storedFiles.find(organizationId(), UUID.fromString(attributes[field]["id"].asString())) }
            assertNotNull(stored, field)
            assertNull(stored!!.createdBy, field)
            assertNotNull(stored.platformRun, field)
        }
    }
}
