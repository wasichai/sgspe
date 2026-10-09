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

// P54: un compromiso (y un candidato, CandidateLinkRule) solo referencia registros de su propio proyecto. las lecturas son del
// tenant hasta UPS-01, así que sin esta regla un miembro del proyecto A inflaría el tope y el ejecutado de una campaña del
// proyecto B. fields y mismatches son puras; judge lee los destinos como quien escribe
object ProjectLinks {
    // los campos RELATION de `commitment` cuyo objeto destino tiene alcance de proyecto, derivados del modelo; `project` es el
    // propio alcance y no un vínculo
    fun fields(catalog: ModelCatalog): List<Pair<String, String>> = fieldsOf(catalog, Commitments.COMMITMENT)

    // lo mismo para cualquier objeto con `project`
    fun fieldsOf(
        catalog: ModelCatalog,
        objectName: String
    ): List<Pair<String, String>> = catalog.manyToOneFields(objectName).filter { (field, target) -> field != "project" && ProjectScope.covers(target) }

    // los campos cuyo destino es de otro proyecto (o de ninguno): `targets` da el proyecto de cada destino vinculado
    fun mismatches(
        project: UUID?,
        targets: Map<String, UUID?>
    ): List<String> = if (project == null) emptyList() else targets.filterValues { it != project }.keys.toList()

    // lo mismo con el objeto destino de cada campo: uno de `tenantCapable` (project opcional, como evidence) sin proyecto es del
    // tenant y no de otro proyecto; con el de otro, sí discrepa
    fun mismatches(
        project: UUID?,
        targets: Map<String, Pair<String, UUID?>>,
        tenantCapable: Set<String>
    ): List<String> = mismatches(project, targets.filterValues { (target, owner) -> owner != null || target !in tenantCapable }.mapValues { it.value.second })

    // los campos de `fields` que una escritura de `objectName` deja vinculados a otro proyecto. se juzga un alta, un vínculo que
    // cambia y todos los vínculos si cambia el proyecto del registro; ni una transición, ni un vínculo M2M, ni un registro sin
    // proyecto. lee como quien escribe: un destino que no se lee es NotReadable
    suspend fun judge(
        objectName: String,
        fields: List<Pair<String, String>>,
        tenantCapable: Set<String>,
        change: RecordWrite,
        records: Records,
        view: WriteView
    ): List<String> {
        if (change.objectName != objectName) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val project = Records.uuid(after["project"]) ?: return emptyList()
        val changed = view.changed(change, after)
        val all = change.kind == RecordChangeKind.CREATED || "project" in changed
        val judged = fields.filter { (field, _) -> (all || field in changed) && Records.uuid(after[field]) != null }
        val targets = judged.associate { (field, target) -> field to (target to records.linkedProject(target, Records.uuid(after[field])!!)) }
        return mismatches(project, targets, tenantCapable)
    }
}

// PROJECT_MISMATCH (409) en el campo del compromiso vinculado a un registro de otro proyecto (ProjectLinks.judge)
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
        ProjectLinks.judge(Commitments.COMMITMENT, ProjectLinks.fields(catalog), emptySet(), change, records, view).map { field ->
            Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un compromiso solo referencia lo de su proyecto (P54)", field)
        }
}
