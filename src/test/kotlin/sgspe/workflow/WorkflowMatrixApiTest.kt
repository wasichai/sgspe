package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.engagement.EngagementWriteGuard
import java.io.File

// la matriz de los quince workflows de F2 a F4 (workflows.md), armada desde model/workflows/*.json: por cada transición, (1) cada
// rol de su lista la aplica desde su estado de origen y el registro queda en el destino, (2) un rol fuera de la lista que
// tiene UPDATE (o `u`) sobre el objeto recibe 403 y el listado le dice `requires role ...`, y (3) desde un estado que no es
// el de origen responde 409 con el registro sin cambios. las expectativas salen de esos mismos JSON, así que un cambio de
// roles o de estados en ellos cambia los casos y no los hace fallar: lo que la matriz prueba es que wasichai aplica los
// workflows tal como están escritos. que los JSON coincidan con docs/workflows.md lo prueba model/test_model.py, y los
// conteos fijos de WORKFLOW_TRANSITIONS impiden que una carga vacía o truncada deje la matriz sin casos. cada paso lleva
// motivo, para que TransitionReasonGuard (plan 2.2) y `requiresReason` no rompan esta clase, y cada registro trae lo que
// piden las guardas de 2.2 y 3.2 (como `activatable()` y `profileable()` de WF-A y WF-B; `close_light` usa un tipo no
// formal, ver LIGHT_CLOSE_TYPE; los compromisos traen D1, D2 y una aprobación de otra persona, D4; los acuerdos son
// convenios firmados y vigentes; los candidatos, apiOnly, los crea la plataforma; las QCS, los riesgos, los incidentes y las
// alertas traen lo que piden las guardas de 4.2a y 4.2b, con F4ApiTest)
class WorkflowMatrixApiTest : F4ApiTest() {
    private data class Transition(
        val name: String,
        val from: String,
        val to: String,
        val roles: List<String>
    )

    private data class Workflow(
        val objectName: String,
        val initial: String,
        val finals: Set<String>,
        val states: List<String>,
        val transitions: List<Transition>
    ) {
        // quienes llevan el registro por sus estados intermedios; el primero es quien lo crea
        val drivers: List<String> get() = DRIVERS.getValue(objectName)

        fun key(rule: Transition) = "$objectName.${rule.name}"
    }

    private val accounts = mutableMapOf<String, Account>()
    private val assigned = mutableSetOf<Pair<String, String>>()

    // el proyecto de la prueba, sin aprobación interna: approve_direct solo pasa así (D8, plan 3.2) y approve no la mira
    private var relaxed: String? = null

    override fun testProject(): String = relaxed ?: project(mapOf("policy_internal_approval" to false)).also { relaxed = it }

    // el compromiso que cubre a los candidatos de la prueba (duplicate_of)
    private val covering: String by lazy { commitment(testProject()) }

    // un usuario por rol, asignado al proyecto de la prueba (o al proyecto que se mueve, en WF-A)
    private fun actor(
        workflow: Workflow,
        role: String,
        record: String?
    ): Account {
        val account = accounts.getOrPut(role) { userWithRole(role) }
        val scope = if (workflow.objectName == "project") record!! else testProject()
        if (assigned.add(role to scope)) assign(account, scope, role)
        return account
    }

