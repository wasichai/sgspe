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
import sgspe.F2ApiTest
import sgspe.engagement.EngagementWriteGuard
import java.io.File

// la matriz de los cinco workflows de F2 (workflows.md), armada desde model/workflows/*.json: por cada transición, (1) cada
// rol de su lista la aplica desde su estado de origen y el registro queda en el destino, (2) un rol fuera de la lista que
// tiene UPDATE (o `u`) sobre el objeto recibe 403 y el listado le dice `requires role ...`, y (3) desde un estado que no es
// el de origen responde 409 con el registro sin cambios. las expectativas salen de esos mismos JSON, así que un cambio de
// roles o de estados en ellos cambia los casos y no los hace fallar: lo que la matriz prueba es que wasichai aplica los
// workflows tal como están escritos. que los JSON coincidan con docs/workflows.md lo prueba model/test_model.py, y los
// conteos fijos de WORKFLOW_TRANSITIONS impiden que una carga vacía o truncada deje la matriz sin casos. cada paso lleva
// motivo, para que TransitionReasonGuard (plan 2.2) no rompa esta clase, y cada registro trae lo que piden las guardas de
// 2.2 (como `activatable()` y `profileable()` de WF-A y WF-B; `close_light` usa un tipo no formal, ver LIGHT_CLOSE_TYPE)
class WorkflowMatrixApiTest : F2ApiTest() {
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
            else -> error("sin registro de prueba para ${workflow.objectName}")
        }

    // un registro nuevo llevado hasta `state` por el camino de pathTo, cada paso por el primer rol de su lista que maneja el objeto
    private fun recordAt(
        workflow: Workflow,
        state: String,
        creatorRole: String,
        engagementType: String = FORMAL_TYPE
    ): String {
        val id = create(workflow, creatorRole, engagementType)
        for (step in checkNotNull(pathTo(workflow, state)) { "${workflow.objectName}: el estado $state no se alcanza" }) {
            val role = workflow.drivers.first { it in step.roles }
            move(workflow.objectName, id, step.name, actor(workflow, role, id).token, TEXT)
        }
        return id
    }

    // quien crea el registro de la prueba: el propio rol si puede crearlo (RU: FIELD_AGENT solo cambia lo que creó), si no el primero de los que llevan el objeto
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
    private fun wrongState(
        workflow: Workflow,
        rule: Transition
    ): String =
        if (rule.from != workflow.initial) {
            workflow.initial
        } else {
            workflow.states.first { it != rule.from && it !in workflow.finals && pathTo(workflow, it) != null }
        }

    // el camino más corto desde el estado inicial hasta `state` sin las transiciones diferidas a 2.2 ni los atajos que 2.2 guarda
    private fun pathTo(
        workflow: Workflow,
        state: String
    ): List<Transition>? {
        val usable = workflow.transitions.filter { workflow.key(it) !in DEFERRED_TO_2_2 && workflow.key(it) !in WALK_AVOIDS }
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
            val transitions = workflow.transitions.filter { workflow.key(it) !in DEFERRED_TO_2_2 }
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
                    val id = recordAt(workflow, rule.from, creatorFor(workflow, role), type)
                    move(objectName, id, rule.name, actor(workflow, role, id).token, TEXT)
                    assertEquals(rule.to, state(objectName, id))
                }
            }
        val outsider = outsider(workflow, rule)
        val forbidden =
            dynamicTest("un rol con UPDATE fuera de la lista recibe 403 y el listado dice requires role") {
                // sin un rol así no hay caso: todos los que actualizan el objeto están en la lista de la transición
                assumeTrue(outsider != null) { "${workflow.key(rule)}: todo rol con UPDATE sobre $objectName está en su lista" }
                val id = recordAt(workflow, rule.from, workflow.drivers.first(), type)
                val who = actor(workflow, outsider!!, id)
                val response = transition(objectName, id, rule.name, who.token, TEXT)
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
                val response = transition(objectName, id, rule.name, who.token, TEXT)
                assertEquals(HttpStatus.CONFLICT, response.status, "$role: ${response.body}")
                assertEquals(wrong, state(objectName, id))
                assertFalse(rule.name in available(objectName, id, who.token).keys, "el listado no ofrece ${rule.name} desde $wrong")
            }
        return allowed + forbidden + conflict
    }

    @Test
    fun `cada transicion diferida a 2_2 existe en su workflow`() {
        for (key in DEFERRED_TO_2_2 + WALK_AVOIDS) {
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
    fun `los cinco workflows de model estan en la matriz`() {
        val files = File("model/workflows").listFiles { file -> file.extension == "json" }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(files, WORKFLOWS.keys)
    }

    @Test
    fun `todo estado de origen se alcanza sin las transiciones diferidas`() {
        for (workflow in WORKFLOWS.values) {
            for (rule in workflow.transitions.filter { workflow.key(it) !in DEFERRED_TO_2_2 }) {
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
        private const val FORMAL_TYPE = "REUNION"
        private const val LIGHT_CLOSE_TYPE = "CONTACTO CARA A CARA"

        // las transiciones de cada workflow en workflows.md (WF-A 9, WF-B 6, WF-C 14, WF-L 4, WF-O 2)
        private val WORKFLOW_TRANSITIONS =
            mapOf("project" to 9, "stakeholder" to 6, "engagement" to 14, "field_visit" to 4, "area_of_influence" to 2)

        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest
        val DEFERRED_TO_2_2 =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede"
            )

        // atajos de WF-C que 2.2 guarda con reglas propias: el registro llega a `held` por el ciclo normal (schedule y register_held)
        val WALK_AVOIDS = setOf("engagement.register_held_unplanned", "engagement.close_light")

        // quién lleva cada registro por sus estados: el primero lo crea; cada paso lo da el primero que está en la lista del paso
        val DRIVERS =
            mapOf(
                "project" to listOf("PROJECT_ADMIN"),
                "stakeholder" to listOf("STAKEHOLDER_MANAGER"),
                "engagement" to listOf("COMMUNITY_RELATIONS_OFFICER", "SOCIAL_MANAGER"),
                "field_visit" to listOf("COMMUNITY_RELATIONS_OFFICER"),
                "area_of_influence" to listOf("SOCIAL_MANAGER")
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
