package sgspe.dashboard

import java.math.BigDecimal
import java.math.RoundingMode

// una versión aprobada de un área de influencia (KPI-TER-02 y 03): su proyecto, su tipo, su versión y su superficie oficial
data class AreaRow(
    val id: String,
    val project: String?,
    val areaType: String?,
    val versionNumber: Int?,
    val areaHa: BigDecimal?
)

// un miembro de una de esas áreas: qué relación tiene llena (locality, community o administrative_unit) y su condición
data class MemberRow(
    val area: String,
    val memberType: String?,
    val condition: String?
)

// un componente vigente sobre tierra de terceros (KPI-TER-04): TERRITORIO COMUNAL o la tenencia del predio
data class LandRow(
    val component: String,
    val componentType: String?,
    val land: String
)

// KPI-TER-02 a 04 puros y las filas de KPI-TER-01 (dashboards.md §4.8; especificación de F6 §3.2). las cifras se calculan
// desde filas ya leídas como quien mira (DashboardData y, en KPI-TER-04, la compuerta de sgspe.territory)
object TerritoryKpis {
    const val NONE = "SIN DATO"
    const val COMPONENTS = "components"
    const val COMPANY_LAND = "PROPIEDAD DE LA EMPRESA"

    // la suma de calculated_area_ha de las áreas aprobadas, con dos decimales; las que no la tienen no se suman y se advierte
    fun surface(areas: List<AreaRow>): IndicatorResult {
        val measured = areas.mapNotNull { it.areaHa }
        val total = measured.fold(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP)
        val rows =
            areas
                .sortedWith(compareBy({ it.project }, { it.areaType }, { it.id }))
                .map { mapOf("key" to it.id, "project" to it.project, "areaType" to it.areaType, "versionNumber" to it.versionNumber, "value" to it.areaHa) }
        val unmeasured = areas.size - measured.size
        val warnings = if (unmeasured > 0) listOf("$unmeasured área(s) aprobada(s) sin superficie calculada: no se suman") else emptyList()
        return IndicatorResult(total, breakdowns = listOf(Breakdown("area", rows)), warnings = warnings)
    }

    // los miembros de las áreas aprobadas (los de otras áreas no cuentan), por tipo de área, de miembro y condición
    fun members(
        areas: List<AreaRow>,
        members: List<MemberRow>
    ): IndicatorResult {
        val types = areas.associate { it.id to it.areaType }
        val counted = members.filter { it.area in types }
        return IndicatorResult(
            counted.size,
            breakdowns =
                listOf(
                    Breakdown("areaType", Numbers.counts(counted.map { types[it.area] ?: NONE })),
                    Breakdown("memberType", Numbers.counts(counted.map { it.memberType ?: NONE })),
                    Breakdown("memberCondition", Numbers.counts(counted.map { it.condition ?: NONE }))
                )
        )
    }

    // los componentes distintos que cruzan tierra de terceros (comunal, propiedad o posesión): los predios PROPIEDAD DE LA
    // EMPRESA no cuentan, igual que en el resumen del área. un componente cuenta una vez por tierra y una vez por tipo
    fun componentsOnLand(all: List<LandRow>): IndicatorResult {
        val rows = all.filter { it.land != COMPANY_LAND }
        val components = rows.distinctBy { it.component }
        return IndicatorResult(
            components.size,
            breakdowns =
                listOf(
                    Breakdown("tenureType", Numbers.counts(rows.distinctBy { it.component to it.land }.map { it.land })),
                    Breakdown("componentType", Numbers.counts(components.map { it.componentType ?: NONE }))
                )
        )
    }

    // la fila de KPI-TER-01 de un KPI: su valor entero, como lo da el indicador por su cuenta, con su código en `key`
    suspend fun component(
        kpi: Indicator,
        data: DashboardData
    ): Map<String, Any?> = linkedMapOf<String, Any?>("key" to kpi.code) + KpiEvaluation.evaluate(kpi, data).json()

    // la fila de una cifra sin código (programs, lastActivity) que quien mira no puede leer
    suspend fun unavailableRow(
        key: String,
        missing: List<String>,
        data: DashboardData
    ): Map<String, Any?> =
        linkedMapOf(
            "key" to key,
            "available" to false,
            "unavailableReason" to KpiEvaluation.reason(missing, data),
            "missingFields" to missing
        )
}
