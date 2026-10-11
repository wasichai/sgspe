package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ApiTest

// el modelo de F5 contra wasichai de verdad (plan 5.1): el único de la entrega sobre su relación opcional (decisión 2 de la
// especificación de F5, Decisión 1 del plan), el tipo obligatorio del ítem, las mediciones inmutables y únicas por periodo,
// un presupuesto por programa y año, los vínculos de F5 en objetos anteriores, y la base F5ApiTest que usan 5.2a y 5.2b
class InvestmentModelApiTest : F5ApiTest() {
    @Test
    fun `una sola entrega por aporte, y las entregas de compromisos no chocan`() {
        val project = testProject()
        // apply.py puso unique: true en el campo de la relación (fase 3); wasichai lo declara único
        val fields = tree(send("GET", "/api/metadata/objects/delivery/fields", null, HttpStatus.OK)).values().associateBy { it["name"].asString() }
        assertEquals(true, fields.getValue("social_contribution")["unique"].asBoolean())
        val contribution = approvedContribution(project, procure = true)
        contributionDelivery(contribution, project, evidenceTypes = emptyList())
        val second =
            runCatching {
                platformCreate(
                    "delivery",
                    mapOf("social_contribution" to contribution, "delivery_date" to "2026-10-21", "conformity_given" to true),
                    "segunda entrega de prueba"
                )
            }
        assertTrue(second.isFailure, "la base aceptó una segunda entrega del mismo aporte")
        val page = tree(send("GET", "/api/objects/delivery/records?social_contribution=$contribution", null, HttpStatus.OK))
        assertEquals(1, page["totalElements"].asInt())
        // dos entregas de un compromiso llevan social_contribution vacío: un UNIQUE de PostgreSQL no compara los NULL
        val commitment = approvedCommitment(project)
        repeat(2) { n ->
            val response =
                exchange(
                    "POST",
                    "/api/sgspe/commitments/$commitment/deliveries",
                    mapOf("attributes" to mapOf("delivery_date" to "2026-10-0${n + 1}", "conformity_given" to true), "evidence" to emptyList<String>()),
                    token,
                    withReason("entrega de prueba")
                )
            assertEquals(HttpStatus.CREATED, response.status, response.body)
        }
    }

    @Test
    fun `el item de un aporte exige su tipo`() {
        val contribution = socialContribution(testProject(), items = 0)
        val response =
            exchange(
                "POST",
                "/api/objects/contribution_item/records",
                mapOf("attributes" to mapOf("social_contribution" to contribution, "description" to "Útiles", "line_amount" to 100))
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertTrue("item_kind" in problemFields(response.body), response.body)
        contributionItem(contribution, attributes = mapOf("item_kind" to "SERVICIO"))
    }

    @Test
    fun `una medicion no cambia y no se repite en el mismo periodo`() {
        val indicator = programIndicator(socialProgram(testProject()))
        val measurement = programMeasurement(indicator)
        assertEquals(HttpStatus.CONFLICT, edit("program_measurement", measurement, mapOf("measured_value" to 30)).status)
        val repeated =
            exchange(
                "POST",
                "/api/objects/program_measurement/records",
                mapOf(
                    "attributes" to
                        mapOf("program_indicator" to indicator, "period_start_date" to "2026-01-01", "period_end_date" to "2026-03-31", "measured_value" to 30)
                )
            )
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
        programMeasurement(indicator, start = "2026-04-01", end = "2026-06-30", value = 30)
    }

    @Test
    fun `un presupuesto por programa y anio`() {
        val program = socialProgram(testProject())
        programBudget(program, year = 2026)
        val repeated =
            exchange(
                "POST",
                "/api/objects/program_budget/records",
                mapOf("attributes" to mapOf("social_program" to program, "budget_year" to 2026, "allocated_amount" to 1000, "currency" to "PEN"))
            )
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
        programBudget(program, year = 2027, currency = "USD")
    }

    @Test
    fun `los vinculos de F5 en objetos anteriores existen en el tenant`() {
        val project = testProject()
        val contribution = socialContribution(project)
        val program = socialProgram(project)
        // las M2M de evidencias (social_contribution es requiresReason: el vínculo lleva motivo)
        evidence(project, "FOTO", links = listOf(mapOf("object" to "social_contribution", "recordId" to contribution)), headers = withReason("foto del aporte"))
        evidence(project, "ACTA", links = listOf(mapOf("object" to "social_program", "recordId" to program)))
        assertEquals(
            1,
            tree(
                send("GET", "/api/objects/social_contribution/records/$contribution/related/evidence_social_contribution", null, HttpStatus.OK)
            )["totalElements"].asInt()
        )
        // los temas priorizados del proyecto y el tema del aporte
        val topic = priorityTopic(project)
        assertEquals(1, tree(send("GET", "/api/objects/project/records/$project/related/project_priority_topic", null, HttpStatus.OK))["totalElements"].asInt())
        val themed = socialContribution(project, attributes = mapOf("topic" to topic))
        assertEquals(topic, record("social_contribution", themed)["attributes"]["topic"].asString())
        // una tarea con un programa de origen (ExactlyOneGuard) y una solicitud que nombra el aporte creado
        createRecord("task", mapOf("project" to project, "title" to "Preparar la evaluación del programa", "social_program" to program))
        val request = stakeholderRequest(project, attributes = mapOf("created_contribution" to contribution))
        assertEquals(contribution, record("stakeholder_request", request)["attributes"]["created_contribution"].asString())
    }

    @Test
    fun `la base arma un aporte adquirido con su entrega y un programa con sus datos`() {
        val project = testProject()
        val contribution = approvedContribution(project, procure = true)
        assertEquals("procured", state("social_contribution", contribution))
        val delivery = contributionDelivery(contribution, project)
        val evidence = tree(send("GET", "/api/objects/delivery/records/$delivery/related/evidence_delivery", null, HttpStatus.OK))
        assertEquals(3, evidence["totalElements"].asInt())
        val program = socialProgram(project)
        val initiative = socialInitiative(program)
        beneficiaryGroup(program, initiative)
        programBudget(program)
        programMilestone(program)
        programMeasurement(programIndicator(program))
        assertEquals("draft", state("social_program", program))
        assertEquals("active", state("agreement", activeAgreement(project)))
        assertTrue(campaign(project).isNotBlank())
    }
}
