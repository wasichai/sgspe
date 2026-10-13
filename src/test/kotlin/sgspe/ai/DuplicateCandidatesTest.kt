package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// las reglas deterministas de AI-07 (especificación de F7 §2.2.10 y §5): nombres normalizados, alternative_names, mismo proyecto,
// fecha y localidad, solo entre registros del mismo objeto y con un tope de pares
class DuplicateCandidatesTest {
    private val project = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val otherProject = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val locality = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private fun at(n: Int) = Instant.parse("2026-10-01T00:00:00Z").plusSeconds(n * 60L)

    private fun person(
        n: Int,
        name: String,
        locality: UUID? = null
    ) = DuplicateRecord("person", id(n), names = listOf(name), locality = locality, createdAt = at(n))

    private fun grievance(
        n: Int,
        text: String,
        date: String = "2026-10-01",
        project: UUID = this.project,
        locality: UUID? = null
    ) = DuplicateRecord("grievance", id(n), text = text, project = project, date = LocalDate.parse(date), locality = locality, createdAt = at(n))

    @Test
    fun `un mismo nombre escrito de otra forma es un par, con el mas antiguo primero`() {
        val pairs = DuplicateCandidates.pairs(listOf(person(2, "ANA  EJEMPLO", locality), person(1, "Ejemplo, Ána", locality), person(3, "Rosa Ccori")), 20)
        val pair = pairs.single()
        assertEquals(id(1) to id(2), pair.first.id to pair.second.id)
        assertEquals(listOf(DuplicateReason.SAME_NAME, DuplicateReason.SAME_LOCALITY), pair.reasons)
        assertEquals(1.1, pair.score, 1e-9)
    }

    @Test
    fun `un nombre alternativo de una organizacion la junta con la otra`() {
        val records =
            listOf(
                DuplicateRecord("organization", id(1), names = listOf("Junta Administradora de Servicios de Saneamiento Ejemplo", "JASS Ejemplo")),
                DuplicateRecord("organization", id(2), names = listOf("jass ejemplo"))
            )
        assertEquals(listOf(DuplicateReason.ALTERNATIVE_NAME), DuplicateCandidates.pairs(records, 20).single().reasons)
    }

    @Test
    fun `nombres parecidos con dos palabras en comun son un par y una sola palabra en comun no`() {
        val similar = DuplicateCandidates.pairs(listOf(person(1, "Ana Ejemplo"), person(2, "Ana María Ejemplo")), 20).single()
        assertEquals(listOf(DuplicateReason.SIMILAR_NAME), similar.reasons)
        val orgs =
            listOf(
                DuplicateRecord("organization", id(3), names = listOf("Comunidad Campesina Ejemplo")),
                DuplicateRecord("organization", id(4), names = listOf("Comunidad Campesina Otra")),
                DuplicateRecord("organization", id(5), names = listOf("Ronda Ejemplo"))
            )
        assertEquals(emptyList<DuplicatePair>(), DuplicateCandidates.pairs(orgs, 20))
    }

    @Test
    fun `los grupos de interes se comparan dentro de su proyecto`() {
        val records =
            listOf(
                DuplicateRecord("stakeholder", id(1), names = listOf("Ana Ejemplo"), project = project),
                DuplicateRecord("stakeholder", id(2), names = listOf("Ana Ejemplo"), project = otherProject),
                DuplicateRecord("stakeholder", id(3), names = listOf("ana ejemplo"), project = project)
            )
        val pair = DuplicateCandidates.pairs(records, 20).single()
        assertEquals(id(1) to id(3), pair.first.id to pair.second.id)
        assertEquals(listOf(DuplicateReason.SAME_NAME, DuplicateReason.SAME_PROJECT), pair.reasons)
    }

    @Test
    fun `dos quejas con textos parecidos del mismo proyecto son un par, y de otro proyecto no`() {
        val text = "El agua del canal de riego del anexo llega turbia desde la perforación del sondaje."
        val records =
            listOf(
                grievance(1, text, locality = locality),
                grievance(2, "Desde la perforación del sondaje, el agua del canal de riego del anexo llega turbia.", "2026-10-05", locality = locality),
                grievance(3, text, project = otherProject)
            )
        val pair = DuplicateCandidates.pairs(records, 20).single()
        assertEquals(id(1) to id(2), pair.first.id to pair.second.id)
        assertEquals(
            listOf(DuplicateReason.SIMILAR_TEXT, DuplicateReason.SAME_PROJECT, DuplicateReason.CLOSE_DATE, DuplicateReason.SAME_LOCALITY),
            pair.reasons
        )
    }

