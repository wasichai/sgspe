package sgspe.deliverable

import sgspe.privacy.FactRow
import java.time.LocalDate

// lo común de los hechos tipados que T1 y T2 emiten desde typeVersion 2 (ADR-0031, ASM-146). un hecho sale de los mismos datos
// que su tabla o su gráfico, nunca de otro cálculo: el tipo arma la tabla y el hecho desde la misma agrupación. un conteo va
// sobre la grilla completa del catálogo con sus ceros (ASM-088), para que SmallCells pueda aplicarse en PUBLICO
object ReportFacts {
    const val NO_DATA = DiagnosisScene.SIN_DATO
    const val NOT_AVAILABLE = "No disponible"

    // la grilla de un conteo de una dimensión: `counts`, los registros por su valor crudo tal como los agrupa la tabla (con
    // `empty` para el vacío, si la tabla lo agrupa así). sale cada valor del catálogo en su orden declarado con el rótulo que
    // imprime la tabla, el vacío (si hay `empty`) y lo que no está en el catálogo como «No disponible», todos con su cero. un
    // catálogo vacío no arma grilla: null, y el hecho no sale
    fun grid(
        catalog: List<String>,
        counts: Map<String, Int>,
        label: (String) -> String,
        empty: String? = NO_DATA
    ): List<FactRow>? {
        if (catalog.isEmpty()) return null
        val known = catalog.toSet()
        val other = counts.filterKeys { it !in known && it != empty }.values.sum()
        return catalog.map { FactRow(listOf(label(it)), count = counts[it] ?: 0) } +
            listOfNotNull(empty?.let { FactRow(listOf(it), count = counts[it] ?: 0) }) +
            FactRow(listOf(NOT_AVAILABLE), count = other)
    }

    // el rótulo de un valor ENUM en un hecho: el de la tabla (ReportLabels.option) o el valor tal cual
    fun option(value: String): String = ReportLabels.option(value) ?: value

    // el periodo de un hecho, en el formato de las fechas del informe
    fun period(
        start: LocalDate,
        end: LocalDate
    ): String = "Del ${ReportFormat.date(start.toString())} al ${ReportFormat.date(end.toString())}"

    fun cutoff(day: LocalDate): String = "Al ${ReportFormat.date(day.toString())}"

    // el texto de una celda como valor de un hecho: lo vacío o «Sin dato» es ausente (null), nunca cero
    fun value(cell: String): String? = cell.takeUnless { it.isEmpty() || it == NO_DATA }
}
