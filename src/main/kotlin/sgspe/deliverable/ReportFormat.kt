package sgspe.deliverable

import sgspe.common.Values
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale

// fechas y números en es-PE como en DOC-01 y DOC-02 (RQ-REP-020): «Setiembre, 2026», «30/09/2026» y «1,234.50» (coma de miles,
// punto decimal, como el «148.00 Ha» de DOC-03)
object ReportFormat {
    private val MONTHS =
        listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre")

    fun monthYear(date: LocalDate): String = "${MONTHS[date.monthValue - 1]}, ${date.year}"

    fun month(date: LocalDate): String = MONTHS[date.monthValue - 1].lowercase()

    fun date(raw: Any?): String {
        val text = Values.text(raw) ?: return ""
        val day =
            runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(text).toLocalDate() }.getOrNull()
                ?: return text
        return "%02d/%02d/%d".format(day.dayOfMonth, day.monthValue, day.year)
    }

    fun decimal(raw: Any?): String {
        val value = Values.decimal(raw) ?: return ""
        return DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(value)
    }

    fun sum(values: List<Any?>): BigDecimal = values.mapNotNull { Values.decimal(it) }.fold(BigDecimal.ZERO, BigDecimal::add)

    fun yesNo(raw: Any?): String =
        when (Values.bool(raw)) {
            true -> "Sí"
            false -> "No"
            null -> "Sin dato"
        }
}
