package sgspe.risk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de WF-F (workflows.md, especificación de F4 §3.6): las de datos puras y las que cuentan medidas y revisiones
// residuales, con un Records de prueba
class RiskWriteGuardTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(
        name: String,
        record: Map<String, Any?>
    ) = RiskRules.transition(name, record).map { it.code to it.field }

    // un Records cuyo count responde `measures` para las medidas y `residuals` para las revisiones; sin lectura, Forbidden
    private fun guard(
        measures: Long?,
        residuals: Long?
    ): RiskWriteGuard {
        val records =
            mock(Records::class.java) { call ->
                if (call.method.name != "count") return@mock null
                val answer = if (call.arguments[0] == Risks.MEASURE) measures else residuals
                answer ?: throw ForbiddenException("sin lectura")
            }
        return RiskWriteGuard(records, mock(WriteView::class.java))
    }

    private fun transition(
        name: String,
        before: Map<String, Any?>
    ) = RecordWrite(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Risks.SOCIAL_RISK,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        before,
        null,
        name
    )

    private fun findings(
        guard: RiskWriteGuard,
        name: String,
        before: Map<String, Any?> = emptyMap()
    ) = runBlocking { guard.findings(transition(name, before), dryRun = false) }.map { it.code to it.field }

    @Test
    fun `evaluar exige probabilidad, impacto y responsable`() {
        assertEquals(
            listOf("FIELD_REQUIRED" to "probability", "FIELD_REQUIRED" to "impact", "FIELD_REQUIRED" to "owner_user_id"),
            codes("assess", emptyMap())
        )
        assertEquals(emptyList<Pair<String, String?>>(), codes("assess", mapOf("probability" to 3, "impact" to 4, "owner_user_id" to UUID.randomUUID())))
    }

    @Test
    fun `cada camino exige su decision de tratamiento y cerrar exige el motivo`() {
        assertEquals(listOf("FIELD_REQUIRED" to "treatment_decision"), codes("decide_mitigation", mapOf("treatment_decision" to "MONITOREAR")))
        assertEquals(emptyList<Pair<String, String?>>(), codes("decide_mitigation", mapOf("treatment_decision" to "MITIGAR")))
        assertEquals(emptyList<Pair<String, String?>>(), codes("decide_monitoring", mapOf("treatment_decision" to "ACEPTAR")))
        assertEquals(listOf("FIELD_REQUIRED" to "treatment_decision"), codes("decide_monitoring", emptyMap()))
        for (close in RiskRules.CLOSE) assertEquals(listOf("FIELD_REQUIRED" to "close_reason"), codes(close, emptyMap()), close)
    }

    @Test
    fun `mitigar exige una medida con responsable y monitorear una revision residual`() {
        val mitigate = mapOf("treatment_decision" to "MITIGAR")
        assertEquals(listOf("MEASURE_OWNER_REQUIRED" to "mitigation_measure"), findings(guard(0, 1), "decide_mitigation", mitigate))
        assertEquals(emptyList<Pair<String, String?>>(), findings(guard(1, 0), "decide_mitigation", mitigate))
        assertEquals(listOf("MEASURE_OWNER_REQUIRED" to "mitigation_measure"), findings(guard(0, 1), "resume_mitigation"))
        assertEquals(listOf("RESIDUAL_ASSESSMENT_REQUIRED" to "risk_assessment"), findings(guard(1, 0), "start_monitoring"))
        assertEquals(emptyList<Pair<String, String?>>(), findings(guard(1, 1), "decide_monitoring", mapOf("treatment_decision" to "MONITOREAR")))
    }

    @Test
    fun `sin lectura de las medidas es OBJECT_NOT_READABLE, nunca un pase`() {
        assertEquals(listOf("OBJECT_NOT_READABLE" to "mitigation_measure"), findings(guard(null, 1), "resume_mitigation"))
    }

    // decisión 16: la cadencia propia va de 1 a 365 días, como la del proyecto; vacía vale risk_review_days del proyecto
    @Test
    fun `una cadencia propia fuera de 1 a 365 dias es OUT_OF_RANGE y vacia vale la del proyecto`() {
        fun every(value: Any?) = RiskRules.reviewEvery(mapOf(Risks.REVIEW_EVERY to value), setOf(Risks.REVIEW_EVERY)).map { it.code to it.field }
        for (valid in listOf(null, "", 1, 30, "365.00")) assertEquals(emptyList<Pair<String, String?>>(), every(valid), "$valid")
        for (wrong in listOf(0, -7, 366, "2.5", "semanal")) assertEquals(listOf("OUT_OF_RANGE" to Risks.REVIEW_EVERY), every(wrong), "$wrong")
        // una cadencia que la escritura no cambia no se juzga: un valor guardado antes de la regla no bloquea otra escritura
        assertEquals(emptyList<Pair<String, String?>>(), RiskRules.reviewEvery(mapOf(Risks.REVIEW_EVERY to 0), setOf("title")))
    }

    // las transiciones de RiskRules existen en WF-F: un renombre dejaría de vigilarlas en silencio
    @Test
    fun `las transiciones de RiskRules existen en el workflow`() {
        val names = catalog.transitions(Risks.SOCIAL_RISK).map { it.name }.toSet()
        val used = RiskRules.CLOSE + RiskRules.NEEDS_MEASURE + RiskRules.NEEDS_RESIDUAL + Risks.ASSESS + "decide_mitigation"
        assertEquals(emptySet<String>(), used - names)
        assertEquals(listOf(RiskRules.MITIGATE) + RiskRules.MONITOR, catalog.enumOf(Risks.SOCIAL_RISK, RiskRules.TREATMENT))
    }
}