    @Test
    fun `textos algo parecidos solo son un par con fecha cercana y la misma localidad`() {
        val a = "Piden reparar el canal de riego del anexo norte antes de la siembra de papa."
        val b = "Reclamo por el canal de riego roto en el anexo norte; afecta la siembra y los animales del sector alto."
        assertEquals(1, DuplicateCandidates.pairs(listOf(grievance(1, a, locality = locality), grievance(2, b, "2026-10-03", locality = locality)), 20).size)
        assertEquals(0, DuplicateCandidates.pairs(listOf(grievance(1, a, locality = locality), grievance(2, b, "2026-11-30", locality = locality)), 20).size)
        assertEquals(0, DuplicateCandidates.pairs(listOf(grievance(1, a), grievance(2, b, "2026-10-03")), 20).size)
    }

    @Test
    fun `solo se comparan registros del mismo objeto y de los objetos de AI-07`() {
        val records =
            listOf(
                person(1, "Ana Ejemplo"),
                DuplicateRecord("organization", id(2), names = listOf("Ana Ejemplo")),
                DuplicateRecord("engagement", id(3), names = listOf("Ana Ejemplo")),
                DuplicateRecord("engagement", id(4), names = listOf("Ana Ejemplo"))
            )
        assertEquals(emptyList<DuplicatePair>(), DuplicateCandidates.pairs(records, 20))
    }

    @Test
    fun `un par ya vinculado o ya propuesto no vuelve`() {
        val text = "El agua del canal de riego del anexo llega turbia desde la perforación del sondaje."
        val linked = listOf(grievance(1, text), grievance(2, text).copy(duplicateOf = id(1)))
        assertEquals(emptyList<DuplicatePair>(), DuplicateCandidates.pairs(linked, 20))
        val people = listOf(person(1, "Ana Ejemplo"), person(2, "Ana Ejemplo"), person(3, "Ana Ejemplo"))
        val pairs = DuplicateCandidates.pairs(people, 20, known = setOf(setOf(id(2), id(1))))
        assertEquals(setOf(setOf(id(1), id(3)), setOf(id(2), id(3))), pairs.map { setOf(it.first.id, it.second.id) }.toSet())
    }

    @Test
    fun `los pares van por puntaje y hasta el tope`() {
        val people = (1..10).map { person(it, "Ana Ejemplo", if (it <= 2) locality else null) }
        val pairs = DuplicateCandidates.pairs(people, 5)
        assertEquals(5, pairs.size)
        assertEquals(id(1) to id(2), pairs.first().first.id to pairs.first().second.id)
        assertTrue(pairs.zipWithNext().all { (x, y) -> x.score >= y.score })
    }

    @Test
    fun `las particulas de los nombres no cuentan`() {
        val a = DuplicateCandidates.pairs(listOf(person(1, "Juan de la Cruz"), person(2, "Cruz, Juan")), 20).single()
        assertEquals(listOf(DuplicateReason.SAME_NAME), a.reasons)
        val b = DuplicateCandidates.pairs(listOf(person(3, "Ana María de Ejemplo"), person(4, "Ana Ejemplo")), 20).single()
        assertEquals(listOf(DuplicateReason.SIMILAR_NAME), b.reasons)
    }

    @Test
    fun `con el tope, el mismo conjunto sea cual sea el orden de entrada`() {
        val people = (1..12).map { person(it, if (it % 2 == 0) "Ana Ejemplo" else "Rosa Ccori", if (it <= 4) locality else null) }
        val expected = DuplicateCandidates.pairs(people, 4).map { it.first.id to it.second.id }
        repeat(5) { seed ->
            assertEquals(expected, DuplicateCandidates.pairs(people.shuffled(java.util.Random(seed.toLong())), 4).map { it.first.id to it.second.id })
        }
    }

    @Test
    fun `dos mil registros se comparan en poco tiempo`() {
        val people = (1..1000).map { person(it, "Nombre$it Apellido$it") }
        val grievances = (1001..2000).map { grievance(it, "Reclamo$it distinto$it asunto$it particular$it unico$it") }
        val start = System.nanoTime()
        val pairs = DuplicateCandidates.pairs(people + grievances, 20)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertEquals(0, pairs.size)
        assertTrue(millis < 2000, "tardó $millis ms")
    }
}
