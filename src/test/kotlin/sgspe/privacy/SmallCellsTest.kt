package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// la supresión de celdas pequeñas de PUBLICO (ASM-088, P28b): menos de 5, el cero incluido, sale con una sola marca; si lo oculto
// suma menos de 5, la menor de las visibles sale con la misma marca, para que restar el total no despeje el valor
class SmallCellsTest {
    private val b = SmallCells.BELOW

    private fun apply(vararg counts: Int) = SmallCells.apply(counts.mapIndexed { i, c -> "k$i" to c }).map { it.second }

    @Test
    fun `5 o mas se publica tal cual y menos de 5, el cero incluido, sale con la marca`() {
        assertEquals(listOf(5, 6, 10), apply(5, 6, 10))
        assertEquals(listOf(b, b, 12), apply(4, 4, 12))
        assertEquals(listOf(b, 9, 12), apply(0, 9, 12))
    }

    @Test
    fun `lo oculto que suma menos de 5 arrastra a la menor de las visibles con la misma marca`() {
        // total 17: 17 - 9 = 8 = 1 + 7 sin poder separarlas, y la marca de ambas es la misma
        assertEquals(listOf(b, b, 9), apply(1, 7, 9))
        // la menor visible, aunque aparezca después
        assertEquals(listOf(9, b, b), apply(9, 6, 2))
        // 1 y 5 con el total 6 a la vista: las dos salen con la marca
        assertEquals(listOf(b, b), apply(1, 5))
        // lo oculto ya suma 5 o más: no hace falta arrastrar
        assertEquals(listOf(b, b, 8), apply(2, 3, 8))
    }

    @Test
    fun `solo ceros y una sola persona no dejan visible nada que restar`() {
        assertEquals(listOf(b, b, b), apply(0, 1, 0))
        assertEquals(listOf(b), apply(3))
        assertEquals(emptyList<Any>(), apply())
        // lo oculto suma cero: no hay a quién proteger y la visible se publica
        assertEquals(listOf(b, 9), apply(0, 9))
    }
}
