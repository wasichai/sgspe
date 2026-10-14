package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import sgspe.common.LIMA
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// el cierre de periodo (api.md §4.11, dashboards.md §10, security.md §14; especificación de F8 §5): una cifra escalar se
// congela con su numerador, denominador y desglose agregado; 409 INDICATOR_NOT_CLOSABLE con su razón (postura, sin escalar,
// sin disponibilidad, sin periodo fuera del vigente); las QCS confidenciales nunca entran en la cifra; 400 PERIOD_NOT_ENDED;
// 409 MEASUREMENT_EXISTS por proyecto y por tenant (Review Focus 4); 403 ACTION_REQUIRED para todo el tenant sin ser
// administrador; la medición es appendOnly
class IndicatorMeasurementsApiTest : F8IndicatorsApiTest() {
    private fun reason(response: Response): String = tree(response.body)["reason"].asString()

    @Test
    fun `una cifra escalar se cierra con su desglose y queda inmutable`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        commitment(project)
        val social = member("SOCIAL_MANAGER", project)
        val created = closePeriod(indicatorId("KPI-COM-01"), project, start, end, social.token)
        assertEquals(HttpStatus.CREATED, created.status, created.body)
        val attributes = tree(created.body)["attributes"]
        assertEquals(1, attributes["measured_value"].asInt())
        assertEquals(project, attributes["project"].asString())
        assertEquals(social.email, attributes["closed_by_user_email"].asString())
        assertTrue(attributes["breakdown"].asString().startsWith("| workflow_state | Valor |\n|---|---|\n"), attributes["breakdown"].asString())
        val id = tree(created.body)["id"].asString()
        val changed = exchange("PUT", "/api/objects/indicator_measurement/records/$id", mapOf("attributes" to mapOf("measured_value" to 9)))
        assertEquals(HttpStatus.FORBIDDEN, changed.status, changed.body)
    }

    @Test
    fun `409 INDICATOR_NOT_CLOSABLE con su razon`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val cases =
            listOf(
                "KPI-STK-02" to Closability.STANCE,
                "KPI-STK-05" to Closability.STANCE,
                "KPI-TER-01" to Closability.NO_SCALAR_VALUE,
                "KPI-EXE-03" to Closability.NO_SCALAR_VALUE
            )
        for ((code, why) in cases) {
            val response = closePeriod(indicatorId(code), project, start, end)
            assertEquals(HttpStatus.CONFLICT, response.status, "$code: ${response.body}")
            assertEquals("INDICATOR_NOT_CLOSABLE", code(response), code)
            assertEquals(listOf("indicator"), problemFields(response.body), code)
            assertEquals(why, reason(response), code)
        }
        // un indicador propio del cliente, que Kpis no calcula: no hay cifra que congelar
        val own = createRecord("indicator", mapOf("code" to uniqueName("KPI-CLI-"), "name" to "Indicador propio de la Asociación Ejemplo"))
        assertEquals(Closability.UNAVAILABLE, reason(closePeriod(own, project, start, end)))
        // decisión 27 (R7): un KPI sin periodo es la foto de hoy y no vale para un periodo de hace dos meses
        val old = YearMonth.now(LIMA).minusMonths(2)
        val stale = closePeriod(indicatorId("KPI-COM-02"), project, old.atDay(1), old.atEndOfMonth())
        assertEquals(HttpStatus.CONFLICT, stale.status, stale.body)
        assertEquals(Closability.NOT_PERIOD_BOUND, reason(stale))
    }

    // decisión 25 (R4): la cifra oficial de un KPI de QCS no cuenta las confidenciales, la cierre quien la cierre
    @Test
    fun `las QCS confidenciales nunca entran en la cifra oficial`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        grievance(project)
        runBlocking { confidentialGrievance(UUID.fromString(project), locality("Caserío Ejemplo", null)) }
        val created = closePeriod(indicatorId("KPI-GRV-02"), project, start, end)
        assertEquals(HttpStatus.CREATED, created.status, created.body)
        assertEquals(1, tree(created.body)["attributes"]["measured_value"].asInt())
    }

    @Test
    fun `un periodo abierto es 400 PERIOD_NOT_ENDED`() {
        val month = YearMonth.now(LIMA)
        val response = closePeriod(indicatorId("KPI-COM-01"), activeProject(), month.atDay(1), month.atEndOfMonth())
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("PERIOD_NOT_ENDED", code(response))
        assertEquals(listOf("attributes.period_end_date"), problemFields(response.body))
    }

    // Review Focus 4
    @Test
    fun `un periodo se cierra una vez por proyecto y una vez para el tenant`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val indicator = indicatorId("KPI-COM-01")
        assertEquals(HttpStatus.CREATED, closePeriod(indicator, project, start, end).status)
        val again = closePeriod(indicator, project, start, end)
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("MEASUREMENT_EXISTS", code(again))
        assertEquals(listOf("attributes.period_start_date"), problemFields(again.body))
        val (oldStart, oldEnd) = uniqueMonth()
        val tenant = indicatorId("KPI-ENG-01")
        val key = uniqueName("cierre-")
        val first = closePeriod(tenant, null, oldStart, oldEnd, key = key)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        // el reenvío con la misma clave devuelve la primera respuesta; sin clave, es la segunda medición
        assertEquals(first.body, closePeriod(tenant, null, oldStart, oldEnd, key = key).body)
        assertEquals("MEASUREMENT_EXISTS", code(closePeriod(tenant, null, oldStart, oldEnd)))
        val stored =
            get("/api/objects/indicator_measurement/records?indicator=$tenant&size=200")["content"].values().filter {
                it["attributes"]["period_start_date"].asString() == oldStart.toString() && it["attributes"]["project"].let { p -> p == null || p.isNull }
            }
        assertEquals(1, stored.size)
    }

    // decisión 10 (R2): todo el tenant lo cierra solo un administrador
    @Test
    fun `todo el tenant exige ser administrador y cerrar exige CREATE`() {
        val (start, end) = uniqueMonth()
        val project = activeProject()
        val social = member("SOCIAL_MANAGER", project)
        val tenant = closePeriod(indicatorId("KPI-ENG-01"), null, start, end, social.token)
        assertEquals(HttpStatus.FORBIDDEN, tenant.status, tenant.body)
        assertEquals("ACTION_REQUIRED", code(tenant))
        assertEquals(listOf("project"), problemFields(tenant.body))
        val (last, lastEnd) = lastMonth()
        val reader = closePeriod(indicatorId("KPI-COM-01"), project, last, lastEnd, member("READ_ONLY", project).token)
        assertEquals(HttpStatus.FORBIDDEN, reader.status, reader.body)
    }

    @Test
    fun `un cuerpo con una propiedad desconocida o fechas ilegibles es 400`() {
        val indicator = indicatorId("KPI-COM-01")
        val unknown =
            exchange(
                "POST",
                "/api/sgspe/indicators/$indicator/measurements",
                mapOf("attributes" to mapOf("period_start_date" to "2026-01-01", "period_end_date" to "2026-01-31"), "note" to "x")
            )
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status, unknown.body)
        val bad = closePeriod(indicator, activeProject(), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-31"))
        assertEquals("OUT_OF_RANGE", code(bad))
    }
}
