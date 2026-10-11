package sgspe.dashboard

// la evaluación de un indicador con los permisos de quien mira (dashboards.md §8): primero la disponibilidad (los objetos y
// campos que lee, un solo proyecto si lo exige) y después el cálculo; los filtros pedidos que no aplica van en ignoredFilters
// (ruling P27: nunca un cero silencioso). la usan el tablero, el detalle de un indicador y cada fila de KPI-TER-01
object KpiEvaluation {
    suspend fun evaluate(
        kpi: Indicator,
        data: DashboardData
    ): IndicatorValue {
        val missing = kpi.requires.filterNot { data.readable(it) }
        val result =
            when {
                missing.isNotEmpty() -> IndicatorResult.unavailable(reason(missing, data), missing)
                kpi.singleProject && data.filters.projects.size != 1 ->
                    IndicatorResult.unavailable(UnavailableReason.SINGLE_PROJECT_REQUIRED, listOf("project"))
                else -> kpi.compute(data)
            }
        val ignored = Kpis.ignoredFilters(kpi, data.filters)
        return result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored.ifEmpty { null })
    }

    // OBJECT_NOT_READABLE si falta READ sobre alguno de los objetos; si no, FIELD_NOT_READABLE
    suspend fun reason(
        missing: List<String>,
        data: DashboardData
    ): String = if (missing.any { !data.readable(it.substringBefore('.')) }) UnavailableReason.OBJECT_NOT_READABLE else UnavailableReason.FIELD_NOT_READABLE
}
