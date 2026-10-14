package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.LinkedMultiValueMap
import sgspe.F8IndicatorsApiTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

// RQ-DSH-034 (especificación de F8 §3.1 y §5): las cifras de un ADMIN por el endpoint son las del lector de la plataforma, que
// usará el ensamblado de entregables (8.2b), en los tableros consolidados y en los de módulo, con la postura, una queja, un
// compromiso, una interacción, un riesgo, una alerta y una medición cerrada. el lector de la plataforma se pide fuera de una
// petición, como lo hará JOB-05
class DashboardDeliverableParityTest : F8IndicatorsApiTest() {
    @Autowired
    private lateinit var dashboards: Dashboards

    @Autowired
    private lateinit var readers: KpiReaders

    private fun withoutClock(node: JsonNode): JsonNode = (node.deepCopy() as ObjectNode).also { it.remove("computedAt") }

    @Test
    fun `el endpoint del ADMIN y el lector de la plataforma dan las mismas cifras`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val held = start.plusDays(5).toString() + "T15:00:00Z"
        engagement(project, "REUNION", officer.token, mapOf("held_at" to held)).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        stakeholder(project)
        commitment(project)
        grievance(project)
        socialRisk(project)
        earlyWarning(project)
        assertEquals(201, closePeriod(indicatorId("KPI-COM-01"), project, start, end).status.value())
        val query = mapOf("project" to listOf(project), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        for (name in listOf(Kpis.EXECUTIVE, Kpis.PROJECT, Kpis.MONITORING, Kpis.STAKEHOLDERS, Kpis.ATTENTION, Kpis.RISKS, Kpis.COMMITMENTS)) {
            val endpoint = get("/api/sgspe/dashboards/$name?project=$project&periodStart=$start&periodEnd=$end")
            val platform =
                runBlocking {
                    val params = LinkedMultiValueMap<String, String>().apply { query.forEach { (k, v) -> addAll(k, v) } }
                    dashboards.dashboard(name, params, readers.platform(organizationId()))
                }
            // la respuesta del lector de la plataforma serializada como la del endpoint: a texto JSON y de vuelta a árbol
            assertEquals(withoutClock(endpoint), withoutClock(tree(JSON.writeValueAsString(platform))), name)
        }
    }
}
