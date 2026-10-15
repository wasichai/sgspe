package sgspe.dashboard

// la evaluación de un indicador con los permisos de quien mira (dashboards.md §8): primero la disponibilidad (los objetos y
// campos que lee, un solo proyecto si lo exige) y después el cálculo; los filtros pedidos que no aplica van en ignoredFilters
// (ruling P27: nunca un cero silencioso). la usan el tablero, el detalle de un indicador y cada fila de KPI-TER-01.
// `also`: lo que un uso pide legible además de lo del KPI (el cierre de periodo, Kpis.closingRequires)
object KpiEvaluation {
    suspend fun evaluate(
        kpi: Indicator,
        data: DashboardData,
        also: List<String> = emptyList()
    ): IndicatorValue {
        val missing = (kpi.requires + also).distinct().filterNot { data.readable(it) }
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

    // la tabla por proyecto del tablero executive (plan 8.2a-bis): la misma disponibilidad para todas las filas y la cifra de
    // cada proyecto de Indicator.computeByProject; sin ella, cada proyecto se evalúa aparte con DashboardData.with, como antes.
    // la clave es el id del proyecto, una por proyecto del filtro
    suspend fun evaluateByProject(
        kpi: Indicator,
        data: DashboardData
    ): Map<String, IndicatorValue> {
        val projects = data.filters.projects
        val missing = kpi.requires.distinct().filterNot { data.readable(it) }
        val ignored = Kpis.ignoredFilters(kpi, data.filters).ifEmpty { null }

        fun value(result: IndicatorResult) = result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored)
        if (missing.isNotEmpty()) {
            val unavailable = value(IndicatorResult.unavailable(reason(missing, data), missing))
            return projects.associate { it.toString() to unavailable }
        }
        val split = if (kpi.singleProject) null else kpi.computeByProject(data)
        return projects.associate { p ->
            val key = p.toString()
            key to
                (
                    split?.let { value(it[key] ?: error("${kpi.code} no dio la cifra del proyecto $key")) }
                        ?: evaluate(kpi, data.with(data.filters.copy(projects = listOf(p))))
                )
        }
    }

    // OBJECT_NOT_READABLE si falta READ sobre alguno de los objetos; si no, FIELD_NOT_READABLE
    suspend fun reason(
        missing: List<String>,
        data: DashboardData
    ): String = if (missing.any { !data.readable(it.substringBefore('.')) }) UnavailableReason.OBJECT_NOT_READABLE else UnavailableReason.FIELD_NOT_READABLE
}
