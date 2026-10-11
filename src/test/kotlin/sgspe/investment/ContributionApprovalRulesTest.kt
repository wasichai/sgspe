package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
import java.util.UUID

// P9 y P6 de WF-P (especificación de F5 §3.3) con un Records de prueba, y las piezas puras que comparten con el plan 5.2b
class ContributionApprovalRulesTest {
    private var items: List<Map<String, Any?>> = emptyList()
    private var agreement: RecordResponse? = null
    private var project: Map<String, Any?> = emptyMap()
    private val projectId = UUID.randomUUID().toString()

    private val records: Records =
        mock(Records::class.java) { call ->
            when (call.method.name) {
                "all" -> items.map { RecordResponse(id = UUID.randomUUID().toString(), createdAt = null, updatedAt = null, attributes = it) }
                "find" ->
                    when (call.arguments[0]) {
                        "agreement" -> agreement
                        "project" -> RecordResponse(id = projectId, createdAt = null, updatedAt = null, attributes = project)
                        else -> null
                    }
                else -> null
            }
        }

    private fun write(
        name: String,
        before: Map<String, Any?>,
        objectName: String = Contributions.CONTRIBUTION,
        kind: RecordChangeKind = RecordChangeKind.TRANSITIONED
    ) = RecordWrite(
        organizationId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        objectId = UUID.randomUUID(),
        objectName = objectName,
        recordId = UUID.randomUUID(),
        kind = kind,
        before = before,
        transition = name
    )

    private fun codes(
        rule: InvestmentRule,
        name: String,
        before: Map<String, Any?>,
        dryRun: Boolean = true
    ) = runBlocking { rule.findings(write(name, before), dryRun) }.map { it.code to it.field }

    @Test
    fun `una regla de aprobacion mira solo las transiciones de WF-P que nombra`() {
        assertEquals(null, ContributionApproval.target(write("approve", emptyMap(), objectName = "commitment"), Contributions.APPROVALS))
        assertEquals(null, ContributionApproval.target(write("reject", emptyMap()), Contributions.APPROVALS))
        assertNull(ContributionApproval.target(write("approve", emptyMap(), kind = RecordChangeKind.UPDATED), Contributions.APPROVALS))
        assertTrue(ContributionApproval.target(write("approve_direct", emptyMap()), Contributions.APPROVALS) != null)
    }

    @Test
    fun `P9 pide items y contribution_type, primero los items y en seco los dos`() {
        val rule = ContributionItemsRule(records)
        assertEquals(listOf("ITEMS_REQUIRED" to "contribution_item", "FIELD_REQUIRED" to "contribution_type"), codes(rule, "approve", emptyMap()))
        assertEquals(listOf("ITEMS_REQUIRED" to "contribution_item"), codes(rule, "approve_direct", emptyMap(), dryRun = false))
        items = listOf(mapOf("line_amount" to 10, "item_kind" to "BIEN"))
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", mapOf("contribution_type" to "DONACION")))
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "reject", emptyMap()))
    }

    @Test
    fun `un acuerdo vigente esta en active y no VENCIDO`() {
        assertTrue(Synergy.live("active", null))
        assertTrue(Synergy.live("active", "POR VENCER"))
        assertFalse(Synergy.live("active", "VENCIDO"))
        assertFalse(Synergy.live("draft", "VIGENTE"))
        assertFalse(Synergy.live(null, null))
    }

    @Test
    fun `P6 exige un acuerdo vigente con sinergia y la politica activa, tambien con justificacion`() {
        val rule = ContributionSynergyRule(records)
        val synergy = mapOf("has_institutional_synergy" to true, "guideline_deviation_justification" to "Convenio en trámite", "project" to projectId)
        assertEquals(listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement"), codes(rule, "approve", synergy))
        val id = UUID.randomUUID().toString()
        agreement = RecordResponse(id = id, createdAt = null, updatedAt = null, attributes = mapOf("validity_status" to "VIGENTE"), state = "draft")
        assertEquals(listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement"), codes(rule, "approve", synergy + ("agreement" to id)))
        agreement = agreement!!.copy(attributes = mapOf("validity_status" to "VENCIDO"), state = "active")
        assertEquals(listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement"), codes(rule, "approve_direct", synergy + ("agreement" to id)))
        agreement = agreement!!.copy(attributes = mapOf("validity_status" to "POR VENCER"))
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", synergy + ("agreement" to id)))
        // sin sinergia, o con la política apagada, no se exige
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", mapOf("has_institutional_synergy" to false)))
        project = mapOf(Synergy.POLICY to false)
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", synergy))
    }
}
