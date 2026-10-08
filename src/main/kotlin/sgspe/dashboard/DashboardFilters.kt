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
    val breakdown: String?
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
            "scope" to scope
        ).filterValues { it != null }

    // los filtros comunes que vinieron (sin los periodos, que siempre tienen valor, ni project, que siempre se aplica)
    fun given(): Set<String> =
        listOfNotNull(
            administrativeUnit?.let { "administrativeUnit" },
            locality?.let { "locality" },
            community?.let { "community" },
            areaOfInfluence?.let { "areaOfInfluence" },
            explorationCampaign?.let { "explorationCampaign" },
            scope?.let { "scope" }
        ).toSet()

    companion object {
        val COMMON =
            setOf("project", "periodStart", "periodEnd", "administrativeUnit", "locality", "community", "areaOfInfluence", "explorationCampaign", "scope")

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
                breakdown
            )
    }
}
