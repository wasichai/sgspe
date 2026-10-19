package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// el tar del paquete de LaTeX y de la respuesta de sgspe-latex: ida y vuelta, determinista y con topes
class TarTest {
    @Test
    fun `ida y vuelta con varios archivos, determinista y en bloques de 512`() {
        val files = linkedMapOf("main.tex" to "hola ñandú".toByteArray(), "img/foto-1.jpg" to ByteArray(1000) { it.toByte() }, "vacio.txt" to ByteArray(0))
        val tar = Tar.write(files)
        assertEquals(0, tar.size % 512)
        assertArrayEquals(tar, Tar.write(files))
        val read = Tar.read(tar)
        assertEquals(files.keys.toList(), read.keys.toList())
        files.forEach { (k, v) -> assertArrayEquals(v, read[k], k) }
    }

    @Test
    fun `una ruta larga, demasiadas entradas o un tar cortado fallan`() {
        assertThrows<IllegalArgumentException> { Tar.write(mapOf("x".repeat(101) to ByteArray(1))) }
        val many = Tar.write((1..5).associate { "f$it" to ByteArray(1) })
        assertThrows<IllegalArgumentException> { Tar.read(many, maxEntries = 4) }
        val tar = Tar.write(mapOf("a" to ByteArray(2000)))
        assertThrows<IllegalArgumentException> { Tar.read(tar.copyOf(1024)) }
    }
}
