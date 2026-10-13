package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// la vuelta de JOB-07 con dobles (ai-architecture.md §13.2, workflows.md JOB-07): indexa lo nuevo, resincroniza el proyecto y la
// clasificación, borra lo reemplazado y lo que ya no existe, recuerda lo que no tiene texto y aísla los fallos
class KnowledgeSweepTest {
    private val org = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val other = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val project = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val moved = UUID.fromString("00000000-0000-0000-0000-0000000000a2")

    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private class Corpus : KnowledgeCorpus {
        val evidences = mutableMapOf<UUID, MutableList<IndexableEvidence>>()
        val texts = mutableMapOf<UUID, String?>()
        val reads = mutableListOf<UUID>()
        val sinces = mutableListOf<Instant?>()
        var failing: UUID? = null
        val unavailable = mutableSetOf<UUID>()

        // las que una vuelta desde una hora ya no lista (no cambiaron desde la anterior)
        val unchanged = mutableSetOf<UUID>()

        override suspend fun changed(
            organizationId: UUID,
            since: Instant?
        ): List<IndexableEvidence> = evidences[organizationId].orEmpty().filter { since == null || it.id !in unchanged }.also { sinces += since }

        override suspend fun replaced(organizationId: UUID): Set<UUID> = evidences[organizationId].orEmpty().mapNotNull { it.replaces }.toSet()

        override suspend fun text(
            organizationId: UUID,
            evidence: IndexableEvidence
        ): CorpusText {
            reads += evidence.id
            if (evidence.id == failing) error("lectura fallida")
            if (evidence.id in unavailable) return CorpusText.Unavailable
            return texts[evidence.id]?.let { CorpusText.Text(it) } ?: CorpusText.NoText
        }
    }

    private class Store : ChunkStore {
        val chunks = mutableMapOf<Pair<UUID, UUID>, Triple<UUID?, String, List<String>>>()

        override suspend fun indexed(organizationId: UUID): Map<UUID, IndexedEvidence> =
            chunks.filterKeys { it.first == organizationId }.entries.associate { (key, value) ->
                key.second to
                    IndexedEvidence(key.second, value.first, value.second)
            }

        override suspend fun replace(
            organizationId: UUID,
            evidenceId: UUID,
            projectId: UUID?,
            classification: String,
            contents: List<String>
        ) {
            chunks[organizationId to evidenceId] = Triple(projectId, classification, contents)
        }

        override suspend fun resync(
            organizationId: UUID,
            evidenceId: UUID,
            projectId: UUID?,
            classification: String
        ): Long {
            val current = chunks.getValue(organizationId to evidenceId)
            chunks[organizationId to evidenceId] = Triple(projectId, classification, current.third)
            return current.third.size.toLong()
        }

        override suspend fun delete(
            organizationId: UUID,
            evidenceIds: Collection<UUID>
        ): Long = evidenceIds.sumOf { (chunks.remove(organizationId to it)?.third?.size ?: 0).toLong() }

        override suspend fun retain(
            organizationId: UUID,
            evidenceIds: Collection<UUID>
        ): Long {
            val gone = chunks.keys.filter { it.first == organizationId && it.second !in evidenceIds }
            return gone.sumOf { (chunks.remove(it)?.third?.size ?: 0).toLong() }
        }
    }

