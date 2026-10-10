package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

// la liberación del alcance de las QCS confidenciales que abre RequestClosureListener (decisión 4 del plan 4.2a): vale solo
// dentro de su bloque y solo para su objeto
class ConfidentialReleaseTest {
    private val commitment = UUID.fromString("00000000-0000-0000-0000-00000000000c")

    @Test
    fun `vale dentro del bloque y para su objeto, y fuera no`() =
        runBlocking {
            assertNull(ConfidentialRelease.on("stakeholder_request"))
            val inside =
                ConfidentialRelease.within("stakeholder_request", "created_commitment", commitment) {
                    assertNull(ConfidentialRelease.on("grievance"))
                    ConfidentialRelease.on("stakeholder_request")
                }
            assertEquals("created_commitment", inside?.field)
            assertEquals(commitment, inside?.value)
            assertNull(ConfidentialRelease.on("stakeholder_request"))
        }

    // un bloque que lanza (la transición close_accepted rechazada) no deja la liberación puesta para lo que sigue
    @Test
    fun `se retira tambien si el bloque lanza`() =
        runBlocking {
            assertThrows<IllegalStateException> {
                runBlocking {
                    ConfidentialRelease.within("stakeholder_request", "created_commitment", commitment) {
                        error("la transición falló")
                    }
                }
            }
            assertNull(ConfidentialRelease.on("stakeholder_request"))
        }
}