    // un registro nuevo en el estado inicial, con lo que piden las guardas de 2.2
    private fun create(
        workflow: Workflow,
        creatorRole: String,
        engagementType: String = FORMAL_TYPE
    ): String =
        when (workflow.objectName) {
            // un proyecto que cumple las precondiciones de `active` (ASM-062); lo crea el admin, como en WF-A
            "project" -> {
                val project = project(mapOf("working_crs" to "EPSG 32718"))
                createRecord("project_party", mapOf("project" to project, "organization" to fixture("organization").id, "party_role" to "CLIENTE"))
                project
            }
            // un grupo de interés con lo que `profile` exige (RF-STK-003), con quien lo crea de responsable
            "stakeholder" -> {
                val owner = actor(workflow, creatorRole, null)
                fixture(
                    "stakeholder",
                    mapOf(
                        "stakeholder_type" to "ORGANIZACION COMUNAL",
                        "stakeholder_scope" to "AIDS",
                        "inclusion_reason" to "Organización del área de influencia directa",
                        "owner_user_id" to owner.id,
                        "owner_user_email" to owner.email
                    ),
                    owner
                ).id
            }
            // una reunión con fecha, acta, motivo de cancelación y un asistente: lo que piden register_held, close y cancel_*
            "engagement" -> {
                val creator = actor(workflow, creatorRole, null)
                val engagement =
                    fixture(
                        "engagement",
                        mapOf(
                            "engagement_type" to engagementType,
                            "title" to "Reunión con la junta",
                            "scheduled_at" to "2026-10-08T15:00:00Z",
                            "held_at" to "2026-10-08T16:00:00Z",
                            "minutes" to "Acuerdos de la reunión.",
                            "cancel_reason" to "Lluvias intensas"
                        ),
                        creator
                    ).id
                createRecord(
                    "engagement_participant",
                    mapOf("engagement" to engagement, "represented_group" to "Junta directiva", "represented_people_count" to 3, "attended" to true),
                    token = creator.token
                )
                engagement
            }
            "field_visit" -> fixture("field_visit", account = actor(workflow, creatorRole, null)).id
            // F3: un compromiso que cumple D1 y D2, con un responsable y un verificador que no lo mueven
            "commitment" ->
                commitment(
                    testProject(),
                    owner = accounts.getOrPut("\u0000owner") { userWithRole("READ_ONLY") },
                    verifier = accounts.getOrPut("\u0000verifier") { userWithRole("READ_ONLY") },
                    creator = actor(workflow, creatorRole, null)
                )
            "agreement" -> agreement(testProject(), actor(workflow, creatorRole, null))
            // apiOnly: la plataforma, como el alta humana del plan 3.2, con el compromiso que lo cubre: mark_duplicate exige
            // duplicate_of (CandidateRules), que en el portal escribe POST .../duplicate
            "commitment_candidate" ->
                platformCreate(
                    "commitment_candidate",
                    mapOf("project" to testProject(), "proposed_text" to "Arreglar el canal de riego", "ai_generated" to false, "duplicate_of" to covering)
                )
            "task" -> task(testProject(), actor(workflow, creatorRole, null))
            // F4: quien crea una QCS queda como quien la recibió; el responsable del riesgo no lo mueve
            "grievance" -> grievance(testProject(), actor(workflow, creatorRole, null))
            "inquiry" -> inquiry(testProject(), actor(workflow, creatorRole, null))
            "stakeholder_request" -> stakeholderRequest(testProject(), actor(workflow, creatorRole, null))
            "social_risk" ->
                socialRisk(
                    testProject(),
                    actor(workflow, creatorRole, null),
                    owner = accounts.getOrPut("\u0000owner") { userWithRole("READ_ONLY") }
                )
            "early_warning" -> earlyWarning(testProject(), actor(workflow, creatorRole, null))
            "incident" -> incident(testProject(), actor(workflow, creatorRole, null))
            else -> error("sin registro de prueba para ${workflow.objectName}")
        }

    // un registro nuevo llevado hasta `state` por el camino de pathTo, cada paso por el primer rol de su lista que maneja el objeto
    // y con lo que pide (prepare); si se da `next`, también listo para aplicarla
    private fun recordAt(
        workflow: Workflow,
        state: String,
        creatorRole: String,
        engagementType: String = FORMAL_TYPE,
        next: Transition? = null
    ): String {
        val id = create(workflow, creatorRole, engagementType)
        for (step in checkNotNull(pathTo(workflow, state)) { "${workflow.objectName}: el estado $state no se alcanza" }) {
            val role = workflow.drivers.first { it in step.roles }
            prepare(workflow, id, step)
            val response = perform(workflow, id, step, actor(workflow, role, id))
            assertTrue(response.status.is2xxSuccessful, "${workflow.key(step)}: ${response.status} ${response.body}")
        }
        if (next != null) prepare(workflow, id, next)
        return id
    }

