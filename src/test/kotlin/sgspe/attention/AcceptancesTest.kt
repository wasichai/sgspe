package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException

// el 400 de un respondedAt mal formado nombra la propiedad del cuerpo (respondedAt), no el campo del modelo (responded_at),
// sin tocar las demás violaciones ni el mensaje
class AcceptancesTest {
    @Test
    fun `responded_at del modelo se informa como respondedAt del cuerpo`() {
        val original =
            ValidationException(
                "Datos inválidos",
                listOf(FieldViolation("responded_at", "debe ser una fecha y hora"), FieldViolation("response", "es demasiado largo"))
            )
        val renamed = Acceptances.bodyFields(original)
        assertEquals(listOf("respondedAt", "response"), renamed.violations.map { it.field })
        assertEquals(original.violations.map { it.message }, renamed.violations.map { it.message })
        assertEquals(original.message, renamed.message)
        assertEquals(400, renamed.status.value())
    }
}
