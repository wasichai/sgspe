package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// WF-E1 con AttentionWriteGuard (workflows.md, especificación de F4 §3.2): los casos propios de la queja que la matriz de
// workflows no cubre. la anónima sin identidad, la confidencialidad (receptor, marca protegida y quién la pone, D-3),
// atender y resolver con datos, la queja grave con su doble llave, la reapertura sin conformidad, la respuesta comunicada y el
// duplicado
class GrievanceWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("GRIEVANCE_MANAGER") }
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER") }

    private fun create(
        attributes: Map<String, Any?>,
        account: Account? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/grievance/records",
            mapOf("attributes" to attentionAttributes(testProject(), account, "QJ-") + attributes),
            account?.token ?: token,
            withReason("alta de prueba")
        )

    // una queja llevada por el gestor de quejas hasta in_action
    private fun inAction(attributes: Map<String, Any?> = emptyMap()): String {
        val id = grievance(testProject(), attributes = attributes)
        for (step in listOf("acknowledge", "assess", "start_action")) move("grievance", id, step, manager.token, step)
        return id
    }

    @Test
    fun `una queja anonima no lleva persona`() {
        val response = create(mapOf("anonymous" to true, "person" to person()))
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("ANONYMOUS_WITH_IDENTITY", code(response))
        assertEquals(listOf("person"), problemFields(response.body))
    }

    @Test
    fun `una confidencial la recibe quien la registra, salvo con READ_CONFIDENTIAL`() {
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val foreign = mapOf("confidential" to true, "received_by_user_id" to other.id, "received_by_user_email" to other.email)
        val response = create(foreign + mapOf("code" to uniqueName("QJ-")), officer)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("CONFIDENTIAL_RECEIVER_REQUIRED", code(response))
        assertEquals(listOf("received_by_user_id"), problemFields(response.body))
        assertEquals(HttpStatus.CREATED, create(foreign, manager).status)
    }

    @Test
    fun `quitar la marca o cambiar el responsable de una confidencial exige READ_CONFIDENTIAL`() {
        val id = grievance(testProject(), officer, mapOf("confidential" to true))
        val response = edit("grievance", id, mapOf("confidential" to false), officer.token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals("ACTION_REQUIRED", code(response))
        assertEquals(listOf("confidential"), problemFields(response.body))
        val owner = edit("grievance", id, mapOf("owner_user_id" to manager.id, "owner_user_email" to manager.email), officer.token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, owner.status, owner.body)
        assertEquals("ACTION_REQUIRED", code(owner))
        assertEquals(listOf("owner_user_id"), problemFields(owner.body))
        assertEquals(HttpStatus.OK, edit("grievance", id, mapOf("confidential" to false), manager.token, withReason()).status)
    }

    // D-3: marcar confidencial una queja existente lo hace su receptor, su responsable o quien tiene READ_CONFIDENTIAL; un
    // tercero recibe 403 CONFIDENTIAL_MARK_FORBIDDEN, también si se pone de responsable en la misma escritura
    @Test
    fun `marcar confidencial una queja existente lo hace su receptor, su responsable o READ_CONFIDENTIAL`() {
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val id = grievance(testProject(), officer)
        val refused = edit("grievance", id, mapOf("confidential" to true), other.token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("CONFIDENTIAL_MARK_FORBIDDEN", code(refused))
        assertEquals(listOf("confidential"), problemFields(refused.body))
        val owned =
            edit("grievance", id, mapOf("confidential" to true, "owner_user_id" to other.id, "owner_user_email" to other.email), other.token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, owned.status, owned.body)
        assertEquals("CONFIDENTIAL_MARK_FORBIDDEN", code(owned))
        assertEquals(HttpStatus.OK, edit("grievance", id, mapOf("confidential" to true), officer.token, withReason()).status)
        val managed = grievance(testProject(), officer)
        assertEquals(HttpStatus.OK, edit("grievance", managed, mapOf("confidential" to true), manager.token, withReason()).status)
    }

    // un PUT genérico como el del portal: lo guardado sin los derivados, más los cambios (wasichai vacía lo que no recibe)
    private fun putWithoutDerived(
        id: String,
        changes: Map<String, Any?>,
        account: Account
    ): Response {
        val stored = attributesOf(record("grievance", id, account.token)) - derivedFields("grievance")
        return exchange("PUT", "/api/objects/grievance/records/$id", mapOf("attributes" to stored + changes), account.token, withReason())
    }

    private fun dueDates(id: String): Pair<Any?, Any?> = attributesOf(record("grievance", id)).let { it["ack_due_date"] to it["resolution_due_date"] }

    // ronda 2: la reposición de derivados (DerivedFieldsKeeper) no depende de que quien escribe siga viendo la QCS. el receptor
    // marca con un PUT que omite los derivados, y el responsable marca y pasa la queja a otra persona en el mismo PUT, con lo
    // que deja de verla: en los dos casos los plazos se conservan (leídos como ADMIN)
    @Test
    fun `marcar confidencial con un PUT sin derivados conserva los plazos aunque quien escribe deje de verla`() {
        val received = grievance(testProject(), officer)
        val dates = dueDates(received)
        assertTrue(dates.first != null && dates.second != null, dates.toString())
        val marked = putWithoutDerived(received, mapOf("confidential" to true), officer)
        assertEquals(HttpStatus.OK, marked.status, marked.body)
        assertEquals(dates, dueDates(received))

        val owner = member("COMMUNITY_RELATIONS_OFFICER")
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val owned = grievance(testProject(), officer, mapOf("owner_user_id" to owner.id, "owner_user_email" to owner.email))
        val before = dueDates(owned)
        val handed = putWithoutDerived(owned, mapOf("confidential" to true, "owner_user_id" to other.id, "owner_user_email" to other.email), owner)
        assertEquals(HttpStatus.OK, handed.status, handed.body)
        val after = attributesOf(record("grievance", owned))
        assertEquals(true to other.id, after["confidential"] to after["owner_user_id"])
        assertEquals(before, dueDates(owned))
    }

    @Test
    fun `atender exige la severidad y resolver la resolucion`() {
        val id = grievance(testProject(), attributes = mapOf("severity" to null))
        for (step in listOf("acknowledge", "assess")) move("grievance", id, step, manager.token, step)
        val attend = transition("grievance", id, "start_action", manager.token, "atender")
        assertEquals(HttpStatus.BAD_REQUEST, attend.status, attend.body)
        assertEquals("FIELD_REQUIRED", code(attend))
        assertEquals(listOf("severity"), problemFields(attend.body))
        val unresolved = inAction(mapOf("resolution" to null))
        assertEquals(listOf("resolution"), problemFields(transition("grievance", unresolved, "resolve", manager.token, "resolver").body))
    }

    @Test
    fun `una queja grave la resuelve quien tiene APPROVE, salvo con la politica apagada`() {
        val id = inAction(mapOf("severity" to "ALTA"))
        val response = transition("grievance", id, "resolve", manager.token, "resolver")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(response))
        move("grievance", id, "resolve_high_severity", member("APPROVER").token, "resolución de la queja grave")
        assertEquals("resolved", state("grievance", id))
        val relaxed = project(mapOf("policy_high_severity_requires_approver" to false))
        val gvm = member("GRIEVANCE_MANAGER", relaxed)
        val other = grievance(relaxed, attributes = mapOf("severity" to "CRITICA"))
        for (step in listOf("acknowledge", "assess", "start_action", "resolve")) move("grievance", other, step, gvm.token, step)
    }

    @Test
    fun `la consulta en seco de resolve_high_severity exige APPROVE`() {
        val id = inAction(mapOf("severity" to "ALTA"))
        // el gestor de quejas no tiene APPROVE: la doble llave lo bloquea, sin campo (los roles los explica la lista de
        // transiciones de wasichai, no la consulta en seco)
        val refused = check(id, "resolve_high_severity", manager.token)
        assertEquals(false, refused["allowed"].asBoolean())
        assertEquals(listOf("ACTION_REQUIRED" to null), blocking(refused).filter { it.first == "ACTION_REQUIRED" })
        // quien tiene APPROVE no tropieza con ella
        val approver = check(id, "resolve_high_severity", member("APPROVER").token)
        assertEquals(emptyList<String>(), blocking(approver).map { it.first }.filter { it == "ACTION_REQUIRED" })
        assertEquals("in_action", state("grievance", id))
    }

    @Test
    fun `no se reabre una queja con conformidad`() {
        val id = inAction(mapOf("complainant_conformity_given" to true))
        move("grievance", id, "resolve", manager.token, "resolver")
        val response = transition("grievance", id, "reopen", manager.token, "reabrir")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("CONFORMITY_GIVEN", code(response))
    }

    @Test
    fun `rechazar exige la respuesta comunicada`() {
        val id = grievance(testProject(), attributes = mapOf("responded_at" to null))
        val response = transition("grievance", id, "reject_received", manager.token, "rechazo")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("responded_at"), problemFields(response.body))
    }

    @Test
    fun `una queja no es duplicado de si misma`() {
        val id = grievance(testProject())
        val response = edit("grievance", id, mapOf("duplicate_of" to id), token, withReason())
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("INVALID_LINK", code(response))
    }

    // POST /api/sgspe/transition-checks de `transition` sobre la queja `id`, como `token`
    private fun check(
        id: String,
        transition: String,
        token: String
    ): JsonNode =
        tree(
            send(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "grievance", "recordId" to id, "transition" to transition),
                HttpStatus.OK,
                token
            )
        )

    // los bloqueos de una consulta en seco: código y campo
    private fun blocking(check: JsonNode): List<Pair<String, String?>> =
        check["blocking"].values().map { it["code"].asString() to it["field"]?.takeUnless { f -> f.isNull }?.asString() }
}
