package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.BodyInserters
import sgspe.F7ApiTest
import java.util.UUID

// JOB-07 contra wasichai de verdad (workflows.md JOB-07, ai-architecture.md §13.2, especificación de F7 §3.5), con la IA activada
// para la organización sembrada (slug demo, el contexto de AiEnabledApiTest): indexa el texto de un PDF con su proyecto y su
// clasificación, resincroniza una clasificación que cambió, borra los fragmentos de una evidencia reemplazada, no indexa un archivo
// sin texto, repetir la vuelta no cambia nada y no deja ninguna ai_interaction
@TestPropertySource(properties = ["sgspe.ai.enabled-organizations=demo"])
class KnowledgeIndexJobApiTest : F7ApiTest() {
    @Autowired
    private lateinit var job: KnowledgeIndexJob

    @Autowired
    private lateinit var chunks: KnowledgeChunks

    private val word =
        "reservorio" +
            UUID
                .randomUUID()
                .toString()
                .filter { it.isLetter() }
                .take(8)
    private val created = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        runBlocking { chunks.delete(organizationId(), created) }
    }

    private fun pdf(vararg lines: String): ByteArray = TestDocuments.pdf(*lines)

    // el archivo subido por el admin para el campo file de evidence; su id
    private fun staged(
        bytes: ByteArray,
        name: String
    ): String {
        val parts = MultipartBodyBuilder().apply { part("file", bytes).filename(name) }.build()
        val body =
            client
                .post()
                .uri("/api/objects/evidence/files/file")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        return tree(body)["id"].asString()
    }

    private fun evidenceWith(
        file: String,
        classification: String = "INTERNA",
        replaces: String? = null
    ): String {
        val attributes =
            mapOf("file" to file, "evidence_type" to "ACTA", "classification" to classification, "project" to testProject()) +
                listOfNotNull(replaces?.let { "replaces" to it })
        val response = createEvidence(attributes)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["id"].asString().also { created += UUID.fromString(it) }
    }

    // una vuelta de JOB-07, como la daría su reloj
    private fun sweep(full: Boolean): KnowledgeTally? = runBlocking { job.runOnce(full) }

    private fun indexed(): Map<UUID, IndexedEvidence> = runBlocking { chunks.indexed(organizationId()) }

    private fun interactions(): Long = tree(send("GET", "/api/objects/ai_interaction/records?size=1", null, HttpStatus.OK))["totalElements"].asLong()

    @Test
    fun `indexa el texto de un PDF con su proyecto y su clasificacion, sin ai_interaction, y repetir no cambia nada`() {
        val acta = evidenceWith(staged(pdf("Acta de la asamblea de la Comunidad Ejemplo.", "Se pidió un $word para el anexo norte."), "acta.pdf"))
        val before = interactions()
        assertNotNull(sweep(full = true))
        assertEquals(IndexedEvidence(UUID.fromString(acta), UUID.fromString(testProject()), "INTERNA"), indexed()[UUID.fromString(acta)])
        val scope = ChunkScope(organizationId(), UUID.fromString(testProject()), emptySet())
        val hits = runBlocking { chunks.search(scope, word, 5) }
        assertEquals(listOf(UUID.fromString(acta) to 0), hits.map { it.evidenceId to it.chunkIndex })
        assertTrue(word in hits.single().content)
        sweep(full = true)
        assertEquals(hits, runBlocking { chunks.search(scope, word, 5) })
        assertEquals(before, interactions())
    }

    @Test
    fun `una clasificacion que cambia se resincroniza en la vuelta siguiente`() {
        val acta = evidenceWith(staged(pdf("Se pidió un $word."), "acta.pdf"))
        sweep(full = true)
        val raised =
            patch(
                "/api/sgspe/evidence/$acta",
                mapOf("attributes" to mapOf("classification" to "CONFIDENCIAL")),
                token,
                ifMatch("evidence", acta) + withReason("reclasificación de prueba")
            )
        assertEquals(HttpStatus.OK, raised.status, raised.body)
        sweep(full = false)
        assertEquals("CONFIDENCIAL", indexed().getValue(UUID.fromString(acta)).classification)
    }

    @Test
    fun `los fragmentos de una evidencia reemplazada se borran`() {
        val first = evidenceWith(staged(pdf("Primera versión: se pidió un $word."), "acta.pdf"))
        sweep(full = true)
        assertTrue(UUID.fromString(first) in indexed())
        val second = evidenceWith(staged(pdf("Versión corregida: se pidió un $word."), "acta-corregida.pdf"), replaces = first)
        sweep(full = false)
        val now = indexed()
        assertFalse(UUID.fromString(first) in now)
        assertTrue(UUID.fromString(second) in now)
    }

    @Test
    fun `un archivo sin texto no se indexa`() {
        val image = evidenceWith(staged(TestDocuments.scannedPdf(), "escaneo.pdf"))
        sweep(full = true)
        assertFalse(UUID.fromString(image) in indexed())
    }
}
