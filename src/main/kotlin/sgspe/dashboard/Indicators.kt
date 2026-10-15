package sgspe.dashboard

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import sgspe.common.Values
import java.math.BigDecimal
import java.math.RoundingMode

// un desglose: por qué se corta (by) y sus filas. cada fila trae al menos key y value; algunas, más datos (la matriz
// interés × poder lleva x, y y su etiqueta). los nombres van en camelCase (api.md §3.2)
data class Breakdown(
    val by: String,
    val rows: List<Map<String, Any?>>
)

// las razones por las que un indicador no está disponible (api.md §4.11, dashboards.md §8)
object UnavailableReason {
    const val OBJECT_NOT_READABLE = "OBJECT_NOT_READABLE"
    const val FIELD_NOT_READABLE = "FIELD_NOT_READABLE"
    const val SINGLE_PROJECT_REQUIRED = "SINGLE_PROJECT_REQUIRED"

    // F6: KPI-TER-01 sin locality ni community (dashboards.md §4.8)
    const val TERRITORY_REQUIRED = "TERRITORY_REQUIRED"
}

// lo que calcula un indicador: el valor, su numerador y denominador si es un porcentaje, sus desgloses y su serie mensual.
// si quien llama no puede leer lo que el indicador lee, unavailableReason lo dice y no hay cifra, nunca un cero: las
// funciones puras reciben ese permiso como dato y no calculan sobre lo que no se puede leer (RQ-DSH-032)
data class IndicatorResult(
    val value: Number?,
    val numerator: Number? = null,
    val denominator: Number? = null,
    val breakdowns: List<Breakdown> = emptyList(),
    val series: List<Map<String, Any?>>? = null,
    val warnings: List<String> = emptyList(),
    val unavailableReason: String? = null,
    val missingFields: List<String> = emptyList()
) {
    val available: Boolean get() = unavailableReason == null

    // la respuesta de la API: las claves vacías no viajan
    fun toValue(
        code: String,
        label: String,
        unit: String? = null
    ): IndicatorValue =
        if (!available) {
            IndicatorValue(code, label, false, unavailableReason = unavailableReason, missingFields = missingFields.ifEmpty { null })
        } else {
            IndicatorValue(
                code,
                label,
                true,
                value,
                numerator,
                denominator,
                unit,
                breakdowns.ifEmpty { null },
                series,
                warnings = warnings.ifEmpty { null }
            )
        }

    companion object {
        fun unavailable(
            reason: String,
            missingFields: List<String> = emptyList()
        ) = IndicatorResult(null, unavailableReason = reason, missingFields = missingFields)
    }
}

// la respuesta de un indicador (api.md §4.11): disponible con su cifra, o no disponible con la razón y los campos que
// faltan, nunca cero (RQ-DSH-032). unavailableReason: FIELD_NOT_READABLE, OBJECT_NOT_READABLE o SINGLE_PROJECT_REQUIRED.
// en JSON las claves nulas se omiten, salvo value en un indicador disponible: un porcentaje sin denominador viaja con
// value en null (disponible y sin cifra), y uno no disponible nunca lleva value
data class IndicatorValue(
    val code: String,
    val label: String,
    val available: Boolean,
    val value: Number? = null,
    val numerator: Number? = null,
    val denominator: Number? = null,
    val unit: String? = null,
    val breakdowns: List<Breakdown>? = null,
    val series: List<Map<String, Any?>>? = null,
    val unavailableReason: String? = null,
    val missingFields: List<String>? = null,
    val warnings: List<String>? = null,
    // los filtros pedidos que este indicador no aplica (ruling P27): community en los KPI-STK, explorationCampaign en F2
    val ignoredFilters: List<String>? = null,
    // F4: true si quien mira no ve las QCS confidenciales y el indicador las lee (dashboards.md §8); un booleano, nunca el
    // número de excluidas. null en los demás
    val confidentialHidden: Boolean? = null
) {
    @JsonValue
    fun json(): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("code" to code, "label" to label, "available" to available)
        if (available) out["value"] = value
        listOf(
            "numerator" to numerator,
            "denominator" to denominator,
            "unit" to unit,
            "breakdowns" to breakdowns,
            "series" to series,
            "unavailableReason" to unavailableReason,
            "missingFields" to missingFields,
            "warnings" to warnings,
            "ignoredFilters" to ignoredFilters,
            "confidentialHidden" to confidentialHidden
        ).forEach { (key, item) -> if (item != null) out[key] = item }
        return out
    }
}

data class CatalogEntry(
    val code: String,
    val label: String,
    val dashboard: String?,
    val available: Boolean
)

// una fila de la tabla por proyecto del tablero executive (dashboards.md §5.1): las cifras de las tiles con valor, sin
// desgloses ni series, calculadas con las mismas funciones sobre el filtro de ese proyecto
data class ProjectFigures(
    val project: String,
    val code: String?,
    val name: String?,
    val indicators: List<IndicatorValue>
)

data class DashboardResponse(
    val dashboard: String,
    val filters: Map<String, Any?>,
    val computedAt: String,
    val indicators: List<IndicatorValue>,
    // F4: en el tablero attention, true si quien mira no ve las QCS confidenciales (dashboards.md §8); no viaja en los demás
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val confidentialHidden: Boolean? = null,
    // F8: la tabla por proyecto del tablero executive; no viaja en los demás ni cuando se omite
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val byProject: List<ProjectFigures>? = null,
    // F8: true si el tablero executive omitió la tabla porque los proyectos por defecto pasan el tope (decisión 24)
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val byProjectOmitted: Boolean? = null,
    // F8: las 13 filas de la Tabla 9.5 del tablero monitoring (MonitoringTable), en el orden de indicators[]; no viaja en los
    // demás
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val rows: List<MonitoringRow>? = null
)

// los números de los indicadores: cada cifra se redondea una sola vez, desde los conteos o los valores sin redondear de su
// nivel, nunca desde otra cifra ya redondeada (como el dictamen P17 de los índices)
object Numbers {
    // porcentaje con un decimal; null si no hay denominador
    fun percent(
        numerator: Number,
        denominator: Number
    ): BigDecimal? {
        val divisor = Values.decimal(denominator) ?: return null
        val dividend = Values.decimal(numerator) ?: return null
        return if (divisor.signum() == 0) {
            null
        } else {
            dividend.multiply(BigDecimal(100)).divide(divisor, 1, RoundingMode.HALF_UP)
        }
    }

    // promedio con dos decimales; null si no hay valores
    fun average(values: List<Number>): BigDecimal? {
        val decimals = values.mapNotNull(Values::decimal)
        return if (decimals.isEmpty()) {
            null
        } else {
            decimals.reduce(BigDecimal::add).divide(BigDecimal(decimals.size), 2, RoundingMode.HALF_UP)
        }
    }

    // los desgloses por clave que comparten los KPI-STK y KPI-ENG: de más a menos y, a igual cuenta, por clave
    fun counts(keys: List<String>): List<Map<String, Any?>> =
        keys
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }

    // lo mismo desde los totales ya contados por clave (los conteos de la base, plan 8.2a-bis): las claves sin cuenta no salen
    fun tally(totals: Map<String, Int>): List<Map<String, Any?>> =
        totals.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }
}
