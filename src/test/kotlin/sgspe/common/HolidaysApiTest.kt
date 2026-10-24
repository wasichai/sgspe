package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest

// GET /api/sgspe/calendar/holidays (#159, api.md §4): la lista de 2026 de application.yml, autenticado; sin token, 401; sin
// year, con un year inválido o fuera de rango y con un parámetro desconocido, 422
class HolidaysApiTest : SgspeApiTest() {
    @Test
    fun `devuelve los feriados de 2026 de application yml, ordenados`() {
        val body = tree(send("GET", "/api/sgspe/calendar/holidays?year=2026", null, HttpStatus.OK))
        assertEquals(2026, body["year"].asInt())
        assertEquals(true, body["configured"].asBoolean())
        val list = body["holidays"]
        val dates = (0 until list.size()).map { list[it].asString() }
        assertEquals(16, dates.size)
        assertEquals("2026-01-01", dates.first())
        assertEquals(listOf("2026-12-08", "2026-12-09", "2026-12-25"), dates.takeLast(3))
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `un anio sin lista responde configured false`() {
        val body = tree(send("GET", "/api/sgspe/calendar/holidays?year=2090", null, HttpStatus.OK))
        assertEquals(false, body["configured"].asBoolean())
        assertEquals(0, body["holidays"].size())
    }

    @Test
    fun `sin token es 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/sgspe/calendar/holidays?year=2026", null, null).status)
    }

    @Test
    fun `sin year, con un year invalido o fuera de rango y con un parametro desconocido es 422`() {
        listOf("", "?year=abc", "?year=1999", "?year=2101", "?year=2026&slug=otra").forEach { query ->
            assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/calendar/holidays$query").status, query)
        }
    }
}
