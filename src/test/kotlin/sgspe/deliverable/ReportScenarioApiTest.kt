package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest

// el escenario de los entregables de prueba (§5 de la especificación de F8) se arma entero contra wasichai de verdad: lo que
// T2 y T1 leerán en los planes 8.2b y 8.2c existe, en el proyecto UTM 17S, con las fotos con y sin consentimiento
class ReportScenarioApiTest : F8ApiTest() {
    @Test
    fun `el escenario de T2 y T1 queda armado y ficticio`() {
        val scenario = reportScenario()
        val project = record("project", scenario.project)["attributes"]
        assertEquals("Proyecto Ejemplo A", project["name"].asString())
        assertEquals("EPSG 32717", project["working_crs"].asString())
        assertEquals("Comunidad Ejemplo", record("community", scenario.community)["attributes"]["name"].asString())
        assertEquals("EXPULSION", record("incident", scenario.incident)["attributes"]["incident_type"].asString())
        assertEquals("approved", state("area_of_influence", scenario.area))
        assertEquals("delivered", state("social_contribution", scenario.contribution))
        assertEquals(scenario.contribution, record("receipt", scenario.receipt)["attributes"]["social_contribution"].asString())
        val consented = record("evidence", scenario.consentedPhoto)["attributes"]
        assertEquals(true, consented["photo_consent_given"].asBoolean())
        val without = record("evidence", scenario.photoWithoutConsent)["attributes"]
        assertFalse(without.has("photo_consent_given") && !without["photo_consent_given"].isNull, "$without")
        val fieldReport = record("deliverable", scenario.fieldReport)["attributes"]
        assertEquals(FIELD_REPORT to scenario.community, fieldReport["deliverable_type"].asString() to fieldReport["community"].asString())
        val diagnosis = record("deliverable", scenario.socialDiagnosis)["attributes"]
        assertEquals(scenario.study to scenario.area, diagnosis["study"].asString() to diagnosis["area_of_influence"].asString())
        val nodes = tree(send("GET", "/api/objects/network_node/records?community_network=${scenario.network}", null, HttpStatus.OK))["content"].values()
        assertEquals(listOf(scenario.node), nodes.map { it["id"].asString() })
    }
}
