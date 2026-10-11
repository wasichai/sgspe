package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// G1 a G4 de WF-G sin base, con un RecordService de mentira (como SupportPolicyGuardTest): lo que cada transición exige, el
// orden en la escritura y en seco, y lo que no se puede leer (NotReadable, nunca un pase)
class ProgramApprovalRuleTest {
    private val user = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "aprobador@sgspe.test", listOf("ROL_DE_PRUEBA"), null)
    private val partner = UUID.randomUUID().toString()
    private val agreement = UUID.randomUUID().toString()
    private var project: Map<String, Any?> = emptyMap()
    private var agreementState: String? = "active"
    private var validity: String? = "VIGENTE"
    private var agreementDenied = false
    private var parties = 1L
    private var partiesDenied = false
    private var approves = true
    private var creator = false

    // get: el proyecto o el acuerdo; list (los conteos de Records.count): las partes del acuerdo con el socio, y el programa por
    // su id (sin criterios: existe) o por su id y created_by (con criterios: lo registró quien aprueba)
    private val service: RecordService =
        mock(RecordService::class.java) { call ->
            when (call.method.name) {
                "get" ->
                    if (call.arguments[0] == Programs.AGREEMENT) {
                        if (agreementDenied) throw ForbiddenException("sin lectura")
                        RecordResponse(agreement, null, null, mapOf(Synergy.VALIDITY to validity), agreementState)
                    } else {
                        RecordResponse(UUID.randomUUID().toString(), null, null, project)
                    }
                "list" -> {
                    val query = call.arguments[1] as RecordQuery
                    val total =
                        if (call.arguments[0] == Programs.AGREEMENT_PARTY) {
                            if (partiesDenied) throw ForbiddenException("sin lectura")
                            parties
                        } else if (query.criteria.isEmpty() || creator) {
                            1L
                        } else {
                            0L
                        }
                    PageResponse.of(emptyList<RecordResponse>(), 0, 1, total)
                }
                else -> null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val rule =
        ProgramApprovalRule(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            mock(Callers::class.java) { call -> if (call.method.name == "userOrNull") user else null },
            mock(Permissions::class.java) { call -> if (call.method.name == "has") approves else null }
        )

    private val base = mapOf<String, Any?>(Programs.PROJECT to UUID.randomUUID().toString())
    private val partnered = base + mapOf(Programs.PARTNER to partner, Programs.AGREEMENT to agreement)

    private fun change(
        transition: String,
        before: Map<String, Any?>,
        objectName: String = Programs.PROGRAM
    ) = RecordWrite(
        UUID.randomUUID(),
        user.userId,
        UUID.randomUUID(),
        objectName,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        before,
        null,
        transition
    )

    private fun findings(
        transition: String,
        before: Map<String, Any?> = base,
        dryRun: Boolean = true
    ) = runBlocking { rule.findings(change(transition, before), dryRun) }

    private fun codes(
        transition: String,
        before: Map<String, Any?> = base,
        dryRun: Boolean = true
    ) = findings(transition, before, dryRun).map { it.code to it.field }

    private val none = emptyList<Pair<String, String?>>()

    @Test
    fun `plan_direct con la aprobacion interna activa es INTERNAL_APPROVAL_REQUIRED y sin ella pasa`() {
        assertEquals(listOf("INTERNAL_APPROVAL_REQUIRED" to "project"), codes(Programs.PLAN_DIRECT))
        project = mapOf(Programs.INTERNAL_POLICY to false)
        assertEquals(none, codes(Programs.PLAN_DIRECT))
    }

    @Test
    fun `approve sin APPROVE y por quien lo registro son 403 y la escritura se detiene en el primero`() {
        approves = false
        creator = true
        assertEquals(listOf("ACTION_REQUIRED" to "record", "SEGREGATION_OF_DUTIES" to "record"), codes(Programs.APPROVE))
        assertTrue(findings(Programs.APPROVE).all { it.status.value() == 403 })
        assertEquals(listOf("ACTION_REQUIRED" to "record"), codes(Programs.APPROVE, dryRun = false))
        approves = true
        assertEquals(listOf("SEGREGATION_OF_DUTIES" to "record"), codes(Programs.APPROVE))
        creator = false
        assertEquals(none, codes(Programs.APPROVE))
    }

    @Test
    fun `con socio y la politica activa G3 exige un acuerdo en active, no VENCIDO y con el socio como parte`() {
        val required = listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement")
        assertEquals(required, codes(Programs.APPROVE, base + (Programs.PARTNER to partner)))
        agreementState = "draft"
        assertEquals(required, codes(Programs.APPROVE, partnered))
        agreementState = "active"
        validity = "VENCIDO"
        assertEquals(required, codes(Programs.APPROVE, partnered))
        validity = null
        parties = 0
        assertEquals(required, codes(Programs.APPROVE, partnered))
        parties = 1
        assertEquals(none, codes(Programs.APPROVE, partnered))
        // también al planificar sin aprobación interna
        project = mapOf(Programs.INTERNAL_POLICY to false)
        parties = 0
        assertEquals(required, codes(Programs.PLAN_DIRECT, partnered))
    }

    @Test
    fun `sin socio o con la politica apagada G3 no lee nada`() {
        agreementDenied = true
        partiesDenied = true
        assertEquals(none, codes(Programs.APPROVE, base + (Programs.AGREEMENT to agreement)))
        project = mapOf(Synergy.POLICY to false)
        assertEquals(none, codes(Programs.APPROVE, partnered))
    }

    // la entrada 3 del Review Focus: un acuerdo o unas partes que quien aprueba no lee no se cuentan como cero
    @Test
    fun `lo que G3 no puede leer es NotReadable, nunca un pase`() {
        agreementDenied = true
        assertEquals(Programs.AGREEMENT, assertThrows<NotReadable> { findings(Programs.APPROVE, partnered) }.objectName)
        agreementDenied = false
        partiesDenied = true
        assertEquals(Programs.AGREEMENT_PARTY, assertThrows<NotReadable> { findings(Programs.APPROVE, partnered) }.objectName)
    }

    @Test
    fun `otras transiciones y otros objetos no se miran`() {
        approves = false
        assertEquals(none, codes(Programs.SUBMIT))
        assertEquals(none, runBlocking { rule.findings(change(Programs.APPROVE, base, "commitment"), true) }.map { it.code to it.field })
    }

    // InvestmentWriteGuard (plan 5.2a) solo corre sus reglas sobre OBJECTS: sin los de programas, G1 a G4 no correrían nunca. la
    // prueba falla hasta que esta tarea los suma
    @Test
    fun `InvestmentWriteGuard corre sus reglas sobre los objetos de programas`() {
        assertTrue(InvestmentWriteGuard.OBJECTS.containsAll(Programs.OBJECTS), "${InvestmentWriteGuard.OBJECTS}")
        assertEquals(InvestmentRuleOrder.PROGRAM, rule.order)
    }
}
