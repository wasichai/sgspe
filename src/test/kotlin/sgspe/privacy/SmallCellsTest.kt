package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// la supresión de celdas pequeñas de PUBLICO (ASM-088, P28c): menos de 5, el cero incluido, sale con una sola marca neutra; mientras
// lo oculto sume menos de 5, el cero incluido, la menor de las visibles sale con la misma marca, para que restar el total no
// despeje un valor y una población de una sola postura no se publique celda por celda
class SmallCellsTest {
    private val h = SmallCells.HIDDEN

    private fun apply(vararg counts: Int) = SmallCells.apply(counts.mapIndexed { i, c -> "k$i" to c }).map { it.second }

    @Test
    fun `5 o mas se publica tal cual si nada pequeno obliga a ocultarlo`() {
        assertEquals(listOf(5, 6, 10), apply(5, 6, 10))
        assertEquals(listOf(h, h, 12), apply(4, 4, 12))
    }

    @Test
    fun `la marca es un texto neutro que no dice un rango`() {
        assertEquals("reservado", SmallCells.HIDDEN)
    }

    @Test
    fun `lo oculto que suma menos de 5, el cero incluido, arrastra a la menor de las visibles con la misma marca`() {
        assertEquals(listOf(h, h, 9), apply(1, 7, 9))
        assertEquals(listOf(9, h, h), apply(9, 6, 2))
        // 1 y 5 con el total 6 a la vista: las dos salen con la marca
        assertEquals(listOf(h, h), apply(1, 5))
        // un cero oculto y una celda de 9: la de 9 también, o se sabría que nadie más está en las demás
        assertEquals(listOf(h, h), apply(0, 9))
        assertEquals(listOf(h, h, 12), apply(0, 9, 12))
        // lo oculto ya suma 5 o más: no hace falta arrastrar
        assertEquals(listOf(h, h, 8), apply(2, 3, 8))
    }

    @Test
    fun `cinco o mas personas en una sola celda con ceros alrededor se ocultan`() {
        assertEquals(listOf(h, h, h, h), apply(0, 6, 0, 0))
        // con otra celda mayor, solo se arrastra la menor
        assertEquals(listOf(h, h, 8, h), apply(0, 6, 8, 0))
    }

    @Test
    fun `solo ceros y una sola persona no dejan visible nada que restar, y sin celdas pequenas no se oculta nada`() {
        assertEquals(listOf(h, h, h), apply(0, 1, 0))
        assertEquals(listOf(h), apply(3))
        assertEquals(emptyList<Any>(), apply())
        assertEquals(listOf(9, 10), apply(9, 10))
    }
}
