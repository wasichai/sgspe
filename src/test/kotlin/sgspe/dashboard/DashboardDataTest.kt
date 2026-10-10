package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import sgspe.common.Records
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

// los topes de lectura de los tableros y los filtros que cada KPI ignora (dashboards.md §3.1 y §6.2), sin base
class DashboardDataTest {
    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-05-15")
    }

    private fun filters(
        community: UUID? = null,
        campaign: UUID? = null,
        locality: UUID? = null
    ) = DashboardFilters(
        listOf(UUID.randomUUID()),
        LocalDate.parse("2026-05-01"),
        LocalDate.parse("2026-05-31"),
        null,
        locality,
        community,
        null,
        campaign,
        null,
        null
    )

    @Test
    fun `pasarse del tope de las M2M es TOO_MANY_RECORDS antes de leer ninguna`() {
        val records = mock(Records::class.java)
        val data = DashboardData(records, filters(), { true }, TODAY, maxRelated = 1)
        val error = assertThrows<WasichaiException> { runBlocking { data.topicsOf(listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())) } }
        assertEquals(400, error.status.value())
        assertEquals("TOO_MANY_RECORDS", error.violations.single().message)
        verifyNoInteractions(records)
    }

    // el tope cuenta el conjunto entero y cada tramo se lee con lo que queda de él: el segundo tramo no puede juntar otro tope
    // completo antes de que se note
    @Test
    fun `readIn lee cada tramo con lo que queda del tope`() {
        val maxes = mutableListOf<Int?>()
        val row = mock(RecordResponse::class.java)
        // Records.all termina con el objeto, los filtros, los criterios, los ids, el tope, el campo y la continuación; devuelve
        // 400 filas por tramo, o lanza TOO_MANY_RECORDS como la real si pasan del tope que recibe
        val records =
            mock(Records::class.java) { call ->
                if (call.method.name != "all") return@mock null
                val max = call.arguments[4] as Int?
                maxes += max
                if (max != null && 400 > max) throw Records.tooMany(call.arguments[0] as String, max)
                List(400) { row }
            }
        val data = DashboardData(records, filters(), { true }, TODAY, maxRows = 600)
        val ids = List(DashboardData.CHUNK + 1) { UUID.randomUUID().toString() }
        val error = assertThrows<WasichaiException> { runBlocking { data.participants(ids) } }
        assertEquals("TOO_MANY_RECORDS", error.violations.single().message)
        assertEquals(listOf<Int?>(600, 200), maxes)
    }

    @Test
    fun `el tope de las M2M baja a 200 interacciones`() {
        assertEquals(200, DashboardData.MAX_RELATED)
    }

    // P27: stakeholder no tiene comunidad, así que los KPI-STK ignoran community y lo dicen; F2 ignora explorationCampaign
    @Test
    fun `los KPI-STK ignoran community y todos ignoran explorationCampaign, y lo dicen`() {
        val both = filters(community = UUID.randomUUID(), campaign = UUID.randomUUID())
        assertEquals(listOf("community", "explorationCampaign"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-STK-01"), both))
        assertEquals(listOf("community", "explorationCampaign"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-STK-07"), both))
        assertEquals(listOf("explorationCampaign"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-ENG-01"), both))
        assertEquals(emptyList<String>(), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-STK-01"), filters(locality = UUID.randomUUID())))
    }

    // un KPI de F3 o F5 que no declare nada aplica todos los filtros: el valor por defecto no hereda lo que ignora F2
    @Test
    fun `un Kpi que no declara nada no ignora ningun filtro`() {
        assertEquals(emptySet<String>(), Kpi("KPI-X", "Prueba", null, "u", emptyList()) { IndicatorResult(null) }.ignores)
        for (code in listOf("KPI-ENG-01", "KPI-ENG-02", "KPI-ENG-03", "KPI-ENG-04", "KPI-ENG-05")) {
            assertEquals(Kpis.IGNORED_IN_F2, Kpis.BY_CODE.getValue(code).ignores, code)
        }
    }

    // con community, los grupos de interés no se filtran por una comunidad que no tienen: no quedan en cero
    @Test
    fun `community no filtra a los grupos de interes`() {
        val data = DashboardData(mock(Records::class.java), filters(community = UUID.randomUUID()), { true }, TODAY)
        assertEquals(0, runBlocking { data.stakeholderTerritories() }.size)
        assertEquals(1, runBlocking { data.engagementTerritories() }.size)
    }

    // un dato corrupto no se descarta en silencio: se excluye, se registra y el KPI avisa que está incompleto
    @Test
    fun `una reprogramacion sin fecha se excluye y el KPI avisa que esta incompleto`() {
        fun record(
            id: String,
            attributes: Map<String, Any?>
        ): RecordResponse {
            val r = mock(RecordResponse::class.java)
            org.mockito.Mockito
                .`when`(r.id)
                .thenReturn(id)
            org.mockito.Mockito
                .`when`(r.attributes)
                .thenReturn(attributes)
            org.mockito.Mockito
                .`when`(r.createdAt)
                .thenReturn(java.time.Instant.parse("2026-05-10T15:00:00Z"))
            return r
        }
        val commitment = UUID.randomUUID().toString()
        val good = record("ok", mapOf("commitment" to commitment, "previous_due_date" to "2026-05-01", "new_due_date" to "2026-06-01"))
        val bad = record("bad", mapOf("commitment" to commitment, "previous_due_date" to "2026-05-01"))
        val data = DashboardData(mock(Records::class.java), filters(), { true }, TODAY)
        val read = data.parseReschedules(listOf(good, bad))
        assertEquals(1, read.rows.size)
        assertEquals(listOf("bad"), read.skipped)
        val warned = read.warn(IndicatorResult(3))
        assertEquals(1, warned.warnings.size)
        assertEquals(3, warned.value)
        assertEquals(emptyList<String>(), data.parseReschedules(listOf(good)).warn(IndicatorResult(3)).warnings)
    }

    // una fecha mal formada es un dato corrupto como una que falta: se excluye y se avisa, no es un 500
    @Test
    fun `una reprogramacion con una fecha mal formada se excluye y avisa`() {
        val r = mock(RecordResponse::class.java)
        org.mockito.Mockito
            .`when`(r.id)
            .thenReturn("mala")
        org.mockito.Mockito.`when`(r.attributes).thenReturn(
            mapOf("commitment" to UUID.randomUUID().toString(), "previous_due_date" to "no-es-fecha", "new_due_date" to "2026-06-01")
        )
        org.mockito.Mockito
            .`when`(r.createdAt)
            .thenReturn(java.time.Instant.parse("2026-05-10T15:00:00Z"))
        val read = DashboardData(mock(Records::class.java), filters(), { true }, TODAY).parseReschedules(listOf(r))
        assertEquals(emptyList<RescheduleRow>(), read.rows)
        assertEquals(listOf("mala"), read.skipped)
        assertEquals(1, read.warn(IndicatorResult(0)).warnings.size)
    }

    // la lectura de compromisos es acotada: pasarse del tope es TOO_MANY_RECORDS, nunca un recorte silencioso
    @Test
    fun `pasarse del tope de compromisos es TOO_MANY_RECORDS`() {
        val row = mock(RecordResponse::class.java)
        val records =
            mock(Records::class.java) { call ->
                if (call.method.name != "all") return@mock null
                val max = call.arguments[4] as Int?
                if (max != null && 11 > max) throw Records.tooMany(call.arguments[0] as String, max)
                List(11) { row }
            }
        val data = DashboardData(records, filters(), { true }, TODAY, maxRows = 10)
        val error = assertThrows<WasichaiException> { runBlocking { data.commitments(campaign = false, owner = false) } }
        assertEquals(400, error.status.value())
        assertEquals("TOO_MANY_RECORDS", error.violations.single().message)
    }

    // una QCS con una fecha mal formada no sale callada de KPI-GRV-03, 04, 05 y 10: la lectura lanza, como la de compromisos
    // (son campos DATE que wasichai valida, así que es un dato corrupto)
    @Test
    fun `una QCS con una fecha mal formada lanza en vez de salir del KPI`() {
        val row = mock(RecordResponse::class.java)
        org.mockito.Mockito
            .`when`(row.id)
            .thenReturn(UUID.randomUUID().toString())
        org.mockito.Mockito
            .`when`(row.state)
            .thenReturn("resolved")
        org.mockito.Mockito
            .`when`(row.attributes)
            .thenReturn(mapOf("received_at" to "2026-01-05T15:00:00Z", "resolved_date" to "no-es-fecha"))
        val records = mock(Records::class.java) { call -> if (call.method.name == "all") listOf(row) else null }
        val data = DashboardData(records, filters(), { true }, TODAY)
        assertThrows<DateTimeParseException> { runBlocking { data.attention() } }
    }
}
