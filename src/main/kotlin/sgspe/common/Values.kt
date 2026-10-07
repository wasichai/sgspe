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
        if (x is Number || y is Number) {
            val dx = decimal(x) ?: return false
            val dy = decimal(y) ?: return false
            return dx.compareTo(dy) == 0
        }
        return x.toString() == y.toString()
    }

    private fun blankToNull(value: Any?): Any? = if (value is String && value.isBlank()) null else value

    fun text(value: Any?): String? = value?.toString()?.trim()?.ifEmpty { null }

    fun decimal(value: Any?): BigDecimal? =
        when (value) {
            null -> null
            is BigDecimal -> value
            is Number -> BigDecimal(value.toString())
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
