package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// WF-P (workflows.md, especificación de F5 §3.3 a §3.5, §5): las reglas de InvestmentWriteGuard por la API genérica, por la
// consulta en seco y por el comando de entrega. cada tarea del plan 5.2a suma sus casos al final
class SocialContributionWorkflowApiTest : ContributionApiTest() {
    private fun idOf(response: Response): String = tree(response.body)["id"].asString()

    // decisión 1: después de aprobar los ítems no cambian; un cambio es otro aporte
    @Test
    fun `un item nuevo, cambiado o borrado despues de aprobar es ITEMS_FROZEN`() {
        val id = requestedContribution()
        val item = idOf(itemFor(id, 120))
        move("social_contribution", id, "approve", member("APPROVER").token, "aprobación")
        val added = itemFor(id, 50)
        assertEquals(HttpStatus.CONFLICT, added.status, added.body)
        assertEquals("ITEMS_FROZEN", code(added))
        assertEquals(listOf("social_contribution"), problemFields(added.body))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("line_amount" to 500))))
        val deleted = exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token)
        assertEquals(HttpStatus.CONFLICT, deleted.status, deleted.body)
        assertEquals("ITEMS_FROZEN", code(deleted))
    }

    @Test
    fun `un item no cambia de aporte, y en requested se cambia y se borra`() {
        val id = requestedContribution()
        val other = requestedContribution()
        val item = idOf(itemFor(id, 80))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("social_contribution" to other))))
        assertEquals(HttpStatus.OK, edit("contribution_item", item, mapOf("line_amount" to 90)).status)
        assertTrue(exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token).status.is2xxSuccessful)
    }

    // Review Focus 3 y elección 7: el PUT del portal reenvía lo leído; solo cambiar un término juzgado es CONTRIBUTION_FROZEN
    @Test
    fun `cambiar la moneda o la campana despues de aprobar es CONTRIBUTION_FROZEN y reenviar lo leido pasa`() {
        val project = testProject()
        val id = approvedWithItems(project)
        val currency = edit("social_contribution", id, mapOf("currency" to "USD"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, currency.status, currency.body)
        assertEquals("CONTRIBUTION_FROZEN", code(currency))
        assertEquals(listOf("currency"), problemFields(currency.body))
        val campaign = edit("social_contribution", id, mapOf("exploration_campaign" to cappedCampaign(project, 1000)), headers = withReason())
        assertEquals("CONTRIBUTION_FROZEN", code(campaign))
        assertEquals(listOf("exploration_campaign"), problemFields(campaign.body))
        assertEquals(HttpStatus.OK, edit("social_contribution", id, emptyMap(), headers = withReason()).status)
        val other = edit("social_contribution", id, mapOf("occasion" to "Día de la comunidad", "beneficiaries_count" to 120), headers = withReason())
        assertEquals(HttpStatus.OK, other.status, other.body)
    }

    @Test
    fun `beneficiaries_count negativo es OUT_OF_RANGE`() {
        val id = requestedContribution()
        val refused = edit("social_contribution", id, mapOf("beneficiaries_count" to -3), headers = withReason())
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("beneficiaries_count"), problemFields(refused.body))
    }

    // ---- Tarea 4: P4, P5, P6, P7 y P9 ----

    @Test
    fun `aprueban APPROVER y SOCIAL_MANAGER, pero no quien registro el aporte`() {
        val project = testProject()
        for (role in listOf("APPROVER", "SOCIAL_MANAGER")) {
            val id = requestedContribution(project)
            move("social_contribution", id, "approve", member(role, project).token, "aprobación")
            assertEquals("approved", state("social_contribution", id), role)
        }
        val manager = member("SOCIAL_MANAGER", project)
        val own = requestedContribution(project, creator = manager)
        val refused = transition("social_contribution", own, "approve", manager.token, "aprobación")
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(refused))
        assertEquals(listOf("record"), problemFields(refused.body))
        assertEquals(HttpStatus.OK, transition("social_contribution", own, "approve", member("SOCIAL_MANAGER", project).token, "aprobación").status)
    }

    // decisión 13 del plan 5.2a: los dos roles de approve tienen APPROVE, así que P4 se ve en la consulta en seco
    @Test
    fun `sin la accion APPROVE la consulta en seco da ACTION_REQUIRED`() {
        val id = requestedContribution()
        val check = dryRun(id, "approve", member("SOCIAL_INVESTMENT_MANAGER"))
        assertTrue(check["blocking"].values().any { it["code"].asString() == "ACTION_REQUIRED" && it["field"].asString() == "record" }, check.toString())
    }

    @Test
    fun `sin items es ITEMS_REQUIRED y sin contribution_type es FIELD_REQUIRED`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val empty = requestedContribution(project, items = emptyList())
        val noItems = transition("social_contribution", empty, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, noItems.status, noItems.body)
        assertEquals("ITEMS_REQUIRED", code(noItems))
        assertEquals(listOf("contribution_item"), problemFields(noItems.body))
        val untyped = requestedContribution(project, attributes = mapOf("contribution_type" to null))
        val noType = transition("social_contribution", untyped, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.BAD_REQUEST, noType.status, noType.body)
        assertEquals("FIELD_REQUIRED", code(noType))
        assertEquals(listOf("contribution_type"), problemFields(noType.body))
        assertEquals("requested", state("social_contribution", empty))
    }

    @Test
    fun `approve_direct con la aprobacion interna activa es INTERNAL_APPROVAL_REQUIRED`() {
        val strict = testProject()
        val id = requestedContribution(strict)
        val refused = transition("social_contribution", id, "approve_direct", registrar(strict).token, "aprobación directa")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        assertEquals(listOf("project"), problemFields(refused.body))
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val direct = requestedContribution(relaxed)
        move("social_contribution", direct, "approve_direct", registrar(relaxed).token, "aprobación directa")
        assertEquals("approved", state("social_contribution", direct))
    }

    // elección 9: la sinergia no es un lineamiento, así que la justificación no la cubre
    @Test
    fun `una sinergia exige un acuerdo vigente, tambien con justificacion`() {
        val project = testProject()
        val approver = member("APPROVER", project)

        fun approve(extra: Map<String, Any?>): Response {
            val attributes =
                mapOf("has_institutional_synergy" to true, "guideline_deviation_justification" to "Convenio en trámite con la municipalidad.") + extra
            return transition("social_contribution", requestedContribution(project, attributes = attributes), "approve", approver.token, "aprobación")
        }
        for (extra in listOf(emptyMap(), mapOf("agreement" to agreement(project)))) {
            val refused = approve(extra)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(refused))
            assertEquals(listOf("agreement"), problemFields(refused.body))
        }
        val expired = liveAgreement(project).also { platformPatch("agreement", it, mapOf("validity_status" to "VENCIDO")) }
        assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(approve(mapOf("agreement" to expired))))
        assertEquals(HttpStatus.OK, approve(mapOf("agreement" to liveAgreement(project))).status)
    }

    // ---- Tarea 5: P2 y P3 ----

    private fun topic(parent: String? = null): String =
        createRecord(
            "topic",
            mapOf("code" to uniqueName("TEM-"), "name" to "Tema de prueba", "active" to true) + listOfNotNull(parent?.let { "parent_topic" to it })
        )

    // un proyecto con `topic` priorizado (project_priority_topic, RF-INV-002)
    private fun prioritizing(topic: String): String {
        val project = project()
        val linked = linkWithReason("project", project, "project_priority_topic", topic)
        assertEquals(HttpStatus.NO_CONTENT, linked.status, linked.body)
        return project
    }

    @Test
    fun `mano de obra y dinero en una donacion bloquean sin justificacion y pasan con ella`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val labor = requestedContribution(project, items = listOf(200 to "MANO DE OBRA"))
        val refused = transition("social_contribution", labor, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PAID_LABOR", code(refused))
        assertEquals(listOf("contribution_item"), problemFields(refused.body))
        val money = requestedContribution(project, items = listOf(500 to "DINERO"), attributes = mapOf("contribution_type" to "DONACION"))
        assertEquals("MONETARY_DONATION", code(transition("social_contribution", money, "approve", approver.token, "aprobación")))
        val justified =
            requestedContribution(
                project,
                items = listOf(200 to "MANO DE OBRA"),
                attributes =
                    mapOf("guideline_deviation_justification" to "Faena comunal.")
            )
        val warnings = dryRun(justified, "approve", approver)["warnings"].values().map { it["code"].asString() }
        assertTrue("PAID_LABOR" in warnings, warnings.toString())
        move("social_contribution", justified, "approve", approver.token, "aprobación")
    }

    @Test
    fun `un tema fuera de las prioridades es PRIORITY_NOT_MET y uno que desciende de una prioridad pasa`() {
        val education = topic()
        val project = prioritizing(education)
        val approver = member("APPROVER", project)
        val outside = requestedContribution(project, attributes = mapOf("topic" to topic()))
        val refused = transition("social_contribution", outside, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PRIORITY_NOT_MET", code(refused))
        assertEquals(listOf("topic"), problemFields(refused.body))
        val inside = requestedContribution(project, attributes = mapOf("topic" to topic(parent = topic(parent = education))))
        move("social_contribution", inside, "approve", approver.token, "aprobación")
    }

    @Test
    fun `un proyecto sin temas priorizados solo advierte PRIORITIES_NOT_CONFIGURED`() {
        val id = requestedContribution()
        val approver = member("APPROVER")
        val check = dryRun(id, "approve", approver)
        assertTrue(check["warnings"].values().any { it["code"].asString() == "PRIORITIES_NOT_CONFIGURED" }, check.toString())
        assertTrue(check["allowed"].asBoolean(), check.toString())
        move("social_contribution", id, "approve", approver.token, "aprobación")
    }

    // ---- Tarea 7: P1 ----

    @Test
    fun `con un tope que bloquea approve por APPROVER se rechaza, por SOCIAL_MANAGER pasa y approve_direct se rechaza`() {
        val project = project(mapOf("policy_internal_approval" to false))
        val over = mapOf("exploration_campaign" to cappedCampaign(project, 500))
        val id = requestedContribution(project, items = listOf(400 to "BIEN", 200 to "SERVICIO"), attributes = over)
        val refused = transition("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        assertEquals(listOf("exploration_campaign"), problemFields(refused.body))
        assertEquals(HttpStatus.OK, transition("social_contribution", id, "approve", member("SOCIAL_MANAGER", project).token, "sobre el tope").status)
        val direct = requestedContribution(project, items = listOf(100 to "BIEN"), attributes = over)
        val refusedDirect = transition("social_contribution", direct, "approve_direct", registrar(project).token, "aprobación directa")
        assertEquals(HttpStatus.CONFLICT, refusedDirect.status, refusedDirect.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refusedDirect))
    }

    @Test
    fun `el tope cuenta un compromiso comprometido y otro aporte aprobado de la misma campana`() {
        val project = testProject()
        val campaign = mapOf("exploration_campaign" to cappedCampaign(project, 1000))
        approvedCommitment(project, attributes = campaign + ("amount" to 400), start = false)
        approvedWithItems(project, items = listOf(300 to "BIEN"), attributes = campaign)
        val approver = member("APPROVER", project)
        val over = requestedContribution(project, items = listOf(400 to "BIEN"), attributes = campaign)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(transition("social_contribution", over, "approve", approver.token, "aprobación")))
        val fits = requestedContribution(project, items = listOf(300 to "BIEN"), attributes = campaign)
        move("social_contribution", fits, "approve", approver.token, "aprobación")
    }

    // Review Focus 1: el monto que se juzga es el de los ítems, aunque el total guardado diga otra cosa
    @Test
    fun `el tope suma los items leidos en la transaccion, no el total guardado`() {
        val project = testProject()
        val id = requestedContribution(project, items = listOf(600 to "BIEN"), attributes = mapOf("exploration_campaign" to cappedCampaign(project, 500)))
        platformPatch("social_contribution", id, mapOf("total_amount" to 0))
        assertEquals("SUPPORT_CAP_EXCEEDED", code(transition("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")))
    }

    @Test
    fun `con la politica apagada el tope superado es una advertencia y lo aprobado queda escrito`() {
        val project = project(mapOf("policy_cap_blocks_approval" to false))
        val id = requestedContribution(project, items = listOf(600 to "BIEN"), attributes = mapOf("exploration_campaign" to cappedCampaign(project, 500)))
        val approver = member("APPROVER", project)
        val check = dryRun(id, "approve", approver)
        assertTrue(check["warnings"].values().any { it["code"].asString() == "SUPPORT_CAP_EXCEEDED" }, check.toString())
        move("social_contribution", id, "approve", approver.token, "aprobación")
        assertEquals(0, java.math.BigDecimal("600").compareTo(record("social_contribution", id)["attributes"]["total_amount"].decimalValue()))
    }
}
