package sgspe.privacy

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.RuleException

// el tope de lectura de una exportación (dashboards.md §6.2, el mismo criterio de los tableros): por cursor hasta el tope;
// pasarse es un 400 TOO_MANY_RECORDS que pide acotar, nunca una cifra recortada en silencio
class BoundedReadTest {
    // páginas de 3 filas: la siguiente se pide con el cursor que dejó la anterior
    private fun pager(total: Int): suspend (String?) -> Pair<List<Int>, String?> =
        { cursor ->
            val from = cursor?.toInt() ?: 0
            val to = minOf(from + 3, total)
            (from until to).toList() to if (to < total) to.toString() else null
        }

    @Test
    fun `lee todas las paginas cuando caben en el tope, justo en el tope incluido`() {
        assertEquals((0 until 7).toList(), runBlocking { BoundedRead.pages("stakeholder", 7, 0, pager(7)) })
        assertEquals(emptyList<Int>(), runBlocking { BoundedRead.pages("stakeholder", 7, 0, pager(0)) })
    }

    @Test
    fun `una fila mas que el tope es TOO_MANY_RECORDS con 400 y el campo project`() {
        val e = assertThrows<RuleException> { runBlocking { BoundedRead.pages("stakeholder", 7, 0, pager(8)) } }
        assertEquals(400, e.status.value())
        assertEquals("TOO_MANY_RECORDS", e.violations.single().message)
        assertEquals("filters.project", e.violations.single().field)
    }

    @Test
    fun `lo ya leido del mismo conjunto cuenta contra el tope`() {
        assertThrows<RuleException> { runBlocking { BoundedRead.pages("person", 7, 5, pager(3)) } }
        assertEquals(listOf(0, 1), runBlocking { BoundedRead.pages("person", 7, 5, pager(2)) })
    }
}
