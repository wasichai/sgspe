package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.scope.ProjectScope
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// P54, pura: un compromiso solo referencia registros de su propio proyecto. las lecturas son del tenant hasta UPS-01, así que
// sin esta regla un miembro del proyecto A inflaría el tope y el ejecutado de una campaña del proyecto B
object ProjectLinks {
    // los campos RELATION de `commitment` cuyo objeto destino tiene alcance de proyecto, derivados del modelo; `project` es el
    // propio alcance y no un vínculo
    fun fields(catalog: ModelCatalog): List<Pair<String, String>> =
        catalog.manyToOneFields(Commitments.COMMITMENT).filter { (field, target) -> field != "project" && ProjectScope.covers(target) }

    // los campos cuyo destino es de otro proyecto (o de ninguno): `targets` da el proyecto de cada destino vinculado
    fun mismatches(
        project: UUID?,
        targets: Map<String, UUID?>
    ): List<String> = if (project == null) emptyList() else targets.filterValues { it != project }.keys.toList()
}

// PROJECT_MISMATCH (409) en el campo vinculado a un registro de otro proyecto. se juzga un alta, un vínculo que cambia y todos
// los vínculos si cambia el proyecto del compromiso. lee como quien escribe: un destino que no se lee es NotReadable
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
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val project = Records.uuid(after["project"]) ?: return emptyList()
        val changed = view.changed(change, after)
        val all = change.kind == RecordChangeKind.CREATED || "project" in changed
        val judged = ProjectLinks.fields(catalog).filter { (field, _) -> (all || field in changed) && Records.uuid(after[field]) != null }
        val projects = judged.associate { (field, target) -> field to projectOf(target, Records.uuid(after[field])!!) }
        return ProjectLinks.mismatches(project, projects).map { field ->
            Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un compromiso solo referencia lo de su proyecto (P54)", field)
        }
    }

    // el proyecto del registro destino: el suyo si es una raíz, el de su padre si es un hijo (agreement_clause)
    private suspend fun projectOf(
        target: String,
        id: UUID
    ): UUID? {
        val record = readAs(target) { records.find(target, id) } ?: throw NotReadable(target)
        val child = ProjectScope.CHILDREN[target] ?: return Records.uuid(record.attributes["project"])
        val parent = Records.uuid(record.attributes[child.first]) ?: return null
        val owner = readAs(child.second) { records.find(child.second, parent) } ?: throw NotReadable(child.second)
        return Records.uuid(owner.attributes["project"])
    }
}
