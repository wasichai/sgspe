package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.common.ForbiddenException

// quién puede pedir AI-07 (api.md §4.14, ai-architecture.md §9.1): una persona que crea interacciones y sugerencias y lee los
// nombres de person; ningún nombre de rol
class DuplicateUseCaseTest {
    @Test
    fun `una persona que crea interacciones y sugerencias y lee los nombres`() {
        assertNull(DuplicateUseCase.decide(serviceAccount = false, canCreate = true, readsNames = true))
        assertEquals(
            "PERSON_REQUIRED",
            DuplicateUseCase
                .decide(serviceAccount = true, canCreate = true, readsNames = true)!!
                .violations
                .single()
                .message
        )
        assertTrue(DuplicateUseCase.decide(serviceAccount = false, canCreate = false, readsNames = true) is ForbiddenException)
        val names = DuplicateUseCase.decide(serviceAccount = false, canCreate = true, readsNames = false)
        assertTrue(names is ForbiddenException && "nombres de person" in names.message.orEmpty())
    }
}
