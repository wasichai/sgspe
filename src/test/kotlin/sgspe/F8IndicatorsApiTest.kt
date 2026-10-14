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

    // un periodo ya terminado de un solo día que sale de uniqueName (un UUID): uno de los 365 000 días desde el 1 de enero
    // de 1000, siempre en el pasado (el cierre exige un periodo terminado, 400 PERIOD_NOT_ENDED). solo para los KPI del
    // periodo (KPI-ENG-01, KPI-COM-08), que se cierran para cualquier periodo terminado; los de PERIODLESS piden el mes
    // vigente (NOT_PERIOD_BOUND). las mediciones del tenant (sin project) lo necesitan: el único compuesto no compara los
    // NULL (WAS-ADR-0037) y el cierre sí, así que un periodo repetido en una base persistente sería 409 MEASUREMENT_EXISTS;
    // con 365 000 días, dos corridas sobre la misma base no lo repiten en la práctica (con 12 000 meses, sí)
    protected fun uniqueDay(): Pair<LocalDate, LocalDate> {
        val day = FIRST_DAY.plusDays(Math.floorMod(uniqueName("dia").hashCode(), DAYS).toLong())
        return day to day
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
        private const val DAYS = 365_000
        private val FIRST_DAY: LocalDate = LocalDate.of(1000, 1, 1)
        private val CATALOG_LOCK = Any()

        @Volatile
        private var loaded = false
    }
}
