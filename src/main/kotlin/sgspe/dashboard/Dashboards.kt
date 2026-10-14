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
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import sgspe.territory.ComponentLand
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los indicadores de módulo y sus tableros (api.md §4.11, dashboards.md §6 a §8). cada cálculo lleva su lector (especificación
// de F8 §2.2.16): sin lector, la persona que llama, con sus permisos, su confidentialHidden y «mis proyectos»; con
// KpiReader.Platform, la plataforma de JOB-05, fuera de una petición (KpiReaders). antes de calcular, cada KPI comprueba que
// el lector puede leer los objetos y campos que usa; si no, sale no disponible con la razón (OBJECT_NOT_READABLE si falta
// READ sobre un objeto, FIELD_NOT_READABLE si falta un campo) y los campos que faltan, nunca cero (RQ-DSH-032). KPI-STK-03
// exige un solo proyecto (SINGLE_PROJECT_REQUIRED). nada se guarda en el servidor (§6.8). los errores llevan su código en
// errors[0].message (P15): 404 DASHBOARD_NOT_FOUND e INDICATOR_NOT_FOUND, 400 INVALID_BREAKDOWN, PROJECT_REQUIRED y
// TOO_MANY_RECORDS (Records.all con tope). desde F4, el tablero attention, cada KPI-GRV y KPI-ENG-05 llevan
// confidentialHidden si al lector se le ocultan las QCS confidenciales. desde 4.4, los proyectos por defecto de la persona son
// «mis proyectos» de ProjectReach (todos los que lee quien lee todo, ninguno si no tiene asignaciones) y un proyecto ajeno
// pedido da conjuntos vacíos: el alcance de lectura acota las filas (dashboards.md §3.1)
@Service
class Dashboards(
    private val records: Records,
    private val metadata: MetadataService,
    private val callers: Callers,
    private val catalog: ModelCatalog,
    private val clock: Clock,
    private val componentLand: ComponentLand,
    private val readers: KpiReaders,
    private val properties: DashboardsProperties
) {
    suspend fun catalog(): List<CatalogEntry> {
        callers.require()
        val readable = Readability(metadata)
        return Kpis.ALL.map { CatalogEntry(it.code, it.label, it.dashboard, readable.missing(it.requires).isEmpty()) }
    }

    suspend fun dashboard(
        name: String,
        params: MultiValueMap<String, String>,
        reader: KpiReader? = null
    ): DashboardResponse {
        val codes =
            Kpis.DASHBOARDS[name]
                ?: throw notFound("DASHBOARD_NOT_FOUND", "El tablero $name no existe o no está disponible en esta fase", "dashboard")
        // el tablero territorial exige el proyecto (especificación de F6 §3.2): sus cifras son de un proyecto, no del tenant
        if (name == Kpis.TERRITORY && params["project"].isNullOrEmpty()) {
            throw Finding("PROJECT_REQUIRED", "El tablero territorial exige al menos un project", "project", status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        return readers.reading(reader ?: readers.person()) { ctx ->
            val today = today()
            val filters = filters(params, DashboardFilters.COMMON + Kpis.extraFilters(codes), today, ctx)
            // la tabla por proyecto calcula una fila por proyecto: pedir más que el tope es 400; tenerlos por defecto («mis
            // proyectos» o todos) da las tiles y omite la tabla (decisión 24)
            val max = properties.executive.maxProjects
            if (name == Kpis.EXECUTIVE && params["project"].orEmpty().isNotEmpty() && filters.projects.size > max) {
                throw Records.tooMany("project", max)
            }
            val omitted = name == Kpis.EXECUTIVE && filters.projects.size > max
            val data = data(ctx, filters, today)
            // con locality o community, la ficha (KPI-TER-01) va primero
            val shown = if (name == Kpis.TERRITORY && (filters.locality != null || filters.community != null)) listOf(TerritorySummary.CODE) + codes else codes
            val indicators = shown.map { mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(it), data), ctx.confidentialHidden) }
            val byProject = if (name == Kpis.EXECUTIVE && !omitted) byProject(data, ctx.confidentialHidden) else null
            DashboardResponse(
                name,
                filters.echo(),
                clock.instant().toString(),
                indicators,
                if (name == Kpis.ATTENTION) ctx.confidentialHidden else null,
                byProject,
                if (omitted) true else null
            )
        }
    }

    suspend fun indicator(
        code: String,
        params: MultiValueMap<String, String>,
        reader: KpiReader? = null
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        return readers.reading(reader ?: readers.person()) { ctx ->
            val today = today()
            val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN + Kpis.extraFilters(listOf(code)), today, ctx)
            val value = mark(KpiEvaluation.evaluate(kpi, data(ctx, filters, today)), ctx.confidentialHidden)
            val by = filters.breakdown
            when {
                by == null || !value.available -> value
                by !in value.breakdowns.orEmpty().map { it.by } -> {
                    val offered = value.breakdowns.orEmpty().map { it.by }
                    val hint = if (offered.isEmpty()) "$code no tiene desgloses" else "$code se desglosa por ${offered.joinToString(", ")}"
                    throw Finding("INVALID_BREAKDOWN", "Desglose inválido: $hint", BREAKDOWN, status = HttpStatus.BAD_REQUEST).toCodedException()
                }
                else -> value.copy(breakdowns = value.breakdowns.orEmpty().filter { it.by == by })
            }
        }
    }

    // un KPI sobre proyectos y periodo dados, sin otros filtros (el cierre de periodo, el ensamblado de 8.2b): sin `projects`,
    // los proyectos por defecto del lector (todos, para la plataforma y para quien lee todos los proyectos)
    suspend fun value(
        code: String,
        projects: List<UUID>?,
        start: LocalDate,
        end: LocalDate,
        reader: KpiReader
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        return readers.reading(reader) { ctx ->
            val filters = DashboardFilters.of(projects ?: ctx.defaultProjects(), start, end)
            mark(KpiEvaluation.evaluate(kpi, data(ctx, filters, today())), ctx.confidentialHidden)
        }
    }

    // dashboards.md §5.1: una fila por proyecto del filtro con las cifras de ExecutiveKpis.BY_PROJECT, calculadas con las
    // mismas funciones y los mismos permisos sobre el filtro de ese proyecto (DashboardData.with), sin desgloses ni series
    private suspend fun byProject(
        data: DashboardData,
        hidden: Boolean
    ): List<ProjectFigures> {
        val ids = data.filters.projects.map { it.toString() }
        val codes = data.names("project", ids, "code")
        val names = data.names("project", ids)
        return data.filters.projects.map { project ->
            val one = data.with(data.filters.copy(projects = listOf(project)))
            ProjectFigures(
                project.toString(),
                codes[project.toString()],
                names[project.toString()],
                ExecutiveKpis.BY_PROJECT.map { code ->
                    mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(code), one), hidden).copy(breakdowns = null, series = null)
                }
            )
        }
    }

    private fun data(
        ctx: KpiContext,
        filters: DashboardFilters,
        today: LocalDate
    ): DashboardData = DashboardData(records, filters, ctx.readable, today, componentLand = componentLand, platform = ctx.platform)

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
        today: LocalDate,
        ctx: KpiContext
    ): DashboardFilters = DashboardFilters.parse(params, allowed, catalog, today) { ctx.defaultProjects() }.resolve()

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
