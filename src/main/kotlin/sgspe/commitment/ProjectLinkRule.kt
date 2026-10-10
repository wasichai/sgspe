package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import sgspe.scope.ProjectLinks
import wasichai.core.data.RecordWrite

// PROJECT_MISMATCH (409) en el campo del compromiso vinculado a un registro de otro proyecto (ProjectLinks.judge, sgspe.scope).
// desde F4 los campos del catálogo suman origin_grievance y social_risk
@Component
class ProjectLinkRule(
    private val records: Records,
    private val view: WriteView,
    private val catalog: ModelCatalog
) : CommitmentRule {
    override val order = CommitmentRuleOrder.RECORD + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        ProjectLinks.judge(Commitments.COMMITMENT, ProjectLinks.fieldsOf(catalog, Commitments.COMMITMENT), emptySet(), change, records, view).map { field ->
            Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un compromiso solo referencia lo de su proyecto (P54)", field)
        }
}
