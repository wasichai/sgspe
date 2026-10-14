package sgspe.dashboard

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ValidationException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// el periodo que se cierra (api.md §4.11): `{ project, attributes: { period_start_date, period_end_date } }`; sin project, todo
// el tenant. una fecha que falta es 400 FIELD_REQUIRED, una ilegible 400 INVALID_VALUE y un inicio posterior al fin 400
// OUT_OF_RANGE (decisión 15 del plan 8.2a)
data class ClosingPeriod(
    val project: UUID?,
    val start: LocalDate,
    val end: LocalDate
) {
    companion object {
        private val DATES = setOf("period_start_date", "period_end_date")

        fun parse(body: Map<String, Any?>): ClosingPeriod {
            StrictBody.requireOnly(body, setOf("project", "attributes"))
            val attributes = StrictBody.objectOf(body, "attributes")
            StrictBody.requireOnly(attributes, DATES, "attributes.")
            val project =
                body["project"]?.let { Records.uuid(it) ?: throw ValidationException("Proyecto inválido", "project", "debe ser un UUID") }
            val start = date(attributes, "period_start_date")
            val end = date(attributes, "period_end_date")
            if (start.isAfter(end)) {
                throw bad("OUT_OF_RANGE", "period_start_date no puede ser posterior a period_end_date", "period_start_date")
            }
            return ClosingPeriod(project, start, end)
        }

        private fun date(
            attributes: Map<String, Any?>,
            name: String
        ): LocalDate {
            val raw = Values.text(attributes[name]) ?: throw bad("FIELD_REQUIRED", "El cierre exige $name", name)
            return runCatching { LocalDate.parse(raw) }.getOrNull() ?: throw bad("INVALID_VALUE", "$name debe ser una fecha AAAA-MM-DD", name)
        }

        private fun bad(
            code: String,
            message: String,
            name: String
        ) = Finding(code, message, "attributes.$name", status = HttpStatus.BAD_REQUEST).toCodedException()
    }
}

// las reglas de «una cifra oficial es completa o no existe» (especificación de F8 §2.2.15, ASM-136; decisiones 2 y 14 del plan
// 8.2a): antes de calcular, por el código; después, por lo que dio el KPI calculado como quien cierra
object Closability {
    const val STANCE = "STANCE"
    const val NO_SCALAR_VALUE = "NO_SCALAR_VALUE"
    const val UNAVAILABLE = "UNAVAILABLE"
    const val NOT_PERIOD_BOUND = "NOT_PERIOD_BOUND"

    // los que leen la postura: una cifra congelada la leería cualquiera con READ sobre indicator_measurement (security.md §7.9)
    val STANCE_KPIS: Set<String> = setOf("KPI-STK-02", "KPI-STK-05", "KPI-STK-06")

    // los que nunca tienen un valor escalar
    val NO_SCALAR: Set<String> = setOf(TerritorySummary.CODE, ExecutiveKpis.TREND)

    // decisión 27 (R7): los KPI que no dependen del periodo pedido (una foto de hoy: conteos de abiertos, de estado, de plazo,
    // de matriz o de montos acumulados); se cierran solo para el periodo vigente
    val PERIODLESS: Set<String> =
        setOf(
            "KPI-STK-01",
            "KPI-STK-02",
            "KPI-STK-03",
            "KPI-COM-01",
            "KPI-COM-02",
            "KPI-COM-03",
            "KPI-COM-04",
            "KPI-COM-06",
            "KPI-COM-07",
            "KPI-COM-10",
            "KPI-GRV-02",
            "KPI-GRV-06",
            "KPI-RSK-01",
            "KPI-RSK-02",
            "KPI-RSK-03",
            "KPI-RSK-05",
            "KPI-INV-01",
            "KPI-INV-02",
            "KPI-INV-04",
            "KPI-TER-02",
            "KPI-TER-03",
            "KPI-TER-04",
            ExecutiveKpis.ACTIVE_PROJECTS,
            ExecutiveKpis.ACTIVE_WARNINGS
        )

    // decisión 26 (R5): los desgloses que nombran registros (un punto por grupo de interés, una fila por interacción, por
    // medida atrasada, por responsable o por riesgo): no se congelan en una cifra oficial, que lee cualquiera con READ
    val RECORD_LEVEL: Set<String> = setOf("stakeholder", "engagement", "overdue", "owner", "risk")

