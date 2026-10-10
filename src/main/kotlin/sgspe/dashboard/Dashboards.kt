package sgspe.dashboard

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los indicadores de módulo y sus tableros (api.md §4.11, dashboards.md §6 a §8): se calculan en vivo con los permisos
// de quien mira, nunca como la plataforma. antes de calcular, cada KPI comprueba que puede leer los objetos y campos que
// usa; si no, sale no disponible con la razón (OBJECT_NOT_READABLE si falta READ sobre un objeto, FIELD_NOT_READABLE si
// falta un campo) y los campos que faltan, nunca cero (RQ-DSH-032). KPI-STK-03 exige un solo proyecto
// (SINGLE_PROJECT_REQUIRED). nada se guarda en el servidor (§6.8). los errores llevan su código en errors[0].message (P15):
// 404 DASHBOARD_NOT_FOUND e INDICATOR_NOT_FOUND, 400 INVALID_BREAKDOWN y TOO_MANY_RECORDS (Records.all con tope). desde F4, el
// tablero attention, cada KPI-GRV y KPI-ENG-05 llevan confidentialHidden si quien mira no ve las QCS confidenciales. desde 4.4,
// los proyectos por defecto son «mis proyectos» de ProjectReach (todos los que lee quien lee todo, ninguno si no tiene
// asignaciones) y un proyecto ajeno pedido da conjuntos vacíos: el alcance de lectura acota las filas (dashboards.md §3.1)
@Service
class Dashboards(
    private val records: Records,
    private val metadata: MetadataService,
    private val callers: Callers,
    private val reach: ProjectReach,
    private val catalog: ModelCatalog,
    private val clock: Clock,
    private val permissions: Permissions
) {
    suspend fun catalog(): List<CatalogEntry> {
        callers.require()
        val readable = Readability(metadata)
        return Kpis.ALL.map { CatalogEntry(it.code, it.label, it.dashboard, readable.missing(it.requires).isEmpty()) }
    }

    suspend fun dashboard(
        name: String,
        params: MultiValueMap<String, String>
    ): DashboardResponse {
        val codes =
            Kpis.DASHBOARDS[name]
                ?: throw notFound("DASHBOARD_NOT_FOUND", "El tablero $name no existe o no está disponible en esta fase", "dashboard")
        val today = today()
        val filters = filters(params, DashboardFilters.COMMON, today)
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead, today)
        val hidden = hidden()
        val indicators = codes.map { mark(evaluate(Kpis.BY_CODE.getValue(it), data, readable), hidden) }
        return DashboardResponse(name, filters.echo(), clock.instant().toString(), indicators, if (name == Kpis.ATTENTION) hidden else null)
    }

    suspend fun indicator(
        code: String,
        params: MultiValueMap<String, String>
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        val today = today()
        val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN, today)
        val readable = Readability(metadata)
        val value =
            mark(evaluate(kpi, DashboardData(records, filters, readable::canRead, today), readable), if (Kpis.confidentialSensitive(code)) hidden() else false)
        val by = filters.breakdown ?: return value
        if (!value.available) return value
        val offered = value.breakdowns.orEmpty().map { it.by }
        if (by !in offered) {
            val hint = if (offered.isEmpty()) "$code no tiene desgloses" else "$code se desglosa por ${offered.joinToString(", ")}"
            throw Finding("INVALID_BREAKDOWN", "Desglose inválido: $hint", BREAKDOWN, status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        return value.copy(breakdowns = value.breakdowns.orEmpty().filter { it.by == by })
    }

    // la disponibilidad primero (permisos, un solo proyecto) y después el cálculo; los filtros que el KPI ignora van en
    // ignoredFilters, disponible o no (ruling P27: nunca un cero silencioso por un filtro que no aplica)
    private suspend fun evaluate(
        kpi: Indicator,
        data: DashboardData,
        readable: Readability
    ): IndicatorValue {
        val missing = readable.missing(kpi.requires)
        val result =
            when {
                missing.isNotEmpty() -> {
                    val reason =
                        if (missing.any { readable.objectMissing(it) }) UnavailableReason.OBJECT_NOT_READABLE else UnavailableReason.FIELD_NOT_READABLE
                    IndicatorResult.unavailable(reason, missing)
                }
                kpi.singleProject && data.filters.projects.size != 1 ->
                    IndicatorResult.unavailable(
                        UnavailableReason.SINGLE_PROJECT_REQUIRED,
                        listOf("project")
                    )
                else -> kpi.compute(data)
            }
        val ignored = Kpis.ignoredFilters(kpi, data.filters)
        return result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored.ifEmpty { null })
    }

    // si a quien mira se le ocultan QCS confidenciales (Permissions.confidentialHidden)
    private suspend fun hidden(): Boolean = permissions.confidentialHidden(callers.require())

    // la marca en un indicador que lee QCS, solo si es cierta (los demás no la llevan)
    private fun mark(
        value: IndicatorValue,
        hidden: Boolean
    ): IndicatorValue = if (hidden && Kpis.confidentialSensitive(value.code)) value.copy(confidentialHidden = true) else value

    // hoy en Lima, del reloj de la aplicación: una sola vez por petición
    private fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    private suspend fun filters(
        params: MultiValueMap<String, String>,
        allowed: Set<String>,
        today: LocalDate
    ): DashboardFilters {
        val user = callers.require()
        return DashboardFilters
            .parse(params, allowed, catalog, today) { reach.mine(user) ?: allProjects() }
            .resolve()
    }

    // los proyectos que quien mira puede leer, hasta el tope de una lectura del tablero; sin READ sobre project, ninguno
    private suspend fun allProjects(): List<UUID> =
        try {
            records.all("project", max = DashboardData.MAX_ROWS).map { UUID.fromString(it.id) }
        } catch (_: ForbiddenException) {
            emptyList()
        }

    private fun notFound(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.NOT_FOUND).toCodedException()

    companion object {
        const val BREAKDOWN = "breakdown"
    }
}

@RestController
@RequestMapping("/api/sgspe/dashboards")
class DashboardsController(
    private val dashboards: Dashboards
) {
    @GetMapping("/indicators")
    suspend fun catalog(request: ServerHttpRequest): List<CatalogEntry> {
        StrictQuery.requireOnly(request.queryParams, emptySet())
        return dashboards.catalog()
    }

    @GetMapping("/indicators/{code}")
    suspend fun indicator(
        @PathVariable code: String,
        request: ServerHttpRequest
    ): IndicatorValue = dashboards.indicator(code, LinkedMultiValueMap(request.queryParams))

    @GetMapping("/{dashboard}")
    suspend fun dashboard(
        @PathVariable dashboard: String,
        request: ServerHttpRequest
    ): DashboardResponse = dashboards.dashboard(dashboard, LinkedMultiValueMap(request.queryParams))
}