    // lo que una transición pide antes de aplicarse y la API genérica no da: register_fulfillment de WF-D exige una entrega con
    // conformidad y, en un compromiso MATERIAL, su acta de entrega y su foto (D9), que el admin registra con el comando de
    // entregas del plan 3.2; close_unfulfilled de WF-E3, el compromiso creado al aceptar ya cancelado (plan 4.2a); y en WF-F
    // (plan 4.2b) la decisión de tratamiento, una medida con responsable y una revisión residual (RiskWriteGuard)
    private fun prepare(
        workflow: Workflow,
        id: String,
        rule: Transition
    ) {
        if (workflow.objectName == "social_risk" && state(workflow.objectName, id) == rule.from) {
            prepareRisk(id, rule)
            return
        }
        if (workflow.key(rule) == "stakeholder_request.close_unfulfilled" && state(workflow.objectName, id) == rule.from) {
            move("commitment", record(workflow.objectName, id)["attributes"]["created_commitment"].asString(), "cancel_draft", reason = TEXT)
            return
        }
        if (workflow.key(rule) != "commitment.register_fulfillment") return
        val evidence = listOf(evidence(testProject(), "ACTA DE ENTREGA"), evidence(testProject(), "FOTO", "PUBLICA"))
        val attributes = mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva")
        val delivery =
            exchange("POST", "/api/sgspe/commitments/$id/deliveries", mapOf("attributes" to attributes, "evidence" to evidence), token, withReason(TEXT))
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
    }

    // lo que pide RiskWriteGuard (plan 4.2b), escrito por el admin: mitigar, la decisión MITIGAR y una medida con responsable;
    // monitorear, la decisión MONITOREAR y una revisión residual por el endpoint de revisiones
    private fun prepareRisk(
        id: String,
        rule: Transition
    ) {
        val decision =
            when (rule.name) {
                "decide_mitigation" -> "MITIGAR"
                "decide_monitoring" -> "MONITOREAR"
                else -> null
            }
        if (decision != null) {
            val edited = edit("social_risk", id, mapOf("treatment_decision" to decision), headers = withReason(TEXT))
            assertEquals(HttpStatus.OK, edited.status, edited.body)
        }
        if (rule.name in setOf("decide_mitigation", "resume_mitigation")) mitigationMeasure(id, accounts.getOrPut("\u0000owner") { userWithRole("READ_ONLY") })
        if (rule.name in setOf("decide_monitoring", "start_monitoring")) {
            val residual = assessRisk(id, token, residual = true)
            assertEquals(HttpStatus.CREATED, residual.status, residual.body)
        }
    }

