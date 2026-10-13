package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import wasichai.test.WasichaiIntegrationTest
import java.util.UUID

// KnowledgeChunks contra PostgreSQL de verdad (ai-architecture.md §13.2 y §13.3): reemplazar es idempotente y numera desde cero;
// la búsqueda filtra por tenant, proyecto (más el tenant) y clasificación, ordena por relevancia y no se rompe con una consulta
// rara; resincronizar, borrar y conservar tocan solo su organización. en dos organizaciones inventadas que borra al terminar
class KnowledgeChunksApiTest : WasichaiIntegrationTest() {
    @Autowired
    private lateinit var chunks: KnowledgeChunks

    private val organization = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val project = UUID.randomUUID()
    private val elsewhere = UUID.randomUUID()

    @AfterEach
    fun cleanUp() {
        runBlocking {
            chunks.retain(organization, emptyList())
            chunks.retain(other, emptyList())
        }
    }

    private fun search(
        query: String,
        excluded: Set<String> = emptySet(),
        organizationId: UUID = organization,
        limit: Int = 25
    ): List<ChunkHit> = runBlocking { chunks.search(ChunkScope(organizationId, project, excluded), query, limit) }

    private fun replace(
        evidence: UUID,
        vararg contents: String,
        projectId: UUID? = project,
        classification: String = "INTERNA",
        organizationId: UUID = organization
    ) = runBlocking { chunks.replace(organizationId, evidence, projectId, classification, contents.toList()) }

    @Test
    fun `reemplazar numera desde cero y deja solo los fragmentos nuevos`() {
        val evidence = UUID.randomUUID()
        replace(evidence, "La asamblea pidió reparar el canal.", "Se acordó una segunda reunión.")
        replace(evidence, "El acta corregida pide reparar los canales de riego.")
        assertEquals(mapOf(evidence to IndexedEvidence(evidence, project, "INTERNA")), runBlocking { chunks.indexed(organization) })
        val hits = search("canal")
        assertEquals(listOf(evidence to 0), hits.map { it.evidenceId to it.chunkIndex })
        assertEquals("El acta corregida pide reparar los canales de riego.", hits.single().content)
        assertEquals(emptyList<ChunkHit>(), search("segunda reunión"))
    }

    @Test
    fun `la busqueda admite el tenant, el proyecto y el tenant sin proyecto, y no las clasificaciones excluidas`() {
        val own = UUID.randomUUID()
        val tenantWide = UUID.randomUUID()
        val otherProject = UUID.randomUUID()
        val confidential = UUID.randomUUID()
        val foreign = UUID.randomUUID()
        replace(own, "Reservorio del anexo norte")
        replace(tenantWide, "Política de reservorio de la empresa", projectId = null)
        replace(otherProject, "Reservorio de otro proyecto", projectId = elsewhere)
        replace(confidential, "Reservorio en el acta confidencial", classification = "CONFIDENCIAL")
        replace(foreign, "Reservorio de otra organización", organizationId = other)
        assertEquals(setOf(own, tenantWide, confidential), search("reservorio").map { it.evidenceId }.toSet())
        assertEquals(setOf(own, tenantWide), search("reservorio", setOf("CONFIDENCIAL", "DATOS PERSONALES")).map { it.evidenceId }.toSet())
        assertEquals(listOf(foreign), search("reservorio", organizationId = other).map { it.evidenceId })
    }

    @Test
    fun `los mas relevantes primero y hasta el tope`() {
        val many = UUID.randomUUID()
        val once = UUID.randomUUID()
        replace(many, "Canal de riego: el canal, el canal y otra vez el canal del anexo.")
        replace(once, "Un canal.")
        val hits = search("canal anexo")
        assertEquals(listOf(many), hits.map { it.evidenceId })
        assertEquals(listOf(many, once), search("canal").map { it.evidenceId })
        assertEquals(1, search("canal", limit = 1).size)
    }

    @Test
    fun `una consulta rara no rompe y una sin palabras no encuentra nada`() {
        replace(UUID.randomUUID(), "El canal de riego del anexo.")
        for (query in listOf("canal OR -riego \"sin cerrar", "(", "'; DROP TABLE x; --", ":* & | !", "canal<->riego")) search(query)
        assertEquals(emptyList<ChunkHit>(), search("de la el"))
        assertEquals(emptyList<ChunkHit>(), search("   "))
    }

    @Test
    fun `resincronizar, borrar y conservar tocan solo su organizacion`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        replace(first, "uno", "dos")
        replace(second, "tres")
        replace(first, "de otra organización", organizationId = other)
        assertEquals(2L, runBlocking { chunks.resync(organization, first, elsewhere, "CONFIDENCIAL") })
        assertEquals(IndexedEvidence(first, elsewhere, "CONFIDENCIAL"), runBlocking { chunks.indexed(organization) }.getValue(first))
        assertEquals(IndexedEvidence(first, project, "INTERNA"), runBlocking { chunks.indexed(other) }.getValue(first))
        assertEquals(1L, runBlocking { chunks.retain(organization, listOf(first)) })
        assertEquals(setOf(first), runBlocking { chunks.indexed(organization) }.keys)
        assertEquals(2L, runBlocking { chunks.delete(organization, listOf(first)) })
        assertEquals(setOf(first), runBlocking { chunks.indexed(other) }.keys)
    }
}
