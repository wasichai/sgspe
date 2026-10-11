package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// InvestmentWriteGuard, hijos del programa (especificación de F5 §3.6, «Otras reglas de los hijos»): en el alta y el cambio
// del programa y de sus hijos, las fechas en orden, el año presupuestal y los conteos (ProgramRules.data, 400 OUT_OF_RANGE);
// en un grupo de beneficiarios, que su iniciativa sea de su mismo programa (409 PROGRAM_MISMATCH), en el alta y cuando la
// escritura cambia la iniciativa o el programa del grupo. la iniciativa se lee como quien escribe: una que no lee es
// NotReadable (OBJECT_NOT_READABLE en InvestmentWriteGuard). la iniciativa y el grupo son del mismo proyecto por construcción:
// los dos cuelgan de un programa
@Component
class ProgramDataRule(
    private val records: Records,
    private val view: WriteView
) : InvestmentRule {
    override val order = InvestmentRuleOrder.PROGRAM + 10

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in Programs.OBJECTS || change.isLink()) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change, after)
        val findings = ProgramRules.data(change.objectName, after, changed).toMutableList()
        if (findings.isNotEmpty() && !dryRun) return findings
        if (change.objectName == Programs.GROUP && (Programs.INITIATIVE in changed || Programs.PROGRAM in changed)) {
            sameProgram(after)?.let { findings += it }
        }
        return findings
    }

    private suspend fun sameProgram(after: Map<String, Any?>): Finding? {
        val initiative = Records.uuid(after[Programs.INITIATIVE]) ?: return null
        val owner = Values.text(records.readable(Programs.INITIATIVE, initiative).attributes[Programs.PROGRAM])
        return if (owner == Values.text(after[Programs.PROGRAM])) null else ProgramRules.programMismatch()
    }
}
