package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// la supresión de celdas pequeñas de PUBLICO (ASM-088): menos de 5 sale «<5»; con una sola celda oculta o con una suma
// oculta menor que 5, también se oculta la menor de las visibles, para que restar el total no despeje el valor
class SmallCellsTest {
    private fun apply(vararg counts: Int) = SmallCells.apply(counts.mapIndexed { i, c -> "k$i" to c }).map { it.second }

    @Test
    fun `5 o mas se publica tal cual y 4 o menos sale menor que 5`() {
        assertEquals(listOf(5, 6, 10), apply(5, 6, 10))
        assertEquals(listOf(SmallCells.BELOW, SmallCells.BELOW, 12), apply(4, 4, 12))
        assertEquals(listOf(SmallCells.BELOW, SmallCells.COMPLEMENT), apply(1, 5))
    }

    @Test
    fun `una sola celda oculta arrastra a la menor de las visibles`() {
        // total 17: 17 - 9 = 8 = 1 + 7 sin poder separarlas
        assertEquals(listOf(SmallCells.BELOW, SmallCells.COMPLEMENT, 9), apply(1, 7, 9))
        // la menor visible, aunque aparezca después
        assertEquals(listOf(9, SmallCells.COMPLEMENT, SmallCells.BELOW), apply(9, 6, 2))
    }

    @Test
    fun `varias ocultas cuya suma es menor que 5 tambien arrastran a una visible`() {
        // 1 + 1 = 2 despejaría las dos; con el 6, solo se sabe que suman 8
        assertEquals(listOf(SmallCells.BELOW, SmallCells.BELOW, SmallCells.COMPLEMENT, 20), apply(1, 1, 6, 20))
        // 2 + 3 = 5 ya no despeja ninguna: no hace falta arrastrar
        assertEquals(listOf(SmallCells.BELOW, SmallCells.BELOW, 8), apply(2, 3, 8))
    }

    @Test
    fun `sin visibles que ocultar no hay mas que hacer y una tabla vacia sigue vacia`() {
        assertEquals(listOf(SmallCells.BELOW), apply(3))
        assertEquals(emptyList<Any>(), apply())
    }
}