    // una revisión de riesgo por su endpoint (POST /api/sgspe/social-risks/{id}/assessments): la primera aplica assess
    private fun assessRisk(
        id: String,
        token: String,
        residual: Boolean = false
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/social-risks/$id/assessments",
            mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 3, "residual" to residual)),
            token,
            withReason(TEXT)
        )

    // una transición como la aplica el portal: accept de WF-K por su comando, que crea el compromiso en draft con los valores
    // que revisa la persona (CommandTransitionGuard rechaza la API genérica); las reclasificaciones de F4 por el suyo, cuando el
    // registro está en su estado de origen (fuera de él, el comando elegiría la transición de otro estado: la API genérica
    // responde 409 antes de las guardas); las demás, por la API genérica
    private fun perform(
        workflow: Workflow,
        id: String,
        rule: Transition,
        who: Account
    ): Response {
        if (rule.name.startsWith("close_reclassified") && state(workflow.objectName, id) == rule.from) return reclassify(workflow, id, who)
        if (workflow.key(rule) == "stakeholder_request.accept") return accept(id, who)
        // assess de WF-F por el endpoint de revisiones, desde identified; fuera de él, por la API genérica, que responde 409
        if (workflow.key(rule) == "social_risk.assess" && state(workflow.objectName, id) == rule.from) return assessRisk(id, who.token)
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
        return exchange(
            "POST",
            "/api/sgspe/commitment-candidates/$id/accept",
            mapOf("attributes" to reviewed),
            who.token,
            ifMatch(workflow.objectName, id, who.token) + withReason(TEXT)
        )
    }

    // la reclasificación de una QCS a otro tipo (POST /api/sgspe/{tipo}/{id}/reclassify), con el código que pide el registro nuevo
    private fun reclassify(
        workflow: Workflow,
        id: String,
        who: Account
    ): Response {
        val target = if (workflow.objectName == "inquiry") "stakeholder_request" else "inquiry"
        val path = mapOf("grievance" to "grievances", "inquiry" to "inquiries", "stakeholder_request" to "stakeholder-requests").getValue(workflow.objectName)
        return exchange(
            "POST",
            "/api/sgspe/$path/$id/reclassify",
            mapOf("targetType" to target, "attributes" to mapOf("code" to uniqueName("RC-"))),
            who.token,
            ifMatch(workflow.objectName, id, who.token) + withReason(TEXT)
        )
    }

    // la aceptación de una solicitud (POST /api/sgspe/stakeholder-requests/{id}/accept): su respuesta y el compromiso en draft
    private fun accept(
        id: String,
        who: Account
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/stakeholder-requests/$id/accept",
            mapOf(
                "creates" to "commitment",
                "response" to "Se atenderá con un compromiso.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to
                    mapOf(
                        "code" to uniqueName("COM-"),
                        "title" to "Materiales para el canal de riego",
                        "amount" to 15000,
                        "currency" to "PEN"
                    )
            ),
            who.token,
            ifMatch("stakeholder_request", id, who.token) + withReason(TEXT)
        )

    // quien crea el registro de la prueba: el propio rol si puede crearlo (RU: FIELD_AGENT solo cambia lo que creó), si no el
    // primero de los que llevan el objeto. quien aprueba un compromiso nunca lo creó (D4): sus roles no tienen CREATE
    private fun creatorFor(
        workflow: Workflow,
        role: String
    ): String = if ("CREATE" in Model.roles.getValue(role)[workflow.objectName].orEmpty()) role else workflow.drivers.first()

    // `close_light` cierra sin acta solo un tipo no formal (RN-ENG-004, EngagementWriteGuard.lightClose de 2.2: con la política
    // por defecto un tipo formal responde 409); las demás transiciones usan REUNION, un tipo formal
    private fun engagementTypeFor(
        workflow: Workflow,
        rule: Transition
    ): String = if (workflow.key(rule) == "engagement.close_light") LIGHT_CLOSE_TYPE else FORMAL_TYPE

    // un rol fuera de la lista de la transición que puede actualizar el objeto: primero uno con UPDATE y, si no hay, uno con `u`
    private fun outsider(
        workflow: Workflow,
        rule: Transition
    ): String? {
        val candidates =
            Model.roles
                .filter { (role, permissions) -> role !in rule.roles && "READ" in permissions[workflow.objectName].orEmpty() }
                .mapValues { it.value.getValue(workflow.objectName) }
        return candidates.entries.firstOrNull { "UPDATE" in it.value }?.key ?: candidates.entries.firstOrNull { "u" in it.value }?.key
    }

    // el estado desde el que la transición no es válida: el inicial y, si ella sale del inicial, el primer estado no final
    // que se alcanza o, si no hay ninguno (WF-K: de `proposed` solo se sale a finales), el primer final que se alcanza
    private fun wrongState(
        workflow: Workflow,
        rule: Transition
    ): String =
        if (rule.from != workflow.initial) {
            workflow.initial
        } else {
            workflow.states.firstOrNull { it != rule.from && it !in workflow.finals && pathTo(workflow, it) != null }
                ?: workflow.states.first { it != rule.from && pathTo(workflow, it) != null }
        }

    // el camino más corto desde el estado inicial hasta `state` sin las transiciones diferidas a 2.2 ni los atajos que 2.2 guarda
    private fun pathTo(
        workflow: Workflow,
        state: String
    ): List<Transition>? {
        val usable = workflow.transitions.filter { workflow.key(it) !in DEFERRED && workflow.key(it) !in WALK_AVOIDS }
        val seen = mutableSetOf(workflow.initial)
        val queue = ArrayDeque(listOf(workflow.initial to emptyList<Transition>()))
        while (queue.isNotEmpty()) {
            val (current, path) = queue.removeFirst()
            if (current == state) return path
            for (next in usable.filter { it.from == current && seen.add(it.to) }) queue.addLast(next.to to path + next)
        }
        return null
    }

    @TestFactory
    fun `cada transicion por rol permitido, rol denegado y estado equivocado`(): List<DynamicNode> =
        WORKFLOWS.values.map { workflow ->
            val transitions = workflow.transitions.filter { workflow.key(it) !in DEFERRED }
            dynamicContainer(
                "${workflow.objectName} (${transitions.size} de ${workflow.transitions.size} transiciones)",
                transitions.map { rule -> dynamicContainer(rule.name, cases(workflow, rule)) }
            )
        }

    private fun cases(
        workflow: Workflow,
        rule: Transition
    ): List<DynamicNode> {
        val objectName = workflow.objectName
        val type = engagementTypeFor(workflow, rule)
        val allowed =
            rule.roles.map { role ->
                dynamicTest("$role aplica ${rule.name}: ${rule.from} pasa a ${rule.to}") {
                    val id = recordAt(workflow, rule.from, creatorFor(workflow, role), type, rule)
                    val response = perform(workflow, id, rule, actor(workflow, role, id))
                    assertTrue(response.status.is2xxSuccessful, "$role: ${response.status} ${response.body}")
                    assertEquals(rule.to, state(objectName, id))
                }
            }
        val outsider = outsider(workflow, rule)
        val forbidden =
            dynamicTest("un rol con UPDATE fuera de la lista recibe 403 y el listado dice requires role") {
                // sin un rol así no hay caso: todos los que actualizan el objeto están en la lista de la transición
                assumeTrue(outsider != null) { "${workflow.key(rule)}: todo rol con UPDATE sobre $objectName está en su lista" }
                val id = recordAt(workflow, rule.from, workflow.drivers.first(), type, rule)
                val who = actor(workflow, outsider!!, id)
                val response = perform(workflow, id, rule, who)
                assertEquals(HttpStatus.FORBIDDEN, response.status, "$outsider: ${response.body}")
                assertEquals(rule.from, state(objectName, id))
                assertEquals(false to "requires role ${rule.roles.joinToString(" or ")}", available(objectName, id, who.token)[rule.name], outsider)
            }
        val wrong = wrongState(workflow, rule)
        val conflict =
            dynamicTest("desde $wrong responde 409 y el registro no cambia") {
                val role = rule.roles.first()
                val id = recordAt(workflow, wrong, creatorFor(workflow, role), type)
                val who = actor(workflow, role, id)
                val response = perform(workflow, id, rule, who)
                assertEquals(HttpStatus.CONFLICT, response.status, "$role: ${response.body}")
                assertEquals(wrong, state(objectName, id))
                assertFalse(rule.name in available(objectName, id, who.token).keys, "el listado no ofrece ${rule.name} desde $wrong")
            }
        return allowed + forbidden + conflict
    }

    // un rol de la lista que no lee todos los proyectos: sin asignación al proyecto del registro, no lo ve (ASM-106)
    private fun scoped(role: String): Boolean = "READ_ALL_PROJECTS" !in Model.roles.getValue(role)["project"].orEmpty()

    // la columna «no asignado» (ASM-108, security.md §14): por workflow, la primera transición que se aplica por la API genérica,
    // como un rol de su lista que no lee todos los proyectos y está asignado a otro proyecto: 404 y el registro no cambia. las
    // de comando (aceptaciones, reclasificaciones, assess) leen el registro antes y responden igual, por su endpoint
    @TestFactory
    fun `una transicion de cada workflow como no asignado responde 404`(): List<DynamicNode> =
        WORKFLOWS.values.map { workflow ->
            val rule =
                workflow.transitions.first { rule ->
                    workflow.key(rule) !in DEFERRED && workflow.key(rule) !in BY_COMMAND && !rule.name.startsWith("close_reclassified") &&
                        rule.roles.any(::scoped) && pathTo(workflow, rule.from) != null
                }
            dynamicTest("${workflow.objectName}.${rule.name}") {
                val role = rule.roles.first(::scoped)
                val id = recordAt(workflow, rule.from, creatorFor(workflow, role), engagementTypeFor(workflow, rule), rule)
                val stranger = userWithRole(role).also { assign(it, project(), role) }
                val response = transition(workflow.objectName, id, rule.name, stranger.token, TEXT)
                assertEquals(HttpStatus.NOT_FOUND, response.status, "$role: ${response.body}")
                assertEquals(rule.from, state(workflow.objectName, id))
            }
        }

    @Test
    fun `cada transicion diferida a 2_2 o 3_2 existe en su workflow`() {
        for (key in DEFERRED + WALK_AVOIDS) {
            val (objectName, name) = key.split(".")
            assertTrue(WORKFLOWS.getValue(objectName).transitions.any { it.name == name }, "$key no existe en model/workflows/$objectName.json")
        }
    }

    @Test
    fun `cada workflow trae todas sus transiciones`() {
        assertEquals(WORKFLOW_TRANSITIONS, WORKFLOWS.mapValues { it.value.transitions.size })
    }

    @Test
    fun `los tipos de interaccion de la matriz existen y close_light usa uno no formal`() {
        val types = Model.enums.getValue("engagement_type")
        assertTrue(FORMAL_TYPE in types && LIGHT_CLOSE_TYPE in types, "tipos de la matriz fuera de enums.engagement_type: $types")
        // los tipos formales de RN-ENG-004: los de la guarda, sin copia (M11)
        val formal = EngagementWriteGuard.FORMAL_TYPES
        assertTrue(FORMAL_TYPE in formal)
        assertFalse(LIGHT_CLOSE_TYPE in formal)
    }

    @Test
    fun `los quince workflows de model estan en la matriz`() {
        val files = File("model/workflows").listFiles { file -> file.extension == "json" }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(files, WORKFLOWS.keys)
    }

    @Test
    fun `todo estado de origen se alcanza sin las transiciones diferidas`() {
        for (workflow in WORKFLOWS.values) {
            for (rule in workflow.transitions.filter { workflow.key(it) !in DEFERRED }) {
                assertNotNull(pathTo(workflow, rule.from), "${workflow.key(rule)}: ${rule.from} no se alcanza")
                assertNotNull(pathTo(workflow, wrongState(workflow, rule)), "${workflow.key(rule)}: no hay un estado equivocado alcanzable")
            }
        }
    }

    @Test
    fun `todo rol de una transicion declara UPDATE o u sobre el objeto`() {
        for (workflow in WORKFLOWS.values) {
            for (rule in workflow.transitions) {
                for (role in rule.roles) {
                    val permissions = Model.roles.getValue(role)[workflow.objectName].orEmpty()
                    assertTrue("UPDATE" in permissions || "u" in permissions, "${workflow.key(rule)}: $role no actualiza ${workflow.objectName}")
                }
            }
        }
    }

    companion object {
        private const val TEXT = "prueba de la matriz de workflows"

        // las transiciones que el portal aplica por su comando y no por la API genérica (perform)
        private val BY_COMMAND = setOf("commitment_candidate.accept", "stakeholder_request.accept", "social_risk.assess")
        private const val FORMAL_TYPE = "REUNION"
        private const val LIGHT_CLOSE_TYPE = "CONTACTO CARA A CARA"

        // las transiciones de cada workflow en workflows.md (WF-A 9, WF-B 6, WF-C 14, WF-L 4, WF-O 2, WF-D 17, WF-I 10, WF-K 3,
        // WF-N 8, WF-E1 22, WF-E2 5, WF-E3 8, WF-F 9, WF-J 6, WF-M 3)
        private val WORKFLOW_TRANSITIONS =
            mapOf(
                "project" to 9,
                "stakeholder" to 6,
                "engagement" to 14,
                "field_visit" to 4,
                "area_of_influence" to 2,
                "commitment" to 17,
                "agreement" to 10,
                "commitment_candidate" to 3,
                "task" to 8,
                "grievance" to 22,
                "inquiry" to 5,
                "stakeholder_request" to 8,
                "social_risk" to 9,
                "early_warning" to 6,
                "incident" to 3
            )

        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest.
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y perform aplica accept por su comando. de F4 se difiere close_accepted de WF-E3,
        // que solo aplica RequestClosureListener al cerrar el compromiso (sus casos, en StakeholderRequestWorkflowApiTest). las
        // reclasificaciones de WF-E1, WF-E2 y WF-E3 y accept de WF-E3 ya no se difieren (plan 4.2a): perform las aplica por su
        // comando y prepare cancela el compromiso que pide close_unfulfilled; WF-F tampoco (plan 4.2b): perform aplica assess por
        // el endpoint de revisiones y prepare escribe lo que piden mitigar y monitorear
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "stakeholder_request.close_accepted"
            )

        // atajos de WF-C que 2.2 guarda con reglas propias: el registro llega a `held` por el ciclo normal (schedule y register_held);
        // approve_direct de WF-D: un compromiso llega a `approved` con submit y approve, la doble llave de D3 y D4; y
        // resolve_high_severity de WF-E1: una queja llega a `resolved` con resolve, sin la doble llave de las graves
        val WALK_AVOIDS =
            setOf("engagement.register_held_unplanned", "engagement.close_light", "commitment.approve_direct", "grievance.resolve_high_severity")

        // quién lleva cada registro por sus estados: el primero lo crea; cada paso lo da el primero que está en la lista del paso
        val DRIVERS =
            mapOf(
                "project" to listOf("PROJECT_ADMIN"),
                "stakeholder" to listOf("STAKEHOLDER_MANAGER"),
                "engagement" to listOf("COMMUNITY_RELATIONS_OFFICER", "SOCIAL_MANAGER"),
                "field_visit" to listOf("COMMUNITY_RELATIONS_OFFICER"),
                "area_of_influence" to listOf("SOCIAL_MANAGER"),
                "commitment" to listOf("COMMITMENT_MANAGER", "APPROVER"),
                "agreement" to listOf("SOCIAL_MANAGER", "PROJECT_MANAGER"),
                "commitment_candidate" to listOf("COMMITMENT_MANAGER"),
                "task" to listOf("COMMUNITY_RELATIONS_OFFICER"),
                "grievance" to listOf("GRIEVANCE_MANAGER"),
                "inquiry" to listOf("GRIEVANCE_MANAGER"),
                // accept de WF-E3 no es de GRIEVANCE_MANAGER: lo da SOCIAL_MANAGER, para llegar a `accepted` (close_unfulfilled)
                "stakeholder_request" to listOf("GRIEVANCE_MANAGER", "SOCIAL_MANAGER"),
                "social_risk" to listOf("SOCIAL_RISK_MANAGER"),
                "early_warning" to listOf("SOCIAL_RISK_MANAGER"),
                "incident" to listOf("SOCIAL_RISK_MANAGER")
            )

        private val WORKFLOWS: Map<String, Workflow> = DRIVERS.keys.associateWith(::load)

        @Suppress("UNCHECKED_CAST")
        private fun load(objectName: String): Workflow {
            val json = JSON.readValue(File("model/workflows/$objectName.json"), Map::class.java) as Map<String, Any?>
            val definition = json["definition"] as Map<String, Any?>
            val states = definition["states"] as List<Map<String, Any?>>
            val transitions =
                (definition["transitions"] as List<Map<String, Any?>>).map {
                    Transition(it["name"] as String, it["from"] as String, it["to"] as String, it["roles"] as List<String>)
                }
            return Workflow(
                objectName,
                states.single { it["type"] == "INITIAL" }["name"] as String,
                states.filter { it["type"] == "FINAL" }.map { it["name"] as String }.toSet(),
                states.map { it["name"] as String },
                transitions
            )
        }
    }
}
