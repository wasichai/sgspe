package sgspe.dashboard

// KPI-TER-01 (dashboards.md §4.8; especificación de F6 §3.2): no tiene un valor propio. reúne en el desglose `components` las
// cifras de la ficha contextual con el filtro de territorio elegido, una fila por cifra y cada una con su available: los KPI
// con su código (su IndicatorValue entero), programs y lastActivity con su clave. así quien no lee stance solo pierde
// KPI-STK-02, y quien no lee social_program solo pierde programs. sin locality ni community, TERRITORY_REQUIRED. la fila de
// KPI-ENG-01 cuenta los últimos 90 días si no se pidió un periodo; las demás no aplican la unidad, el área, la campaña, el
// ámbito ni el responsable (la ficha mira un lugar)
object TerritorySummary {
    const val CODE = "KPI-TER-01"
    const val PROGRAMS = "programs"
    const val LAST_ACTIVITY = "lastActivity"
    const val ENGAGEMENT_DAYS = 90L

    // los filtros comunes que KPI-TER-01 acepta y no aplica
    val IGNORES: Set<String> = setOf("administrativeUnit", "areaOfInfluence", "explorationCampaign", "scope", "owner")

    private val PROGRAM_REQUIRES =
        listOf(
            "social_program.project",
            "social_program.name",
            "social_initiative.social_program",
            "social_initiative.locality",
            "social_initiative.community",
            "beneficiary_group.social_program",
            "beneficiary_group.locality"
        )
    private val LAST_ACTIVITY_REQUIRES = listOf("engagement.project", "engagement.held_at", "engagement.locality", "engagement.community")

    suspend fun summary(
        data: DashboardData,
        indicators: Map<String, Indicator>
    ): IndicatorResult {
        val f = data.filters
        if (f.locality == null && f.community == null) {
            return IndicatorResult.unavailable(UnavailableReason.TERRITORY_REQUIRED, listOf("locality", "community"))
        }
        val base = data.with(f.copy(administrativeUnit = null, areaOfInfluence = null, explorationCampaign = null, scope = null, owner = null))
        val recent = if (f.periodGiven) base else base.with(base.filters.copy(periodStart = data.today.minusDays(ENGAGEMENT_DAYS - 1), periodEnd = data.today))
        val rows =
            listOf(
                TerritoryKpis.component(indicators.getValue("KPI-STK-02"), base),
                TerritoryKpis.component(indicators.getValue("KPI-ENG-01"), recent),
                TerritoryKpis.component(indicators.getValue("KPI-COM-02"), base),
                TerritoryKpis.component(indicators.getValue("KPI-COM-03"), base),
                TerritoryKpis.component(indicators.getValue("KPI-GRV-02"), base),
                TerritoryKpis.component(indicators.getValue("KPI-RSK-01"), base),
                programs(base),
                TerritoryKpis.component(indicators.getValue("KPI-INV-03"), base),
                lastActivity(base)
            )
        return IndicatorResult(null, breakdowns = listOf(Breakdown(TerritoryKpis.COMPONENTS, rows)))
    }

    private suspend fun programs(data: DashboardData): Map<String, Any?> {
        val missing = PROGRAM_REQUIRES.filterNot { data.readable(it) }
        if (missing.isNotEmpty()) return TerritoryKpis.unavailableRow(PROGRAMS, missing, data)
        val programs = data.programsInTerritory()
        return linkedMapOf(
            "key" to PROGRAMS,
            "available" to true,
            "value" to programs.size,
            "breakdowns" to listOf(Breakdown("program", programs.map { mapOf("key" to it.id, "label" to it.name) }))
        )
    }

    private suspend fun lastActivity(data: DashboardData): Map<String, Any?> {
        val missing = LAST_ACTIVITY_REQUIRES.filterNot { data.readable(it) }
        if (missing.isNotEmpty()) return TerritoryKpis.unavailableRow(LAST_ACTIVITY, missing, data)
        return linkedMapOf("key" to LAST_ACTIVITY, "available" to true, "value" to data.lastHeld()?.toString())
    }
}
