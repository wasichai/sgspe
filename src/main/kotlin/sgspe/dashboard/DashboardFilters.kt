package sgspe.dashboard

import org.springframework.util.MultiValueMap
import sgspe.common.ModelCatalog
import sgspe.common.StrictQuery
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID

// los filtros comunes de los tableros (dashboards.md §3.1, api.md §4.11). project se repite; sin project, el filtro por
// defecto lo resuelve quien llama a resolve (Dashboards: todos los proyectos para el ADMIN; para los demás, sus
// asignaciones vigentes o, sin ellas, todos los proyectos que puede leer): una comodidad, no un control de acceso
// (RF-PRJ-005, RQ-SEC-006). el periodo por defecto es el mes en curso de Lima. un parámetro desconocido es un 422
// (ASM-054); un valor inválido, un 400 que lo nombra
data class DashboardFilters(
    val projects: List<UUID>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val administrativeUnit: UUID?,
    val locality: UUID?,
    val community: UUID?,
    val areaOfInfluence: UUID?,
    val explorationCampaign: UUID?,
    val scope: String?,
    val breakdown: String?,
    // F3: el responsable de un compromiso (owner_user_id), en los KPI-COM que lo aceptan (dashboards.md §4.4)
    val owner: UUID? = null,
    // F5: el programa y el año presupuestal, solo en el tablero investment y en los KPI-INV (INVESTMENT, decisión 2 del plan 5.2b)
    val program: UUID? = null,
    val year: Int? = null,
    // F6: si vino periodStart o periodEnd. sin periodo pedido, KPI-TER-01 cuenta las interacciones de los últimos 90 días
    val periodGiven: Boolean = false
) {
    // lo que la respuesta devuelve en `filters`: los proyectos efectivos y los filtros que vinieron
    fun echo(): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "project" to projects.map { it.toString() },
            "periodStart" to periodStart.toString(),
            "periodEnd" to periodEnd.toString(),
            "administrativeUnit" to administrativeUnit?.toString(),
            "locality" to locality?.toString(),
            "community" to community?.toString(),
            "areaOfInfluence" to areaOfInfluence?.toString(),
            "explorationCampaign" to explorationCampaign?.toString(),
            "scope" to scope,
            "owner" to owner?.toString(),
            "program" to program?.toString(),
            "year" to year
        ).filterValues { it != null }

    // los filtros comunes que vinieron (sin los periodos, que siempre tienen valor, ni project, que siempre se aplica)
    fun given(): Set<String> =
        listOfNotNull(
            administrativeUnit?.let { "administrativeUnit" },
            locality?.let { "locality" },
            community?.let { "community" },
            areaOfInfluence?.let { "areaOfInfluence" },
            explorationCampaign?.let { "explorationCampaign" },
            scope?.let { "scope" },
            owner?.let { "owner" },
            program?.let { "program" },
            year?.let { "year" }
        ).toSet()

    companion object {
        val COMMON =
            setOf(
                "project",
                "periodStart",
                "periodEnd",
                "administrativeUnit",
                "locality",
                "community",
                "areaOfInfluence",
                "explorationCampaign",
                "scope",
                "owner"
            )

        // F5: los filtros que solo aceptan el tablero investment y los KPI-INV (Kpis.extraFilters); en otro, 422
        val INVESTMENT: Set<String> = setOf("program", "year")

        // el año presupuestal, como program_budget.budget_year (ProgramRules)
        const val MIN_YEAR = 2000
        const val MAX_YEAR = 2100

        // los filtros de un cálculo sin pedido HTTP (el cierre de periodo, el ensamblado de entregables): proyectos y periodo
        fun of(
            projects: List<UUID>,
            start: LocalDate,
            end: LocalDate
        ): DashboardFilters = DashboardFilters(projects.distinct(), start, end, null, null, null, null, null, null, null, periodGiven = true)

        fun parse(
            params: MultiValueMap<String, String>,
            allowed: Set<String>,
            catalog: ModelCatalog,
            today: LocalDate,
            defaultProjects: suspend () -> List<UUID>
        ): Pending {
            StrictQuery.requireOnly(params, allowed)
            val violations = mutableListOf<FieldViolation>()

            fun uuid(
                name: String,
                raw: String
            ): UUID? =
                runCatching { UUID.fromString(raw) }.getOrElse {
                    violations += FieldViolation(name, "debe ser un UUID")
                    null
                }

            fun uuid(name: String): UUID? = params.getFirst(name)?.let { uuid(name, it) }

            fun date(name: String): LocalDate? =
                params.getFirst(name)?.let { raw ->
                    try {
                        LocalDate.parse(raw)
                    } catch (_: DateTimeParseException) {
                        violations += FieldViolation(name, "debe ser una fecha AAAA-MM-DD")
                        null
                    }
                }
            val projects = params["project"].orEmpty().mapNotNull { uuid("project", it) }
            val start = date("periodStart") ?: YearMonth.from(today).atDay(1)
            val end = date("periodEnd") ?: YearMonth.from(today).atEndOfMonth()
            if (start.isAfter(end)) violations += FieldViolation("periodStart", "no puede ser posterior a periodEnd")
            val scope = params.getFirst("scope")
            val scopes = catalog.enumValues("stakeholder_scope")
            if (scope != null && scopes.isNotEmpty() && scope !in scopes) violations += FieldViolation("scope", "debe ser uno de ${scopes.joinToString(", ")}")
            val year =
                params.getFirst("year")?.let { raw ->
                    raw.trim().toIntOrNull()?.takeIf { it in MIN_YEAR..MAX_YEAR }
                        ?: run {
                            violations += FieldViolation("year", "debe ser un año entero de $MIN_YEAR a $MAX_YEAR")
                            null
                        }
                }
            val pending =
                Pending(
                    projects,
                    start,
                    end,
                    uuid("administrativeUnit"),
                    uuid("locality"),
                    uuid("community"),
                    uuid("areaOfInfluence"),
                    uuid("explorationCampaign"),
                    scope,
                    params.getFirst("breakdown"),
                    uuid("owner"),
                    uuid("program"),
                    year,
                    params.getFirst("periodStart") != null || params.getFirst("periodEnd") != null,
                    defaultProjects
                )
            if (violations.isNotEmpty()) throw ValidationException("Filtro inválido", violations)
            return pending
        }
    }

    // los filtros ya validados, con los proyectos por defecto por resolver (leerlos exige una consulta)
    class Pending(
        private val projects: List<UUID>,
        private val start: LocalDate,
        private val end: LocalDate,
        private val administrativeUnit: UUID?,
        private val locality: UUID?,
        private val community: UUID?,
        private val areaOfInfluence: UUID?,
        private val explorationCampaign: UUID?,
        private val scope: String?,
        private val breakdown: String?,
        private val owner: UUID?,
        private val program: UUID?,
        private val year: Int?,
        private val periodGiven: Boolean,
        private val defaults: suspend () -> List<UUID>
    ) {
        suspend fun resolve(): DashboardFilters =
            DashboardFilters(
                projects.ifEmpty { defaults() }.distinct(),
                start,
                end,
                administrativeUnit,
                locality,
                community,
                areaOfInfluence,
                explorationCampaign,
                scope,
                breakdown,
                owner,
                program,
                year,
                periodGiven
            )
    }
}
