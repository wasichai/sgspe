package sgspe.deliverable

import sgspe.privacy.FactRow
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// las cifras y los meses de las láminas, con el formato del informe (ReportFormat: coma de miles, punto decimal), para que la
// presentación diga lo mismo que el documento. lo ausente es «sin dato» y lo que PUBLICO reservó, «reservado»: nunca un cero
object DeckFormat {
    const val ABSENT = "sin dato"
    const val RESERVED = "reservado"
    private val MONTHS =
        listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre")
    private val YEAR_MONTH = Regex("""^(\d{4})-(\d{2})$""")
    private val DECIMAL = Regex("""^-?\d+(?:\.\d+)?$""")

    fun count(value: Int): String = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(value)

    // el valor para dibujar de un texto del modelo: sin las comas de miles; null si no es un número
    fun plain(text: String?): String? = text?.replace(",", "")?.trim()?.takeIf { DECIMAL.matches(it) }

    fun number(text: String?): BigDecimal? = plain(text)?.toBigDecimal()

    // un decimal con `digits` decimales, en el formato del informe
    fun decimal(
        value: BigDecimal,
        digits: Int
    ): String {
        val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }

    fun percent(text: String): String = "${decimal(number(text)!!, 0)} %"

    // el número de casos sin que se parta al final de la línea
    fun cases(n: Int): String = "n = $n"

    // el rótulo de un mes: «2026-04» es «abril»; con `year`, «abril de 2026»; otro texto, tal cual
    fun month(
        label: String,
        year: Boolean = false,
        capital: Boolean = true
    ): String {
        val match = YEAR_MONTH.matchEntire(label) ?: return label
        val name = MONTHS[match.groupValues[2].toInt() - 1].let { if (capital) it.replaceFirstChar(Char::uppercase) else it }
        return if (year) "$name de ${match.groupValues[1]}" else name
    }

    // una cifra de una fila de un hecho: el conteo, el valor (con su unidad si es un porcentaje) o lo reservado o ausente
    fun figure(
        row: FactRow,
        digits: Int? = null,
        unit: String? = null
    ): DeckFigure =
        when {
            row.reserved == true -> DeckFigure(RESERVED, null, reserved = true)
            row.count != null -> DeckFigure(count(row.count), row.count.toString())
            plain(row.value) != null -> {
                val value = number(row.value)!!
                val text = if (digits != null) decimal(value, digits) else row.value!!
                DeckFigure(if ((row.unit ?: unit) == "%") "$text %" else text, plain(row.value))
            }
            else -> DeckFigure(ABSENT, null)
        }
}
