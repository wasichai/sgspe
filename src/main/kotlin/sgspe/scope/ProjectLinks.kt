package sgspe.scope

import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// P54 (F3) y la regla «mismo proyecto» de F4 (api.md §3.3, `PROJECT_MISMATCH`): un registro solo referencia registros de su
// propio proyecto. las lecturas son del tenant hasta UPS-01, así que sin esta regla un miembro del proyecto A vincularía lo del
// proyecto B. fieldsOf y mismatches son puras; judge lee los destinos como quien escribe. la usan ProjectLinkRule y
// CandidateLinkRule (sgspe.commitment) y ProjectLinkGuard (F4)
object ProjectLinks {
    // los campos RELATION de `objectName` cuyo objeto destino tiene alcance de proyecto, derivados del modelo; `project` es el
    // propio alcance y no un vínculo
    fun fieldsOf(
        catalog: ModelCatalog,
        objectName: String
    ): List<Pair<String, String>> = catalog.manyToOneFields(objectName).filter { (field, target) -> field != "project" && ProjectScope.covers(target) }

    // las raíces cuyo `project` es opcional (evidence, export_log, early_warning_rule): sin proyecto son del tenant y no de otro
    // proyecto
    fun tenantCapable(catalog: ModelCatalog): Set<String> = ProjectScope.ROOTS.filter { !catalog.isRequiredRelation(it, "project") }.toSet()

    // los campos cuyo destino es de otro proyecto (o de ninguno): `targets` da el proyecto de cada destino vinculado
    fun mismatches(
        project: UUID?,
        targets: Map<String, UUID?>
    ): List<String> = if (project == null) emptyList() else targets.filterValues { it != project }.keys.toList()

    // lo mismo con el objeto destino de cada campo: uno de `tenantCapable` sin proyecto es del tenant y no de otro proyecto; con
    // el de otro, sí discrepa
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

// el proyecto del registro `id` de `target` (un objeto con alcance de proyecto) leído como quien escribe: el suyo si es una raíz,
// el de su padre si es un hijo (agreement_clause, engagement_question). un destino que no se lee es NotReadable
suspend fun Records.linkedProject(
    target: String,
    id: UUID
): UUID? {
    val record = readable(target, id)
    val child = ProjectScope.CHILDREN[target] ?: return Records.uuid(record.attributes["project"])
    val parent = Records.uuid(record.attributes[child.first]) ?: return null
    return Records.uuid(readable(child.second, parent).attributes["project"])
}
