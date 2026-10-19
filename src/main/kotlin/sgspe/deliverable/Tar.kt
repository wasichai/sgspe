package sgspe.deliverable

import java.io.ByteArrayOutputStream

// un tar ustar mínimo para el paquete de LaTeX y la respuesta de sgspe-latex (ADR-0030): solo archivos regulares, con rutas
// relativas de hasta 100 caracteres, sin dependencias. `write` es determinista (sin fecha, dueño ni modo variables); `read`
// lee solo archivos regulares, con un tope de entradas y de bytes, y falla con IllegalArgumentException ante algo que no
// entiende. puro
object Tar {
    private const val BLOCK = 512

    fun write(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, bytes) in files) {
            val path = name.toByteArray(Charsets.UTF_8)
            require(path.size <= 100) { "ruta de más de 100 bytes en el paquete" }
            val header = ByteArray(BLOCK)
            path.copyInto(header, 0)
            octal(header, 100, 8, 0b110_100_100)
            octal(header, 108, 8, 0)
            octal(header, 116, 8, 0)
            octal(header, 124, 12, bytes.size.toLong())
            octal(header, 136, 12, 0)
            header[156] = '0'.code.toByte()
            "ustar\u000000".toByteArray(Charsets.US_ASCII).copyInto(header, 257)
            // la suma se calcula con su propio campo en espacios
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            val sum = header.sumOf { it.toInt() and 0xFF }
            octal(header, 148, 7, sum.toLong())
            header[155] = ' '.code.toByte()
            out.write(header)
            out.write(bytes)
            val pad = (BLOCK - bytes.size % BLOCK) % BLOCK
            out.write(ByteArray(pad))
        }
        out.write(ByteArray(2 * BLOCK))
        return out.toByteArray()
    }

    fun read(
        bytes: ByteArray,
        maxEntries: Int = 16
    ): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var at = 0
        while (at + BLOCK <= bytes.size) {
            val header = bytes.copyOfRange(at, at + BLOCK)
            if (header.all { it.toInt() == 0 }) break
            require(out.size < maxEntries) { "demasiadas entradas en el tar" }
            val name = text(header, 0, 100)
            val size = number(header, 124, 12)
            val type = header[156].toInt().toChar()
            require(size >= 0 && at + BLOCK + size <= bytes.size) { "entrada del tar cortada" }
            val start = at + BLOCK
            if (type == '0' || type == '\u0000') out[name] = bytes.copyOfRange(start, start + size.toInt())
            at = start + (size.toInt() + BLOCK - 1) / BLOCK * BLOCK
        }
        return out
    }

    private fun octal(
        header: ByteArray,
        offset: Int,
        length: Int,
        value: Long
    ) {
        val digits =
            java.lang.Long
                .toOctalString(value)
                .padStart(length - 1, '0')
        require(digits.length <= length - 1) { "valor fuera del campo del tar" }
        digits.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        header[offset + length - 1] = 0
    }

    private fun text(
        header: ByteArray,
        offset: Int,
        length: Int
    ): String {
        val end = (offset until offset + length).firstOrNull { header[it].toInt() == 0 } ?: (offset + length)
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun number(
        header: ByteArray,
        offset: Int,
        length: Int
    ): Long =
        text(header, offset, length).trim { it == ' ' || it == '\u0000' }.ifEmpty { "0" }.let {
            requireNotNull(it.toLongOrNull(8)) { "tamaño del tar ilegible" }
        }
}