    // decisión 26 (R5), como lista de permitidos: los primeros desgloses agregados (conteos por categoría, estado, tipo,
    // tema, territorio, campaña, programa o área), los únicos que se congelan. uno nuevo que no esté aquí ni en RECORD_LEVEL
    // hace fallar ClosabilityKpisTest hasta que se decida; mientras tanto, no se congela
    val AGGREGATE: Set<String> =
        setOf(
            "influence_group",
            "sex",
            "engagement_type",
            "topic",
            "outlet",
            "tone",
            "workflow_state",
            "daysOverdue",
            "commitment_theme",
            "against",
            "currency",
            "type",
            "severity",
            "category",
            "matrix",
            "locality",
            "campaign",
            "program",
            "source",
            "area",
            "areaType",
            "tenureType",
            "stage"
        )

    // la razón por el código y el periodo, o null si se puede calcular: un código que Kpis no calcula (un indicador propio
    // del cliente) no tiene cifra que congelar; uno sin periodo solo vale para el periodo vigente: un mes calendario entero
    // (del día 1 al último), el pasado o el actual de `today` (hoy en Lima). en la práctica, el mes pasado: el actual aún no
    // terminó y el cierre lo rechaza antes con PERIOD_NOT_ENDED
    fun before(
        code: String,
        period: ClosingPeriod,
        today: LocalDate
    ): String? =
        when {
            code !in Kpis.BY_CODE -> UNAVAILABLE
            code in STANCE_KPIS -> STANCE
            code in NO_SCALAR -> NO_SCALAR_VALUE
            code in PERIODLESS && !current(period, today) -> NOT_PERIOD_BOUND
            else -> null
        }

    // decisión 27 (R7): un mes entero, el pasado o el actual
    private fun current(
        period: ClosingPeriod,
        today: LocalDate
    ): Boolean {
        val month = YearMonth.from(period.start)
        val now = YearMonth.from(today)
        return period.start == month.atDay(1) && period.end == month.atEndOfMonth() && (month == now || month == now.minusMonths(1))
    }

    // la razón por el KPI calculado, o null si es una cifra completa. confidentialHidden no impide cerrar: el cierre calcula
    // siempre sin las QCS confidenciales (decisión 25, R4), y sin la marca confidential legible el KPI ya llega no disponible
    // (Kpis.closingRequires). KPI-ENG-05 no depende del lector: cuenta por referred_at, sin leer la QCS (dashboards.md §4.3)
    fun after(value: IndicatorValue): String? =
        when {
            !value.available -> UNAVAILABLE
            value.value == null -> NO_SCALAR_VALUE
            else -> null
        }

    // el primer desglose como tabla Markdown («| <by> | Valor |»), con la etiqueta de cada fila o su clave; null sin desglose
    // o si no es un desglose agregado (AGGREGATE): entonces la medición lleva solo el valor
    fun breakdown(value: IndicatorValue): String? {
        val first = value.breakdowns?.firstOrNull() ?: return null
        if (first.rows.isEmpty() || first.by !in AGGREGATE) return null
        val lines = first.rows.map { row -> "| ${cell(row["label"] ?: row["key"])} | ${cell(row["value"])} |" }
        return (listOf("| ${cell(first.by)} | Valor |", "|---|---|") + lines).joinToString("\n")
    }

    // el mensaje del 409 INDICATOR_NOT_CLOSABLE; NOT_PERIOD_BOUND dice qué periodo vale (en la práctica, el mes pasado)
    fun message(
        reason: String,
        code: String
    ): String {
        val base = "$code no se cierra ($reason): una cifra oficial es completa o no existe (ASM-136)"
        return if (reason == NOT_PERIOD_BOUND) {
            "$base. $code no depende del periodo: solo se cierra para un mes calendario entero, el pasado en Lima " +
                "(el actual aún no terminó)"
        } else {
            base
        }
    }

    // la barra invertida primero (para que la de `\|` no se duplique) y cada salto de línea, \r\n, \r o \n, a un espacio
    private fun cell(value: Any?): String =
        (value?.toString() ?: "")
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace(Regex("\r\n|\r|\n"), " ")
}
