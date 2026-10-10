package sgspe.warning

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// JOB-04 (workflows.md «Tipos de regla de JOB-04» y «Pruebas de trabajos», ASM-097): cada tipo dispara con el valor igual al
// umbral y no con uno menos; la regla del proyecto reemplaza a la del tenant del mismo tipo y una del tenant crea una alerta por
// proyecto; una alerta abierta, o detectada dentro de la ventana, silencia la regla; la alerta nace en detected sin datos
// personales, y su alta dispara AUT-09 y warning_new aunque la cree la plataforma
class WarningRulesJobApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: WarningRulesJob

    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun recent(): String = Instant.now().minus(1, ChronoUnit.HOURS).toString()

    private fun today(): String = LocalDate.now(LIMA).toString()

    // un proyecto activo (JOB-04 solo mira active y suspended): sin las precondiciones de WF-A, como lo permite ASM-062
    private fun activeProject(): String {
        val id = project(mapOf("policy_activation_preconditions" to false))
        move("project", id, "submit_for_review", reason = "revisión")
        move("project", id, "approve", reason = "aprobación")
        return id
    }

    private fun run() {
        runBlocking { job.runOnce() }
    }

    private fun alerts(project: String): List<JsonNode> =
        tree(send("GET", "/api/objects/early_warning/records?project=$project", null, HttpStatus.OK))["content"].values().toList()

    private fun descriptions(project: String): Set<String> = alerts(project).map { it["attributes"]["trigger_description"].asString() }.toSet()

    private fun rule(
        project: String?,
        kind: String,
        threshold: Int,
        name: String = "Regla $kind"
    ): String = warningRule(project, manager, mapOf("rule_kind" to kind, "threshold" to threshold, "name" to name))

    @Test
    fun `cada tipo por eventos dispara al alcanzar el umbral y no con uno menos`() {
        val project = activeProject()
        for (kind in listOf(
            Warnings.SIGNALS,
            Warnings.QCS,
            Warnings.OVERDUE_COMMITMENTS,
            Warnings.NEGATIVE_MENTIONS,
            Warnings.INCIDENTS
        )) {
            rule(project, kind, 2)
        }
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        val original = grievance(project, attributes = mapOf("received_at" to recent()))
        // un planteamiento reclasificado cuenta una vez (AttentionCounting)
        inquiry(project, attributes = mapOf("received_at" to recent(), "reclassified_from_grievance" to original))
        platformPatch("commitment", approvedCommitment(project), mapOf("deadline_status" to "VENCIDO"))
        mediaMention(project, attributes = mapOf("published_date" to today()))
        mediaMention(project, attributes = mapOf("published_date" to today(), "tone" to "POSITIVO"))
        incident(project, attributes = mapOf("occurred_at" to recent()))
        // una señal y un incidente fuera de la ventana de 7 días no cuentan
        warningSignal(project, attributes = mapOf("received_at" to Instant.now().minus(8, ChronoUnit.DAYS).toString()))
        incident(project, attributes = mapOf("occurred_at" to Instant.now().minus(8, ChronoUnit.DAYS).toString()))
        run()
        assertEquals(emptyList<JsonNode>(), alerts(project))
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        grievance(project, attributes = mapOf("received_at" to recent()))
        platformPatch("commitment", approvedCommitment(project), mapOf("deadline_status" to "VENCIDO"))
        mediaMention(project, attributes = mapOf("published_date" to today()))
        incident(project, attributes = mapOf("occurred_at" to recent()))
        run()
        assertEquals(
            setOf(
                "2 señales en 7 días (umbral 2)",
                "2 QCS recibidas en 7 días (umbral 2)",
                "2 compromisos vencidos (umbral 2)",
                "2 menciones negativas en 7 días (umbral 2)",
                "2 incidentes en 7 días (umbral 2)"
            ),
            descriptions(project)
        )
        for (alert in alerts(project)) {
            assertEquals("detected", alert["state"].asString())
            assertFalse(alert["attributes"]["ai_detected"].asBoolean())
            assertTrue(alert["attributes"]["generator_key"].asString().startsWith("warning_rule:"))
        }
    }

    @Test
    fun `NIVEL DE RIESGO crea una alerta por riesgo con su territorio`() {
        val project = activeProject()
        rule(project, Warnings.RISK_LEVEL, 15)
        val high = socialRisk(project)
        val low = socialRisk(project)
        for ((risk, impact) in listOf(high to 4, low to 2)) {
            val response =
                exchange(
                    "POST",
                    "/api/sgspe/social-risks/$risk/assessments",
                    mapOf("attributes" to mapOf("assessment_date" to today(), "probability" to 4, "impact" to impact)),
                    token,
                    withReason("revisión")
                )
            assertEquals(HttpStatus.CREATED, response.status, response.body)
        }
        run()
        val alert = alerts(project).single()
        val code = record("social_risk", high)["attributes"]["code"].asString()
        assertEquals("Riesgo $code con puntaje 16 (umbral 15)", alert["attributes"]["trigger_description"].asString())
        assertEquals(high, alert["attributes"]["social_risk"].asString())
    }

    @Test
    fun `la regla del proyecto reemplaza a la del tenant y una del tenant alerta en cada proyecto`() {
        val tenant = rule(null, Warnings.INCIDENTS, 1, "Incidentes del tenant")
        try {
            val first = activeProject()
            val second = activeProject()
            val own = activeProject()
            rule(own, Warnings.INCIDENTS, 5)
            for (project in listOf(first, second, own)) incident(project, attributes = mapOf("occurred_at" to recent()))
            run()
            assertEquals(setOf("1 incidentes en 7 días (umbral 1)"), descriptions(first))
            assertEquals(setOf("1 incidentes en 7 días (umbral 1)"), descriptions(second))
            assertEquals(emptySet<String>(), descriptions(own))
            assertEquals("Incidentes del tenant: INCIDENTES", alerts(first).single()["attributes"]["title"].asString())
        } finally {
            edit("early_warning_rule", tenant, mapOf("enabled" to false), manager.token)
        }
    }

    @Test
    fun `una alerta abierta y luego la ventana silencian la regla`() {
        val project = activeProject()
        rule(project, Warnings.SIGNALS, 1)
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        run()
        run()
        val alert = alerts(project).single()["id"].asString()
        val triage = member("SOCIAL_RISK_MANAGER", project)
        move("early_warning", alert, "start_triage", triage.token)
        move("early_warning", alert, "dismiss", triage.token, "falsa alarma")
        run()
        assertEquals(1, alerts(project).size)
    }

    // workflows.md «Listeners»: el alta de JOB-04 como la plataforma llega a AUT-09 y a WarningNoticeListener
    @Test
    fun `la alerta de JOB-04 dispara AUT-09 y warning_new, con el motivo del trabajo`() {
        val project = activeProject()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        rule(project, Warnings.SIGNALS, 1, "Señales de la semana")
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        run()
        val alert = alerts(project).single()
        val id = alert["id"].asString()
        assertEquals("Señales de la semana: CONTEO DE SEÑALES", alert["attributes"]["title"].asString())
        assertEquals("SUCCEEDED", awaitRun("early_warning", "aut-09-warning-triage-task", id)["status"].asString())
        val inbox = tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, officer.token))["content"].values()
        assertTrue(inbox.any { it["link"]?.get("recordId")?.asString() == id })
        val history = tree(send("GET", "/api/objects/early_warning/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == WarningRulesJob.REASON })
    }
}
