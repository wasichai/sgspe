package sgspe.dashboard

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// una fila de la Tabla 9.5 de DOC-01 como la fija dashboards.md §4.9: número, indicador, KPI, medio de verificación y
// entregable que la produciría
data class MonitoringSpec(
    val number: Int,
    val code: String,
    val label: String,
    val verificationMeans: String,
    val deliverable: String
)

// un registro de indicator como lo usa el tablero: su id, su código, su meta y su medio de verificación (editables)
data class CatalogIndicator(
    val id: String,
    val code: String,
    val target: BigDecimal?,
    val verificationMeans: String?
)

// una indicator_measurement: su indicador, su proyecto (null, todo el tenant), su periodo, su valor y su cierre
data class MeasurementRow(
    val id: String,
    val indicator: String,
    val project: String?,
    val start: LocalDate,
    val end: LocalDate,
    val value: BigDecimal?,
    val closedAt: Instant?
)

// la cifra oficial de una fila (api.md §4.11): la medición, el instante del cierre y el valor
data class Official(
    val measurement: String,
    val closedAt: String?,
    val value: Number?
)

// lo que el pedido es para la cifra oficial: un proyecto o, con project null, todo el tenant, y un periodo
data class OfficialScope(
    val project: String?,
    val start: LocalDate,
    val end: LocalDate
)

// una fila del tablero monitoring (decisión 5 del plan 8.2a): su número y su texto de la Tabla 9.5, el medio de verificación
// (el del catálogo si lo editaron), el entregable, la disponibilidad y el valor del KPI (sus desgloses van en indicators[] del
// mismo orden), la meta con su origen y la cifra oficial, o null
data class MonitoringRow(
    val number: Int,
    val code: String,
    val label: String,
    val verificationMeans: String,
    val deliverable: String,
    val available: Boolean,
    val value: Number?,
    val target: Number?,
    val targetSource: String,
    val official: Official?
)

// el tablero monitoring sin base (dashboards.md §5.5 y §10, ASM-136)
object MonitoringTable {
    const val PROJECT = "PROJECT"
    const val CATALOG = "CATALOG"
    const val NONE = "NONE"
    private const val MEETINGS = "KPI-ENG-02"

    val ROWS: List<MonitoringSpec> =
        listOf(
            MonitoringSpec(
                1,
                "KPI-STK-04",
                "Grupos de interés contactados, por sexo, ámbito e institución",
                "Reporte de cobertura de grupos de interés contactados",
                "T5"
            ),
            MonitoringSpec(
                2,
                "KPI-STK-05",
                "Porcentaje de grupos de interés de oposición que aceptaron conversar con la empresa",
                "Reporte de cobertura de grupos de interés contactados",
                "T5"
            ),
            MonitoringSpec(
                3,
                "KPI-STK-06",
                "Porcentaje de grupos de interés de oposición que variaron su posición",
                "Reporte de cobertura de grupos de interés contactados",
                "T5"
            ),
            MonitoringSpec(
                4,
                "KPI-STK-07",
                "Porcentaje de grupos de interés que aceptaron más de un contacto",
                "Reporte de reuniones mensuales; minuta de reunión; sistematización de percepciones y posición",
                "T3, T6"
            ),
            MonitoringSpec(5, "KPI-ENG-02", "Reuniones sostenidas al mes (al menos 2)", "Reporte de reuniones mensuales; minuta de reunión", "T3"),
            MonitoringSpec(6, "KPI-GRV-07", "Porcentaje de quejas, consultas o solicitudes absueltas", "Reporte de quejas, consultas y solicitudes", "T4"),
            MonitoringSpec(
                7,
                "KPI-ENG-03",
                "Asistentes por reunión informativa, por sexo y ámbito, con la organización que representan",
                "Reporte de reuniones informativas y de recojo de sugerencias; lista de asistentes",
                "T10"
            ),
            MonitoringSpec(
                8,
                "KPI-ENG-04",
                "Temas consultados durante la reunión",
                "Reporte de reuniones informativas y de recojo de sugerencias",
                "T10"
            ),
            MonitoringSpec(
                9,
                "KPI-ENG-05",
                "Porcentaje de inquietudes resueltas en las reuniones informativas",
                "Reporte de reuniones informativas; evaluaciones de los participantes de talleres",
                "T10"
            ),
            MonitoringSpec(10, "KPI-COM-08", "Compromisos formales (escritos) según tema", "Matriz de seguimiento de compromisos", "T7"),
            MonitoringSpec(
                11,
                "KPI-ENG-06",
                "Monitoreos ambientales del agua participativos y con agente independiente",
                "Reporte de los resultados de los monitoreos de agua",
                "T9"
            ),
            MonitoringSpec(
                12,
                "KPI-ENG-07",
                "Comunicados en medios sobre compromisos cumplidos",
                "Matriz de seguimiento de compromisos (difusión)",
                "T7"
            ),
            MonitoringSpec(
                13,
                "KPI-ENG-08",
                "Porcentaje de medios con información positiva y negativa",
                "Seguimiento de medios informativos [DOC-01 §9.3.1]",
                "—"
            )
        )

