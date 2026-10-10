package sgspe.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// la escritura con que DerivedFieldsKeeper repone los derivados que un PUT dejó vacíos no es un cambio para las
// automatizaciones (ronda branchfix de 4.2b, R-I2): el eco se reconoce sin base, y solo él
class DerivedRestoreTest {
    private val risk = UUID.randomUUID()
    private val restore = DerivedRestore("social_risk", risk, mapOf("risk_level" to "CRITICO", "score" to 20))

    private fun change(
        before: Map<String, Any?>,
        after: Map<String, Any?>,
        objectName: String = "social_risk",
        id: UUID = risk,
        kind: RecordChangeKind = RecordChangeKind.UPDATED
    ) = RecordChange(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, id, kind, before, after, "assessed")

    private val cleared = mapOf<String, Any?>("title" to "Empleo local", "risk_level" to null, "score" to null)

    @Test
    fun `reponer el valor que el PUT dejo vacio es un eco`() {
        assertTrue(restore.echoes(change(cleared, cleared + mapOf("risk_level" to "CRITICO", "score" to 20L))))
    }

    @Test
    fun `un valor distinto del repuesto no es un eco`() {
        assertFalse(restore.echoes(change(cleared, cleared + mapOf("risk_level" to "ALTO", "score" to 20))))
    }

    @Test
    fun `un derivado que ya tenia valor no es un eco`() {
        // una escritura anidada sobre el mismo registro, después de la reposición: su `before` ya trae los valores repuestos
        val restored = cleared + mapOf("risk_level" to "CRITICO", "score" to 20)
        assertFalse(restore.echoes(change(restored, restored + ("title" to "Otro título"))))
    }

    @Test
    fun `otro registro, otro objeto o un alta no son el eco`() {
        val after = cleared + mapOf("risk_level" to "CRITICO", "score" to 20)
        assertFalse(restore.echoes(change(cleared, after, id = UUID.randomUUID())))
        assertFalse(restore.echoes(change(cleared, after, objectName = "commitment")))
        assertFalse(restore.echoes(change(cleared, after, kind = RecordChangeKind.CREATED)))
    }
}
