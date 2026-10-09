package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// las reglas de datos de CommitmentWriteGuard (RN-ENG-001, RF-COM-004), sin base
class CommitmentDataTest {
    @Test
    fun `un compromiso de un taller exige justificacion y la politica que bloquea lo rechaza`() {
        assertNull(CommitmentData.workshopFinding("REUNION", null, blocked = false))
        assertNull(CommitmentData.workshopFinding("TALLER", "Absuelve una pregunta pendiente", blocked = false))
        val missing = CommitmentData.workshopFinding("REUNION INFORMATIVA", "  ", blocked = false)!!
        assertEquals("JUSTIFICATION_REQUIRED" to HttpStatus.BAD_REQUEST, missing.code to missing.status)
        assertEquals("origin_justification", missing.field)
        val blocked = CommitmentData.workshopFinding("TALLER", "con justificación", blocked = true)!!
        assertEquals("WORKSHOP_COMMITMENT_BLOCKED" to HttpStatus.CONFLICT, blocked.code to blocked.status)
    }

    @Test
    fun `el origen se juzga al crear o al cambiar la interaccion o la justificacion`() {
        assertEquals(true, CommitmentData.originJudged(RecordChangeKind.CREATED, emptySet()))
        assertEquals(true, CommitmentData.originJudged(RecordChangeKind.UPDATED, setOf("origin_justification")))
        assertEquals(false, CommitmentData.originJudged(RecordChangeKind.UPDATED, setOf("title")))
    }

    @Test
    fun `una dependencia que cierra un ciclo se detecta, y un grafo demasiado grande falla cerrado`() {
        val (a, b, c) = List(3) { UUID.randomUUID() }
        val edges = mapOf(b to listOf(c), c to listOf(a))
        // a → b cerraría a → b → c → a
        assertEquals(CommitmentData.CycleCheck.CYCLE, runBlocking { CommitmentData.createsCycle(a, b, 10) { edges[it].orEmpty() } })
        assertEquals(CommitmentData.CycleCheck.NONE, runBlocking { CommitmentData.createsCycle(c, a, 10) { emptyList() } })
        assertEquals(CommitmentData.CycleCheck.CYCLE, runBlocking { CommitmentData.createsCycle(a, a, 10) { emptyList() } })
        val chain = List(5) { UUID.randomUUID() }
        val next = { id: UUID -> listOfNotNull(chain.getOrNull(chain.indexOf(id) + 1)) }
        assertEquals(CommitmentData.CycleCheck.TOO_LARGE, runBlocking { CommitmentData.createsCycle(a, chain.first(), 3) { next(it) } })
    }
}
