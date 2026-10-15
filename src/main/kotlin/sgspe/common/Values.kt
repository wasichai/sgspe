package sgspe.common

import java.math.BigDecimal

// igualdad de dos valores de atributo, vengan del almacén (Long, BigDecimal, cadenas ISO, uuid en cadena) o de un cuerpo
// json (Int, Double, cadenas). si uno de los dos es un número se comparan como números (3, 3.0 y 3.00 son iguales); si
// no, como texto, así un código "0012" nunca es igual a "12". null y "" son el mismo vacío
object Values {
    fun same(
        a: Any?,
        b: Any?
    ): Boolean {
        val x = blankToNull(a)
        val y = blankToNull(b)
        if (x == null || y == null) return x == null && y == null
        // un campo FILE: lo guardado puede ser el id y lo reenviado el descriptor { id, name, contentType, size, sha256 } que
        // se leyó (desde F8, plan 8.2b): son el mismo archivo si tienen el mismo id. solo si uno de los dos es un descriptor (un
        // mapa con `id`) y el otro un id o un descriptor; dos mapas cualesquiera se comparan enteros, como antes
        if (x is Map<*, *> || y is Map<*, *>) {
            val fx = fileId(x)
            val fy = fileId(y)
            val descriptor = (x as? Map<*, *>)?.containsKey("id") == true || (y as? Map<*, *>)?.containsKey("id") == true
            return if (descriptor && fx != null && fy != null) fx == fy else x == y
        }
        if (x is Number || y is Number) {
            val dx = decimal(x) ?: return false
            val dy = decimal(y) ?: return false
            return dx.compareTo(dy) == 0
        }
        return x.toString() == y.toString()
    }

    // el id de un descriptor de archivo, o el valor como texto si no es un mapa; null para un mapa sin id
    private fun fileId(value: Any): String? = if (value is Map<*, *>) value["id"]?.toString() else value.toString()

    private fun blankToNull(value: Any?): Any? = if (value is String && value.isBlank()) null else value

    fun text(value: Any?): String? = value?.toString()?.trim()?.ifEmpty { null }

    fun decimal(value: Any?): BigDecimal? =
        when (value) {
            null -> null
            is BigDecimal -> value
            // NaN e infinito no son números decimales: toBigDecimalOrNull los vuelve null y same devuelve false
            is Number -> value.toString().toBigDecimalOrNull()
            is String -> value.trim().toBigDecimalOrNull()
            else -> null
        }

    fun bool(value: Any?): Boolean? =
        when (value) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
}
