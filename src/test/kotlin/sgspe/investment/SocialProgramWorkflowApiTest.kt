package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F5ProgramApiTest
import sgspe.common.DerivedFieldsJob
import java.math.BigDecimal

// WF-G por la API (workflows.md «Pruebas», especificación de F5 §5): G1 a G4, el motivo de suspender y cancelar, las reglas de
// los hijos, las mediciones inmutables, total_budget con su listener y JOB-02, y el aviso de aprobación. testProject() tiene la
// aprobación interna activa (por defecto); projectWithoutInternalApproval() no
class SocialProgramWorkflowApiTest : F5ProgramApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    private val investor by lazy { member("SOCIAL_INVESTMENT_MANAGER") }
    private val approver by lazy { member("APPROVER") }

    private fun submitted(
        attributes: Map<String, Any?> = emptyMap(),
        project: String = testProject(),
        creator: Account = investor
    ): String {
        val id = socialProgram(project, creator, attributes)
        move("social_program", id, "submit", creator.token)
        return id
    }

    private fun total(program: String): BigDecimal? = record("social_program", program)["attributes"]["total_budget"]?.takeUnless { it.isNull }?.decimalValue()

    private fun inbox(token: String) = tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values()

    private fun pendingFor(
        token: String,
        id: String
    ): Boolean = inbox(token).any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    @Test
    fun `approve por un APPROVER que no registro el programa lo planifica`() {
        val id = submitted()
        move("social_program", id, "approve", approver.token)
        assertEquals("planned", state("social_program", id))
    }

    @Test
    fun `approve por quien registro el programa es 403 SEGREGATION_OF_DUTIES`() {
        // una persona con los dos roles: registra y envía como SOCIAL_INVESTMENT_MANAGER y aprueba como APPROVER
        val both = userWithRoles(listOf("SOCIAL_INVESTMENT_MANAGER", "APPROVER")).also { assign(it, testProject(), "APPROVER") }
        val id = submitted(creator = both)
        val refused = transition("social_program", id, "approve", both.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(refused))
        assertEquals("pending_approval", state("social_program", id))
    }

    // decisión 8 del plan 5.2b: G1 no se alcanza por la API con los roles de hoy; la consulta en seco corre la regla sin los roles
    @Test
    fun `sin APPROVE la consulta en seco de approve da ACTION_REQUIRED`() {
        val id = submitted()
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "social_program", "recordId" to id, "transition" to "approve"),
                    HttpStatus.OK,
                    investor.token
                )
            )
        assertFalse(check["allowed"].asBoolean())
        assertTrue(check["blocking"].values().any { it["code"].asString() == "ACTION_REQUIRED" }, check.toString())
    }

    @Test
    fun `con socio y la politica activa approve exige un acuerdo vigente con el socio como parte`() {
        val partner = organization("Asociación Ejemplo")
        val project = testProject()

        fun refusedWith(attributes: Map<String, Any?>) {
            val id = submitted(mapOf("partner_organization" to partner) + attributes)
            val refused = transition("social_program", id, "approve", approver.token)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(refused))
            assertEquals("agreement", tree(refused.body)["errors"][0]["field"].asString())
        }
        refusedWith(emptyMap())
        refusedWith(mapOf("agreement" to agreement(project)))
        refusedWith(mapOf("agreement" to activeAgreementWith(project)))
        val expired = activeAgreementWith(project, listOf(partner))
        platformPatch("agreement", expired, mapOf("validity_status" to "VENCIDO"))
        refusedWith(mapOf("agreement" to expired))
        val id = submitted(mapOf("partner_organization" to partner, "agreement" to activeAgreementWith(project, listOf(partner))))
        move("social_program", id, "approve", approver.token)
        assertEquals("planned", state("social_program", id))
    }

    @Test
    fun `con la politica de sinergia apagada el socio sin acuerdo no bloquea`() {
        val project = project(mapOf("policy_synergy_requires_agreement" to false))
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val id = submitted(mapOf("partner_organization" to organization("Asociación Ejemplo")), project, investor)
        move("social_program", id, "approve", member("APPROVER", project).token)
        assertEquals("planned", state("social_program", id))
    }

    @Test
    fun `plan_direct con la aprobacion interna activa es 409 y sin ella pasa, con la sinergia de G3`() {
        val strict = socialProgram(testProject(), investor)
        val refused = transition("social_program", strict, "plan_direct", investor.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        val relaxed = projectWithoutInternalApproval()
        val planner = member("SOCIAL_INVESTMENT_MANAGER", relaxed)
        val plain = socialProgram(relaxed, planner)
        move("social_program", plain, "plan_direct", planner.token)
        assertEquals("planned", state("social_program", plain))
        val partnered = socialProgram(relaxed, planner, mapOf("partner_organization" to organization("Asociación Ejemplo")))
        assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(transition("social_program", partnered, "plan_direct", planner.token)))
    }

    @Test
    fun `suspend, return_to_draft y las cancel_ sin motivo son 400 y con motivo pasan`() {
        val relaxed = projectWithoutInternalApproval()
        val planner = member("SOCIAL_INVESTMENT_MANAGER", relaxed)
        val manager = member("SOCIAL_MANAGER", relaxed)
        val running = socialProgram(relaxed, planner)
        move("social_program", running, "plan_direct", planner.token)
        move("social_program", running, "start_execution", planner.token)
        val noReason = transition("social_program", running, "suspend", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, noReason.status, noReason.body)
        assertEquals("REASON_REQUIRED", code(noReason))
        move("social_program", running, "suspend", manager.token, "falta de acceso a la comunidad")
        assertEquals("REASON_REQUIRED", code(transition("social_program", running, "cancel_suspended", manager.token)))
        move("social_program", running, "cancel_suspended", manager.token, "la comunidad desistió")
        val draft = socialProgram(relaxed, planner)
        assertEquals("REASON_REQUIRED", code(transition("social_program", draft, "cancel_draft", planner.token)))
        val pending = submitted(project = relaxed, creator = planner)
        val approverOfRelaxed = member("APPROVER", relaxed)
        assertEquals("REASON_REQUIRED", code(transition("social_program", pending, "return_to_draft", approverOfRelaxed.token)))
        assertEquals("REASON_REQUIRED", code(transition("social_program", pending, "cancel_pending_approval", approverOfRelaxed.token)))
        move("social_program", pending, "approve", approverOfRelaxed.token)
        assertEquals("REASON_REQUIRED", code(transition("social_program", pending, "cancel_planned", approverOfRelaxed.token)))
        move("social_program", pending, "cancel_planned", approverOfRelaxed.token, "sin presupuesto")
        assertEquals("cancelled", state("social_program", pending))
    }

    // la entrada 1 del Review Focus
    @Test
    fun `total_budget solo suma la moneda del programa y sigue su cambio de moneda`() {
        val id = socialProgram(testProject(), investor, mapOf("currency" to "PEN"))
        assertEquals(0, BigDecimal.ZERO.compareTo(total(id)))
        programBudget(id, 2026, 1000, creator = investor)
        programBudget(id, 2027, 500.5, creator = investor)
        val usd = programBudget(id, 2028, 300, "USD", creator = investor)
        assertEquals(0, BigDecimal("1500.5").compareTo(total(id)))
        val edited = edit("social_program", id, mapOf("currency" to "USD"), investor.token)
        assertEquals(HttpStatus.OK, edited.status, edited.body)
        assertEquals(0, BigDecimal("300").compareTo(total(id)))
        // la baja del presupuesto en USD (SOCIAL_INVESTMENT_MANAGER no borra presupuestos: CRU) la hace el admin
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/program_budget/records/$usd").status)
        assertEquals(0, BigDecimal.ZERO.compareTo(total(id)))
    }

    @Test
    fun `JOB-02 corrige total_budget con el motivo del trabajo y una vuelta sin cambios no escribe`() {
        val id = socialProgram(testProject(), investor, mapOf("currency" to "PEN"))
        programBudget(id, 2026, 1000, creator = investor)
        platformPatch("social_program", id, mapOf("total_budget" to 999), "prueba: total dañado")
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(0, BigDecimal("1000").compareTo(total(id)))
        val history = tree(send("GET", "/api/objects/social_program/records/$id/history", null, HttpStatus.OK))
        assertEquals(ProgramTotalsRecalculator.REASON, history.values().first()["reason"].asString())
        val updatedAt = record("social_program", id)["updatedAt"].asString()
        assertNotNull(runBlocking { job.runOnce(full = true) })
        assertEquals(updatedAt, record("social_program", id)["updatedAt"].asString())
    }

    @Test
    fun `un anio fuera de 2000 a 2100, un conteo negativo y fechas al reves son 400 OUT_OF_RANGE`() {
        val id = socialProgram(testProject(), investor)

        fun refused(
            objectName: String,
            attributes: Map<String, Any?>,
            field: String
        ) {
            val response = exchange("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), investor.token)
            assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
            assertEquals("OUT_OF_RANGE", code(response))
            assertEquals(field, tree(response.body)["errors"][0]["field"].asString())
        }
        refused("program_budget", mapOf("social_program" to id, "budget_year" to 1999, "allocated_amount" to 10, "currency" to "PEN"), "budget_year")
        refused("beneficiary_group", mapOf("social_program" to id, "name" to "Familias", "target_count" to -1), "target_count")
        refused("social_initiative", mapOf("social_program" to id, "name" to "Riego", "start_date" to "2026-06-01", "end_date" to "2026-01-01"), "end_date")
        val indicator = programIndicator(id, investor)
        refused(
            "program_measurement",
            mapOf("program_indicator" to indicator, "period_start_date" to "2026-04-01", "period_end_date" to "2026-03-31", "measured_value" to 1),
            "period_end_date"
        )
    }

    @Test
    fun `una medicion repetida en el periodo es 409 y una medicion no se cambia ni se borra`() {
        val indicator = programIndicator(socialProgram(testProject(), investor), investor)
        val measurement = programMeasurement(indicator, creator = investor)
        val repeated =
            exchange(
                "POST",
                "/api/objects/program_measurement/records",
                mapOf(
                    "attributes" to
                        mapOf("program_indicator" to indicator, "period_start_date" to "2026-01-01", "period_end_date" to "2026-02-28", "measured_value" to 20)
                ),
                investor.token
            )
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
        // appendOnly (RQ-AUD-003): la plataforma lo rechaza también al admin
        assertEquals(HttpStatus.CONFLICT, edit("program_measurement", measurement, mapOf("notes" to "corrección")).status)
        assertEquals(HttpStatus.CONFLICT, exchange("DELETE", "/api/objects/program_measurement/records/$measurement").status)
        // decisión 9 del plan 5.2b: un indicador con mediciones no se borra (409 de wasichai, AppendOnlyReferences)
        assertEquals(HttpStatus.CONFLICT, exchange("DELETE", "/api/objects/program_indicator/records/$indicator").status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/program_indicator/records/$indicator").status)
    }

    @Test
    fun `un grupo de beneficiarios con una iniciativa de otro programa es 409 PROGRAM_MISMATCH`() {
        val mine = socialProgram(testProject(), investor)
        val other = socialProgram(testProject(), investor)
        val initiative = socialInitiative(other, investor)
        val refused =
            exchange(
                "POST",
                "/api/objects/beneficiary_group/records",
                mapOf("attributes" to mapOf("social_program" to mine, "social_initiative" to initiative, "name" to "Familias")),
                investor.token
            )
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PROGRAM_MISMATCH", code(refused))
        val group = beneficiaryGroup(other, initiative, investor)
        val moved = edit("beneficiary_group", group, mapOf("social_program" to mine), investor.token)
        assertEquals("PROGRAM_MISMATCH", code(moved))
    }

    @Test
    fun `el envio avisa a los APPROVER asignados y la aprobacion resuelve el aviso`() {
        val other = member("GIS_ANALYST")
        // los destinatarios salen de las asignaciones vigentes al enviar: el APPROVER se asigna antes (approver es lazy)
        val approver = this.approver
        val id = submitted()
        assertTrue(pendingFor(approver.token, id))
        assertFalse(pendingFor(other.token, id))
        assertFalse(pendingFor(investor.token, id))
        move("social_program", id, "approve", approver.token)
        assertFalse(pendingFor(approver.token, id))
    }
}
