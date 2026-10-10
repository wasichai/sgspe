package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.common.Records
import wasichai.notifications.Audience
import java.time.Instant
import java.util.UUID

// los avisos de la revisión vencida de un riesgo como fuente de wasichai-notifications (ASM-099): risk_escalated por nivel,
// al responsable, a los roles de la transición de tratamiento que sigue y a los de approve_senior de WF-D
class RiskReviewNoticesApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    @Autowired
    private lateinit var notices: DeadlineNotices

    @Autowired
    private lateinit var records: Records

    private fun ids(vararg accounts: Account): Set<UUID> = accounts.map { UUID.fromString(it.id) }.toSet()

    @Test
    fun `una revision vencida desde enero avisa en los tres niveles`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val senior = member("PROJECT_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        val assessed =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 2)),
                owner.token,
                withReason("primera revisión")
            )
        assertEquals(HttpStatus.CREATED, assessed.status, assessed.body)
        runBlocking { job.runOnce() }
        val organization = organizationId()
        val drafts = runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
        val mine =
            drafts
                .filter { it.key?.startsWith("risk_escalated:$risk:") == true }
                .associate { it.key!!.split(":")[2] to it.audience.map { a -> (a as Audience.User).id }.toSet() }
        assertEquals(setOf("0", "1", "2"), mine.keys)
        assertEquals(ids(owner), mine.getValue("0"))
        assertTrue(mine.getValue("1").containsAll(ids(owner, social)))
        assertEquals(ids(senior), mine.getValue("2"))
    }
}
