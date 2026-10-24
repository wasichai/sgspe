package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// B-03 del rediseño (wasichai/sgspe#161): GET /api/sgspe/projects/{id}/counts da en una lectura los conteos de las pestañas
// de un proyecto, los mismos totalElements que la API genérica le da a cada cuenta con size=1 (ADR-0029: el alcance de
// lectura; security.md §9.6: sin las QCS confidenciales que no lee). lo que la cuenta no lee es null, nunca 0, y un proyecto
// que no existe o no es de sus proyectos es el mismo 404
class ProjectCountsApiTest : F5ApiTest() {
    private val receiver by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val colleague by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val fieldAgent by lazy { member("FIELD_AGENT") }

    private val overdue = mapOf("deadline_status" to "VENCIDO")
    private val attention = listOf("grievance", "inquiry", "stakeholder_request")

    // las lecturas genéricas que el portal sumaba por clave (sgspe-ui, contexto/conteos.ts)
    private val sources: Map<String, List<Pair<String, Map<String, String>>>> =
        linkedMapOf(
            "stakeholders:total" to listOf("stakeholder" to emptyMap()),
            "engagements:total" to listOf("engagement" to emptyMap()),
            "commitments:total" to listOf("commitment" to emptyMap()),
            "commitments:accion" to listOf("commitment" to overdue),
            "attention:total" to attention.map { it to emptyMap() },
            "attention:accion" to attention.map { it to overdue },
            "risks:total" to listOf("social_risk" to emptyMap()),
            "programs:total" to listOf("social_program" to emptyMap()),
            "documents:total" to listOf("evidence" to emptyMap())
        )

    // un registro de cada pestaña en el proyecto de la prueba, un compromiso y una queja vencidos, una queja confidencial
    // que recibió `receiver` y un grupo de interés de otro proyecto, que no se cuenta
    private fun scenario(): String {
        val project = testProject()
        stakeholder(project)
        engagement(project)
        commitment(project)
        platformPatch("commitment", commitment(project), overdue)
        grievance(project, receiver)
        platformPatch("grievance", grievance(project, receiver), overdue)
        grievance(project, receiver, mapOf("confidential" to true))
        inquiry(project, receiver)
        socialRisk(project)
        socialProgram(project)
        evidence(project)
        stakeholder(project())
        return project
    }

    private fun counts(
        project: String,
        account: Account?
    ): JsonNode = tree(send("GET", "/api/sgspe/projects/$project/counts", null, HttpStatus.OK, account?.token ?: token))

    // la suma de los totalElements de las lecturas genéricas que la cuenta puede hacer; null si no puede ninguna o si una
    // de las que puede falla
    private fun generic(
        project: String,
        account: Account?,
        parts: List<Pair<String, Map<String, String>>>
    ): Long? {
        val totals =
            parts.mapNotNull { (objectName, filters) ->
                val query = (mapOf("project" to project) + filters + ("size" to "1")).entries.joinToString("&") { "${it.key}=${it.value}" }
                val response = exchange("GET", "/api/objects/$objectName/records?$query", null, account?.token ?: token)
                when (response.status) {
                    HttpStatus.OK -> tree(response.body)["totalElements"].asLong()
                    HttpStatus.FORBIDDEN -> null
                    else -> return null
                }
            }
        return if (totals.isEmpty()) null else totals.sum()
    }

    private fun value(node: JsonNode): Long? = if (node.isNull) null else node.asLong()

    @Test
    fun `los conteos coinciden con los totales de las lecturas genericas de cada cuenta`() {
        val project = scenario()
        for ((name, account) in listOf("admin" to null, "quien recibió" to receiver, "colega" to colleague, "agente de campo" to fieldAgent)) {
            val body = counts(project, account)["counts"]
            assertEquals(sources.keys.toList(), body.propertyNames().toList(), name)
            for ((key, parts) in sources) {
                assertEquals(generic(project, account, parts), value(body[key]), "$name, $key")
            }
        }
        val admin = counts(project, null)["counts"]
        assertEquals(1L, value(admin["engagements:total"]))
        assertEquals(2L, value(admin["commitments:total"]))
        assertEquals(1L, value(admin["commitments:accion"]))
        assertEquals(4L, value(admin["attention:total"]))
        assertEquals(1L, value(admin["attention:accion"]))
    }

    @Test
    fun `la QCS confidencial no se cuenta a quien no la lee, y se dice con confidentialHidden`() {
        val project = scenario()
        val mine = counts(project, receiver)
        val theirs = counts(project, colleague)
        assertEquals(value(theirs["counts"]["attention:total"])!! + 1, value(mine["counts"]["attention:total"]))
        assertTrue(theirs["confidentialHidden"].asBoolean())
        assertFalse(counts(project, null)["confidentialHidden"].asBoolean())
    }

    @Test
    fun `lo que la cuenta no lee es null, nunca cero`() {
        val project = scenario()
        val body = counts(project, fieldAgent)["counts"]
        for (key in listOf("commitments:total", "commitments:accion", "risks:total", "programs:total")) {
            assertTrue(body.has(key), key)
            assertTrue(body[key].isNull, "$key: ${body[key]}")
        }
        assertEquals(1L, value(body["engagements:total"]))
    }

    @Test
    fun `un proyecto ajeno y uno que no existe dan el mismo 404`() {
        val project = scenario()
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val foreign = exchange("GET", "/api/sgspe/projects/$project/counts", null, outsider.token)
        val missing = exchange("GET", "/api/sgspe/projects/${UUID.randomUUID()}/counts", null, outsider.token)
        assertEquals(HttpStatus.NOT_FOUND, foreign.status, foreign.body)
        assertEquals(HttpStatus.NOT_FOUND, missing.status, missing.body)
        assertEquals("PROJECT_NOT_FOUND", code(foreign))
        assertEquals("PROJECT_NOT_FOUND", code(missing))
        // otro proyecto de la cuenta asignada tampoco se le cuenta
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/projects/${project()}/counts", null, receiver.token).status)
    }

    @Test
    fun `un parametro de consulta desconocido es 422`() {
        val project = testProject()
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/projects/$project/counts?size=1", null).status)
    }
}
