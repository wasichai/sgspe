package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.scope.ProjectScope
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// CommitmentWriteGuard, candidatos (WF-K): marcar un candidato como duplicado exige duplicate_of, que solo escribe
// POST /api/sgspe/commitment-candidates/{id}/duplicate (commitment_candidate es apiOnly). por la API genérica la transición
// llega sin él y se rechaza. no lee nada: va primero en su familia (P49)
@Component
class CandidateRules : CommitmentRule {
    override val order = CommitmentRuleOrder.CANDIDATE + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.CANDIDATE || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.transition != MARK_DUPLICATE || Values.text(change.before?.get(DUPLICATE_OF)) != null) return emptyList()
        return listOf(
            Finding(
                "FIELD_REQUIRED",
                "Marcar un candidato como duplicado exige el compromiso que ya lo cubre: use POST /api/sgspe/commitment-candidates/{id}/duplicate",
                DUPLICATE_OF
            )
        )
    }

    companion object {
        const val MARK_DUPLICATE = "mark_duplicate"
        const val DUPLICATE_OF = "duplicate_of"
    }
}

// P54 sobre el candidato, pura: sus vínculos son de su proyecto, porque aceptarlo crea un compromiso que hereda la interacción
// de origen (ProjectLinkRule la juzga en el alta del compromiso) y duplicate_of y created_commitment son compromisos del mismo
// proyecto. una evidencia puede ser del tenant (project opcional, ProjectScope): sin proyecto no es de otro
object CandidateLinks {
    val TENANT_CAPABLE = setOf("evidence")

    // los campos RELATION del candidato cuyo destino tiene alcance de proyecto, del modelo; `project` es el propio alcance
    fun fields(catalog: ModelCatalog): List<Pair<String, String>> =
        catalog.manyToOneFields(Commitments.CANDIDATE).filter { (field, target) -> field != "project" && ProjectScope.covers(target) }

    // los campos cuyo destino es de otro proyecto: `targets` da, por campo, el objeto destino y su proyecto
    fun mismatches(
        project: UUID?,
        targets: Map<String, Pair<String, UUID?>>
    ): List<String> =
        ProjectLinks.mismatches(
            project,
            targets.filterValues { (target, owner) -> owner != null || target !in TENANT_CAPABLE }.mapValues { it.value.second }
        )
}

// PROJECT_MISMATCH (409) en el vínculo del candidato que es de otro proyecto. se juzga el alta, un vínculo que cambia y todos si
// cambia el proyecto del candidato. lee como quien escribe: un destino que no se lee es NotReadable (OBJECT_NOT_READABLE)
@Component
class CandidateLinkRule(
    private val records: Records,
    private val view: WriteView,
    private val catalog: ModelCatalog
) : CommitmentRule {
    override val order = ORDER

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.CANDIDATE) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val project = Records.uuid(after["project"]) ?: return emptyList()
        val changed = view.changed(change, after)
        val all = change.kind == RecordChangeKind.CREATED || "project" in changed
        val judged = CandidateLinks.fields(catalog).filter { (field, _) -> (all || field in changed) && Records.uuid(after[field]) != null }
        val targets = judged.associate { (field, target) -> field to (target to records.linkedProject(target, Records.uuid(after[field])!!)) }
        return CandidateLinks.mismatches(project, targets).map { field ->
            Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un candidato solo referencia lo de su proyecto (P54)", field)
        }
    }

    companion object {
        // después de CandidateRules, que no lee
        const val ORDER = CommitmentRuleOrder.CANDIDATE + 2
    }
}
