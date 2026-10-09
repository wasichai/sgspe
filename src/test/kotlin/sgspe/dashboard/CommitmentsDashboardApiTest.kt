package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// el tablero commitments (dashboards.md §5.6, api.md §4.11): sus KPI en una llamada, los filtros que cada uno no aplica, el
// filtro owner y «no disponible» para quien no lee lo que el KPI lee
class CommitmentsDashboardApiTest : F3DomainApiTest() {
    @Test
    fun `el tablero trae los once KPI-COM con el proyecto pedido`() {
        val project = testProject()
        approvedCommitment(project)
        commitment(project)
        val body = tree(send("GET", "/api/sgspe/dashboards/commitments?project=$project", null, HttpStatus.OK))
        val codes = body["indicators"].values().map { it["code"].asString() }
        assertEquals((1..11).map { "KPI-COM-%02d".format(it) }.toSet(), codes.toSet())
        val registered = body["indicators"].values().first { it["code"].asString() == "KPI-COM-01" }
        assertEquals(2, registered["value"].asInt())
        assertEquals(2, body["indicators"].values().first { it["code"].asString() == "KPI-COM-02" }["value"].asInt())
    }

    @Test
    fun `owner filtra los KPI que lo aceptan y los demas lo declaran en ignoredFilters`() {
        val project = testProject()
        val owner = member("COMMITMENT_MANAGER", project)
        approvedCommitment(project, owner)
        approvedCommitment(project)
        val blocked = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-COM-06?project=$project&owner=${owner.id}", null, HttpStatus.OK))
        assertTrue(blocked["ignoredFilters"] == null || blocked["ignoredFilters"].isNull)
        val registered = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-COM-01?project=$project&owner=${owner.id}", null, HttpStatus.OK))
        assertEquals(listOf("owner"), registered["ignoredFilters"].values().map { it.asString() })
        assertEquals(2, registered["value"].asInt())
        val engagement = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-ENG-01?project=$project&owner=${owner.id}", null, HttpStatus.OK))
        assertEquals(listOf("owner"), engagement["ignoredFilters"].values().map { it.asString() })
    }

    @Test
    fun `un parametro desconocido es 422`() {
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/dashboards/commitments?responsable=x", null, token).status)
    }

    // FIELD_AGENT no lee commitment (roles.json): ningun KPI-COM sale con cifra, ni 0; cada uno dice OBJECT_NOT_READABLE y
    // nombra lo que falta (deadline_status en KPI-COM-03 y 04, amount y executed_amount en KPI-COM-10)
    @Test
    fun `quien no lee compromisos recibe los KPI-COM no disponibles y sin cifra`() {
        val project = testProject()
        approvedCommitment(project)
        val agent = member("FIELD_AGENT", project)
        val body = tree(send("GET", "/api/sgspe/dashboards/commitments?project=$project", null, HttpStatus.OK, agent.token))
        val byCode = body["indicators"].values().associateBy { it["code"].asString() }
        assertEquals(11, byCode.size)
        for ((code, kpi) in byCode) {
            assertFalse(kpi["available"].asBoolean(), code)
            assertEquals("OBJECT_NOT_READABLE", kpi["unavailableReason"].asString(), code)
            assertFalse(kpi.has("value"), code)
        }

        fun missing(code: String) = byCode.getValue(code)["missingFields"].values().map { it.asString() }
        assertTrue("commitment.deadline_status" in missing("KPI-COM-03"))
        assertTrue("commitment.deadline_status" in missing("KPI-COM-04"))
        assertTrue("commitment.amount" in missing("KPI-COM-10"))
        assertTrue("commitment.executed_amount" in missing("KPI-COM-10"))
    }
}
