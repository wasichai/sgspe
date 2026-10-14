package sgspe.dashboard

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// el código de un indicador es el ID de su KPI en dashboards.md (§10): los tableros, el cierre y load_indicators.py lo buscan
// por ese código, así que no cambia (400 FIELD_NOT_EDITABLE). la meta, la frecuencia y los textos los edita quien tiene UPDATE
@Component
@Order(GuardOrder.DOMAIN)
class IndicatorWriteGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != INDICATOR || change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val before = Values.text(change.before?.get(CODE)) ?: return emptyList()
        if (Values.text(view.after(change)[CODE]) == before) return emptyList()
        return listOf(
            Finding("FIELD_NOT_EDITABLE", "El código de un indicador no cambia: es el ID de su KPI en dashboards.md", CODE, status = HttpStatus.BAD_REQUEST)
        )
    }

    companion object {
        const val INDICATOR = "indicator"
        const val CODE = "code"
    }
}