    val CODES: List<String> = ROWS.map { it.code }

    // a qué medición puede casar el pedido (decisión 4 del plan 8.2a): ningún filtro fuera de project y el periodo; un solo
    // project pedido, o ninguno por quien lee todo el tenant. si no, ninguna cifra oficial
    // `requested`: los project del pedido ya leídos como UUID (Dashboards los parsea y quita repetidos)
    fun scope(
        requested: List<UUID>,
        filters: DashboardFilters,
        tenantWide: Boolean
    ): OfficialScope? {
        val projects = requested.distinct()
        return when {
            filters.given().isNotEmpty() -> null
            projects.size == 1 -> OfficialScope(projects.single().toString(), filters.periodStart, filters.periodEnd)
            projects.isEmpty() && tenantWide -> OfficialScope(null, filters.periodStart, filters.periodEnd)
            else -> null
        }
    }

    // la medición del indicador con el mismo alcance y el mismo periodo, solo si el KPI está disponible para el lector (el
    // chequeo de dashboards.md §8 antes de mostrar una cifra oficial) y no le oculta QCS confidenciales (decisión 25: la
    // cifra oficial de un KPI de QCS no se muestra a quien no ve todas)
    fun official(
        indicator: CatalogIndicator?,
        value: IndicatorValue,
        measurements: List<MeasurementRow>,
        scope: OfficialScope?
    ): Official? {
        if (indicator == null || scope == null || !value.available || value.confidentialHidden == true) return null
        val match =
            measurements.firstOrNull {
                it.indicator == indicator.id && it.project == scope.project && it.start == scope.start && it.end == scope.end
            } ?: return null
        return Official(match.id, match.closedAt?.toString(), match.value)
    }

    // la meta de la fila y su origen (decisión 3 del plan 8.2a, R1 del coordinador). #5: el denominador de KPI-ENG-02, la
    // única fuente de la meta (que cae al target_value del catálogo cuando el proyecto no tiene la suya), con origen PROJECT
    // si un proyecto del filtro o un plan la define, CATALOG si no y el catálogo trae meta, y NONE sin ninguna. las demás:
    // la del catálogo tal cual, o ninguna
    fun target(
        spec: MonitoringSpec,
        value: IndicatorValue,
        indicator: CatalogIndicator?,
        projectDefined: Boolean
    ): Pair<Number?, String> =
        when {
            spec.code == MEETINGS && !value.available -> null to NONE
            spec.code == MEETINGS && projectDefined -> value.denominator to PROJECT
            indicator?.target == null -> null to NONE
            spec.code == MEETINGS -> value.denominator to CATALOG
            else -> indicator.target to CATALOG
        }

    // las 13 filas en su orden. `values`: los 13 KPI ya evaluados para el lector; `catalog`: los indicator por código (vacío en
    // un tenant de F7 o si el lector no lee indicator)
    fun rows(
        values: List<IndicatorValue>,
        catalog: Map<String, CatalogIndicator>,
        measurements: List<MeasurementRow>,
        scope: OfficialScope?,
        projectDefined: Boolean
    ): List<MonitoringRow> =
        ROWS.map { spec ->
            val value = values.first { it.code == spec.code }
            val indicator = catalog[spec.code]
            val (target, source) = target(spec, value, indicator, projectDefined)
            MonitoringRow(
                spec.number,
                spec.code,
                spec.label,
                indicator?.verificationMeans?.ifBlank { null } ?: spec.verificationMeans,
                spec.deliverable,
                value.available,
                if (value.available) value.value else null,
                target,
                source,
                official(indicator, value, measurements, scope)
            )
        }
}
