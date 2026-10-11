package sgspe.attention

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Attention
import sgspe.common.ObjectDefinitions
import sgspe.common.Records
import sgspe.common.TenantModel
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// un tenant sin el modelo de F4 sigue con WF-D de F3 (I-2 de la revisión de la rama del plan 4.2a): cerrar un compromiso no
// busca solicitudes que el tenant no tiene, así que el cierre no se revierte. con las QCS, el listener sí las busca
class RequestClosureListenerTest {
    private val reads = mutableListOf<String>()

    // lee solicitudes como un doble: ninguna aceptada con ese compromiso
    private val records: Records =
        mock(Records::class.java) { invocation ->
            if (invocation.method.name != "all") error("lectura inesperada: ${invocation.method.name}")
            reads += invocation.arguments[0] as String
            emptyList<Any>()
        }

    private fun model(withF4: Boolean): TenantModel =
        TenantModel(
            mock(ObjectDefinitions::class.java) { invocation ->
                if (invocation.method.name != "current") return@mock null
                if (!withF4 && invocation.arguments[1] in Attention.OBJECTS) throw NotFoundException("sin ${invocation.arguments[1]}")
                mock(ObjectDefinition::class.java)
            }
        )

    private val close =
        RecordChange(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Acceptances.COMMITMENT,
            UUID.randomUUID(),
            RecordChangeKind.TRANSITIONED,
            mapOf("code" to "COM-1"),
            mapOf("code" to "COM-1"),
            "closed",
            RequestClosureListener.CLOSE
        )

    @Test
    fun `sin el modelo de F4 el cierre de un compromiso no busca solicitudes`() {
        runBlocking { RequestClosureListener(records, model(withF4 = false)).recordChanged(close) }
        assertEquals(emptyList<String>(), reads)
    }

    @Test
    fun `con el modelo de F4 busca las solicitudes aceptadas del compromiso`() {
        runBlocking { RequestClosureListener(records, model(withF4 = true)).recordChanged(close) }
        assertEquals(listOf(Attention.REQUEST), reads)
    }

    // F5 (§3.7): el cierre de un aporte busca las solicitudes aceptadas que lo crearon, por created_contribution
    @Test
    fun `cerrar un aporte busca las solicitudes aceptadas por created_contribution`() {
        val filters = mutableListOf<Map<*, *>>()
        val capturing: Records =
            mock(Records::class.java) { invocation ->
                if (invocation.method.name != "all") error("lectura inesperada: ${invocation.method.name}")
                filters += invocation.arguments[1] as Map<*, *>
                emptyList<Any>()
            }
        val contribution = close.copy(objectName = Acceptances.CONTRIBUTION)
        runBlocking { RequestClosureListener(capturing, model(withF4 = true)).recordChanged(contribution) }
        assertEquals(listOf(mapOf(AttentionRules.CREATED_CONTRIBUTION to contribution.recordId.toString())), filters)
    }

    @Test
    fun `otra transicion u otro objeto no buscan solicitudes`() {
        runBlocking { RequestClosureListener(records, model(withF4 = true)).recordChanged(close.copy(transition = "register_delivery")) }
        runBlocking { RequestClosureListener(records, model(withF4 = true)).recordChanged(close.copy(objectName = "agreement")) }
        assertEquals(emptyList<String>(), reads)
    }
}