    private val corpus = Corpus()
    private val store = Store()
    private val platform = mutableListOf<UUID>()
    private var now = Instant.parse("2026-10-13T15:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }

    private fun sweep(organizations: List<UUID> = listOf(org)) =
        KnowledgeSweep(corpus, store, { organizations }, { organization, block ->
            platform += organization
            block()
        }, clock, chunkChars = 100, overlapChars = 10)

    private fun evidence(
        n: Int,
        classification: String? = "INTERNA",
        project: UUID? = this.project,
        replaces: UUID? = null,
        organization: UUID = org
    ) = IndexableEvidence(id(n), project, classification, replaces).also { corpus.evidences.getOrPut(organization) { mutableListOf() } += it }

    @Test
    fun `indexa el texto de una evidencia nueva con su proyecto y su clasificacion, como la plataforma`() {
        evidence(1)
        corpus.texts[id(1)] = "La asamblea pidió reparar el canal. ".repeat(10)
        val tally = runBlocking { sweep().run(full = false) }
        assertEquals(1, tally.indexed)
        assertEquals(listOf(org), platform)
        val (projectId, classification, contents) = store.chunks.getValue(org to id(1))
        assertEquals(project to "INTERNA", projectId to classification)
        assertTrue(contents.size > 1 && contents.all { it.length <= 100 }, "$contents")
    }

    @Test
    fun `una evidencia ya indexada no se vuelve a leer y solo se resincroniza si cambio su proyecto o su clasificacion`() {
        evidence(1, classification = "CONFIDENCIAL", project = moved)
        store.chunks[org to id(1)] = Triple(project, "INTERNA", listOf("texto"))
        val tally = runBlocking { sweep().run(full = false) }
        assertEquals(KnowledgeTally(resynced = 1), tally)
        assertEquals(Triple<UUID?, String, List<String>>(moved, "CONFIDENCIAL", listOf("texto")), store.chunks.getValue(org to id(1)))
        assertEquals(emptyList<UUID>(), corpus.reads)
        assertEquals(KnowledgeTally(), runBlocking { sweep().run(full = false) })
    }

    @Test
    fun `una evidencia reemplazada pierde sus fragmentos y no se indexa`() {
        evidence(1)
        evidence(2, replaces = id(1))
        store.chunks[org to id(1)] = Triple(project, "INTERNA", listOf("viejo", "texto"))
        corpus.texts[id(2)] = "El acta corregida."
        val tally = runBlocking { sweep().run(full = false) }
        assertEquals(KnowledgeTally(indexed = 1, deleted = 2), tally)
        assertNull(store.chunks[org to id(1)])
        assertEquals(listOf(id(2)), corpus.reads)
    }

    @Test
    fun `la vuelta completa, y la primera del proceso, borran los fragmentos de las evidencias que ya no existen`() {
        evidence(1)
        store.chunks[org to id(1)] = Triple(project, "INTERNA", listOf("sigue"))
        store.chunks[org to id(9)] = Triple(project, "INTERNA", listOf("borrada"))
        store.chunks[other to id(9)] = Triple(project, "INTERNA", listOf("de otro tenant"))
        val sweep = sweep()
        assertEquals(KnowledgeTally(deleted = 1), runBlocking { sweep.run(full = false) })
        store.chunks[org to id(8)] = Triple(project, "INTERNA", listOf("borrada después"))
        assertEquals(KnowledgeTally(), runBlocking { sweep.run(full = false) })
        assertEquals(KnowledgeTally(deleted = 1), runBlocking { sweep.run(full = true) })
        assertEquals(setOf(org to id(1), other to id(9)), store.chunks.keys)
    }

    @Test
    fun `una evidencia sin texto no se indexa y no se vuelve a leer en este proceso`() {
        evidence(1)
        corpus.texts[id(1)] = null
        evidence(2)
        corpus.texts[id(2)] = "   \n  "
        val sweep = sweep()
        assertEquals(KnowledgeTally(withoutText = 2), runBlocking { sweep.run(full = false) })
        assertEquals(KnowledgeTally(), runBlocking { sweep.run(full = true) })
        assertEquals(listOf(id(1), id(2)), corpus.reads)
        assertTrue(store.chunks.isEmpty())
    }

    @Test
    fun `una evidencia sin clasificacion no se indexa`() {
        evidence(1, classification = null)
        corpus.texts[id(1)] = "texto"
        assertEquals(KnowledgeTally(), runBlocking { sweep().run(full = false) })
        assertTrue(corpus.reads.isEmpty())
    }

    @Test
    fun `la primera vuelta mira todo y las siguientes desde la anterior, con traslape`() {
        val sweep = sweep()
        runBlocking { sweep.run(full = false) }
        now = Instant.parse("2026-10-13T15:30:00Z")
        runBlocking { sweep.run(full = false) }
        runBlocking { sweep.run(full = true) }
        assertEquals(listOf(null, Instant.parse("2026-10-13T14:58:00Z"), null), corpus.sinces)
    }

    @Test
    fun `una evidencia que falla no detiene a las demas, pero la vuelta no avanza`() {
        evidence(1)
        evidence(2)
        evidence(3, organization = other)
        corpus.failing = id(1)
        corpus.texts[id(2)] = "texto de la segunda"
        corpus.texts[id(3)] = "texto de otro tenant"
        val sweep = sweep(listOf(org, other))
        val tally = runBlocking { sweep.run(full = false) }
        assertEquals(1, tally.indexed)
        assertTrue(org to id(2) in store.chunks)
        assertTrue(other to id(3) in store.chunks)
        assertNull(sweep.lastRun)
    }

    @Test
    fun `un listado vacio no vacia el indice de la organizacion`() {
        store.chunks[org to id(1)] = Triple(project, "INTERNA", listOf("sigue"))
        assertEquals(KnowledgeTally(), runBlocking { sweep().run(full = true) })
        assertTrue(org to id(1) in store.chunks)
    }

    @Test
    fun `una evidencia no disponible no se recuerda como sin texto y se vuelve a leer en la vuelta siguiente`() {
        evidence(1)
        corpus.unavailable += id(1)
        corpus.texts[id(1)] = "El acta de la asamblea."
        val sweep = sweep()
        assertEquals(KnowledgeTally(unavailable = 1), runBlocking { sweep.run(full = false) })
        // la vuelta avanza: la evidencia queda pendiente aunque la lista desde la anterior ya no la traiga
        assertTrue(sweep.lastRun != null)
        corpus.unchanged += id(1)
        corpus.unavailable.clear()
        now = Instant.parse("2026-10-13T15:30:00Z")
        assertEquals(KnowledgeTally(indexed = 1), runBlocking { sweep.run(full = false) })
        assertTrue(org to id(1) in store.chunks)
        assertEquals(listOf(id(1), id(1)), corpus.reads)
    }

    @Test
    fun `una evidencia pendiente que ya no existe no se vuelve a leer en la vuelta completa`() {
        evidence(1)
        evidence(2)
        corpus.texts[id(1)] = "texto"
        corpus.unavailable += id(2)
        val sweep = sweep()
        runBlocking { sweep.run(full = false) }
        corpus.evidences.getValue(org).removeIf { it.id == id(2) }
        corpus.reads.clear()
        assertEquals(KnowledgeTally(), runBlocking { sweep.run(full = true) })
        assertEquals(KnowledgeTally(), runBlocking { sweep.run(full = false) })
        assertEquals(emptyList<UUID>(), corpus.reads)
    }

    @Test
    fun `el texto acotado a maxChars no parte un par sustituto`() {
        evidence(1)
        corpus.texts[id(1)] = "abcd😀"
        val sweep = KnowledgeSweep(corpus, store, { listOf(org) }, { _, block -> block() }, clock, chunkChars = 100, overlapChars = 10, maxChars = 5)
        runBlocking { sweep.run(full = false) }
        assertEquals(listOf("abcd"), store.chunks.getValue(org to id(1)).third)
    }
}
