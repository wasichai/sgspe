package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// la regla de EDIT_ANY (security.md §5.5, RQ-SEC-008, ASM-069), compartida por EditAnyGuard y la emisión manual de un documento
// (ruling P62): en un objeto que declara EDIT_ANY, quien no la tiene solo toca lo que creó; sin quien llama, se niega
class OwnRecordsTest {
    private val user = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "agente@sgspe.test", listOf("ROL_DE_PRUEBA"))

    private fun refusal(
        declares: Boolean = true,
        caller: AuthenticatedUser? = user,
        holds: Boolean = false,
        created: Boolean = false
    ): Finding? =
        runBlocking {
            var createdAsked = false
            val finding =
                OwnRecords.refusal(
                    "engagement",
                    declares,
                    caller,
                    { holds },
                    {
                        createdAsked = true
                        created
                    }
                )
            if (holds) assertFalse(createdAsked, "con EDIT_ANY no se lee created_by")
            finding
        }

    @Test
    fun `sin EDIT_ANY y sin haber creado el registro es NOT_OWN_RECORD sin campo, un 403`() {
        val finding = refusal()!!
        assertEquals("NOT_OWN_RECORD", finding.code)
        assertNull(finding.field)
        assertEquals(HttpStatus.FORBIDDEN, finding.status)
        assertEquals("NOT_OWN_RECORD", refusal(caller = null)?.code)
    }

    @Test
    fun `con EDIT_ANY, con el registro propio o en un objeto que no la declara pasa`() {
        assertNull(refusal(holds = true))
        assertNull(refusal(created = true))
        assertNull(refusal(declares = false, caller = null))
    }
}
