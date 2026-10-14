package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.YearMonth

// la base de las pruebas de integración del plan 8.2a (especificación de F8 §5): lo de F6ApiTest más el catálogo de
// indicadores cargado una vez por JVM con model/load_indicators.py (como el aprovisionamiento de SgspeApiTest), un proyecto
// en active, el id de un indicador por su código, el cierre de un periodo por su endpoint y el mes pasado de Lima (un periodo
// que ya terminó). todo ficticio; las ayudas son bloqueantes y escriben como el admin salvo que se pase otro token
abstract class F8IndicatorsApiTest : F6ApiTest() {
    @BeforeEach
    fun indicatorCatalog() {
        synchronized(CATALOG_LOCK) {
            if (!loaded) {
                val run = runScript(listOf("model/load_indicators.py"))
                assertEquals(0, run.exit, "python3 model/load_indicators.py\n${run.output}")
                loaded = true
            }
        }
    }

    // el id del indicador con ese código (KPI-…), leído como el admin
    protected fun indicatorId(code: String): String =
        tree(send("GET", "/api/objects/indicator/records?code=$code", null, HttpStatus.OK))["content"].values().single()["id"].asString()

    protected fun get(
        path: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", path, null, HttpStatus.OK, token))

    // «Proyecto Ejemplo A» en active (WF-A): con CRS de trabajo y una parte CLIENTE, como pide approve (ASM-062), enviado y
    // aprobado por un PROJECT_ADMIN asignado; `attributes` suma parámetros (meetings_per_month_target)
    protected fun activeProject(
        code: String = uniqueName("PRY-EJ-"),
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val project = project(mapOf("code" to code, "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717") + attributes)
        createRecord("project_party", mapOf("project" to project, "organization" to organization(), "party_role" to "CLIENTE"))
        val admin = member("PROJECT_ADMIN", project)
        move("project", project, "submit_for_review", admin.token)
        move("project", project, "approve", admin.token)
        return project
    }

    // el mes pasado en Lima: un periodo que ya terminó (PERIOD_NOT_ENDED no aplica)
    protected fun lastMonth(): Pair<LocalDate, LocalDate> {
        val month = YearMonth.now(LIMA).minusMonths(1)
        return month.atDay(1) to month.atEndOfMonth()
    }

    // un mes ya terminado que sale de uniqueName (un UUID): uno de los 12 000 meses de los años 1000 a 1999, siempre en el
    // pasado (el cierre exige un periodo terminado, 400 PERIOD_NOT_ENDED), así que dos llamadas, dos clases o dos corridas
    // sobre la misma base no repiten mes en la práctica. las mediciones del tenant (sin project) lo necesitan: el único
    // compuesto no compara los NULL (WAS-ADR-0037) y el cierre sí
    protected fun uniqueMonth(): Pair<LocalDate, LocalDate> {
        val month = YearMonth.of(1000, 1).plusMonths(Math.floorMod(uniqueName("mes").hashCode(), MONTHS).toLong())
        return month.atDay(1) to month.atEndOfMonth()
    }

    // POST /api/sgspe/indicators/{id}/measurements; sin project, todo el tenant
    protected fun closePeriod(
        indicator: String,
        project: String?,
        start: LocalDate,
        end: LocalDate,
        token: String = this.token,
        key: String? = null
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/indicators/$indicator/measurements",
            mapOf("project" to project, "attributes" to mapOf("period_start_date" to start.toString(), "period_end_date" to end.toString())),
            token,
            key?.let { mapOf("Idempotency-Key" to it) }.orEmpty()
        )

    companion object {
        private const val MONTHS = 12_000
        private val CATALOG_LOCK = Any()

        @Volatile
        private var loaded = false
    }
}
