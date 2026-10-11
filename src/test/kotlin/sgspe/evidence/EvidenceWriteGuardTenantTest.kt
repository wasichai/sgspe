package sgspe.evidence

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.r2dbc.core.DatabaseClient
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.TenantModel
import sgspe.common.Transactions
import sgspe.common.WriteView
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.WasichaiSchemas
import java.util.UUID

// bajar la clasificación de una evidencia (F3) en un tenant sin el modelo de F4 (I-2 de la revisión de la rama del plan 4.2a):
// la regla de la QCS confidencial solo consulta las relaciones evidence_<qcs> de los objetos que el tenant tiene; con ellos,
// las consulta todas
class EvidenceWriteGuardTenantTest {
    private val related = mutableListOf<String>()

    private val records: Records =
        mock(Records::class.java) { invocation ->
            if (invocation.method.name != "related") error("lectura inesperada: ${invocation.method.name}")
            related += invocation.arguments[2] as String
            emptyList<Any>()
        }

    private val before = mapOf(EvidenceRules.CLASSIFICATION to "CONFIDENCIAL")
    private val after = mapOf(EvidenceRules.CLASSIFICATION to "INTERNA")

    private fun guard(withF4: Boolean): EvidenceWriteGuard {
        val user = mock(AuthenticatedUser::class.java)
        return EvidenceWriteGuard(
            view = mock(WriteView::class.java) { invocation -> if (invocation.method.name == "after") after else null },
            callers = mock(Callers::class.java) { invocation -> if (invocation.method.name == "userOrNull") user else null },
            permissions = mock(Permissions::class.java) { invocation -> invocation.method.name == "has" },
            transactions = mock(Transactions::class.java),
            definitions = mock(ObjectDefinitions::class.java),
            schemas = mock(WasichaiSchemas::class.java),
            db = mock(DatabaseClient::class.java),
            records = records,
            tenant =
                TenantModel(
                    mock(ObjectDefinitions::class.java) { invocation ->
                        if (invocation.method.name != "current") return@mock null
                        if (!withF4 && invocation.arguments[1] in Attention.OBJECTS) throw NotFoundException("sin ${invocation.arguments[1]}")
                        mock(ObjectDefinition::class.java)
                    }
                )
        )
    }

    private val downgrade =
        RecordWrite(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            EvidenceRules.EVIDENCE,
            UUID.randomUUID(),
            RecordChangeKind.UPDATED,
            before,
            after,
            null,
            null
        )

    @Test
    fun `sin el modelo de F4 la baja de clasificacion no consulta vinculos con QCS`() {
        assertEquals(emptyList<Any>(), runBlocking { guard(withF4 = false).findings(downgrade, false) })
        assertEquals(emptyList<String>(), related)
    }

    @Test
    fun `con el modelo de F4 consulta los vinculos con las tres QCS`() {
        assertEquals(emptyList<Any>(), runBlocking { guard(withF4 = true).findings(downgrade, false) })
        assertEquals(Attention.OBJECTS.map { EvidenceRules.linkRelationship(it) }, related)
    }
}
