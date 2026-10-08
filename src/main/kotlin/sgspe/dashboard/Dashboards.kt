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
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los indicadores de módulo y sus tableros (api.md §4.11, dashboards.md §6 a §8): se calculan en vivo con los permisos
// de quien mira, nunca como la plataforma. antes de calcular, cada KPI comprueba que puede leer los objetos y campos que
// usa; si no, sale no disponible con la razón (OBJECT_NOT_READABLE si falta READ sobre un objeto, FIELD_NOT_READABLE si
// falta un campo) y los campos que faltan, nunca cero (RQ-DSH-032). KPI-STK-03 exige un solo proyecto
// (SINGLE_PROJECT_REQUIRED). nada se guarda en el servidor (§6.8). los errores llevan su código en errors[0].message (P15):
// 404 DASHBOARD_NOT_FOUND e INDICATOR_NOT_FOUND, 400 INVALID_BREAKDOWN y TOO_MANY_RECORDS (DashboardData)
@Service
class Dashboards(
    private val records: Records,
    private val metadata: MetadataService,
    private val callers: Callers,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val clock: Clock
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
        val filters = filters(params, DashboardFilters.COMMON)
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead)
        return DashboardResponse(name, filters.echo(), clock.instant().toString(), codes.map { evaluate(Kpis.BY_CODE.getValue(it), data, readable) })
    }

    suspend fun indicator(
        code: String,
        params: MultiValueMap<String, String>
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN)
        val readable = Readability(metadata)
        val value = evaluate(kpi, DashboardData(records, filters, readable::canRead), readable)
        val by = filters.breakdown ?: return value
        if (!value.available) return value
        val offered = value.breakdowns.orEmpty().map { it.by }
        if (by !in offered) {
            val hint = if (offered.isEmpty()) "$code no tiene desgloses" else "$code se desglosa por ${offered.joinToString(", ")}"
            throw Finding("INVALID_BREAKDOWN", "Desglose inválido: $hint", BREAKDOWN, status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        return value.copy(breakdowns = value.breakdowns.orEmpty().filter { it.by == by })
    }

    private suspend fun evaluate(
        kpi: Indicator,
        data: DashboardData,
        readable: Readability
    ): IndicatorValue {
        val missing = readable.missing(kpi.requires)
        if (missing.isNotEmpty()) {
            val reason =
                if (missing.any { readable.objectMissing(it) }) UnavailableReason.OBJECT_NOT_READABLE else UnavailableReason.FIELD_NOT_READABLE
            return IndicatorResult.unavailable(reason, missing).toValue(kpi.code, kpi.label, kpi.unit)
        }
        if (kpi.singleProject && data.filters.projects.size != 1) {
            return IndicatorResult.unavailable(UnavailableReason.SINGLE_PROJECT_REQUIRED, listOf("project")).toValue(kpi.code, kpi.label, kpi.unit)
        }
        return kpi.compute(data).toValue(kpi.code, kpi.label, kpi.unit)
    }

    private suspend fun filters(
        params: MultiValueMap<String, String>,
        allowed: Set<String>
    ): DashboardFilters {
        val user = callers.require()
        return DashboardFilters
            .parse(params, allowed, catalog, LocalDate.now(clock.withZone(LIMA))) {
                if (user.isAdmin) allProjects() else assigned(user.userId).ifEmpty { allProjects() }
            }.resolve()
    }

    // las asignaciones vigentes de quien mira (Assignments.projectsOf, con Validity.inForce); sin READ sobre
    // project_assignment no tiene asignaciones que leer
    private suspend fun assigned(userId: UUID): List<UUID> =
        try {
            assignments.projectsOf(userId)
        } catch (_: ForbiddenException) {
            emptyList()
        }

    // los proyectos que quien mira puede leer, hasta el tope de una lectura del tablero; sin READ sobre project, ninguno
    private suspend fun allProjects(): List<UUID> =
        try {
            DashboardData.readBounded(records, "project", DashboardData.MAX_ROWS).map { UUID.fromString(it.id) }
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

// qué objetos y campos puede leer quien mira: la definición de cada objeto filtrada por sus permisos
// (MetadataService.definitionOf, la misma vista que usan la interfaz y el agente, dashboards.md §8). sin READ sobre el
// objeto, ningún campo suyo es legible. se consulta una vez por objeto en cada pedido
class Readability(
    private val metadata: MetadataService
) {
    private val fields = mutableMapOf<String, Set<String>?>()

    private suspend fun fieldsOf(objectName: String): Set<String>? {
        if (objectName in fields) return fields[objectName]
        val readable =
            try {
                metadata
                    .definitionOf(objectName)
                    .fields
                    .map { it.name }
                    .toSet()
            } catch (_: ForbiddenException) {
                null
            } catch (_: NotFoundException) {
                null
            }
        fields[objectName] = readable
        return readable
    }

    suspend fun canRead(path: String): Boolean {
        val objectName = path.substringBefore('.')
        val field = path.substringAfter('.', "")
        val readable = fieldsOf(objectName) ?: return false
        return field.isEmpty() || field in readable
    }

    suspend fun missing(requires: List<String>): List<String> = requires.filterNot { canRead(it) }

    suspend fun objectMissing(path: String): Boolean = fieldsOf(path.substringBefore('.')) == null
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
