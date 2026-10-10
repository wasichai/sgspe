package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordWrite

// la regla «mismo proyecto» de los objetos de F4 (workflows.md, regla 10; api.md §3.3): una relación hacia un registro con
// alcance de proyecto (los campos salen del catálogo, ProjectLinks.fieldsOf) y los vínculos social_risk_stakeholder e
// incident_stakeholder deben quedar en el proyecto del registro; si no, 409 PROJECT_MISMATCH sobre el campo o la relación. una
// regla de alerta del tenant (sin proyecto) no discrepa. lee los destinos como quien escribe: uno que no lee es 403
// OBJECT_NOT_READABLE. compromisos y candidatos usan la misma pieza desde sus reglas (ProjectLinkRule, CandidateLinkRule), así
// que commitment.origin_grievance y commitment.social_risk se juzgan allí
@Component
@Order(GuardOrder.DOMAIN)
class ProjectLinkGuard(
    private val records: Records,
    private val view: WriteView,
    private val catalog: ModelCatalog
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return try {
            if (change.isLink()) links(change) else fields(change)
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun fields(change: RecordWrite): List<Finding> =
        ProjectLinks
            .judge(change.objectName, ProjectLinks.fieldsOf(catalog, change.objectName), ProjectLinks.tenantCapable(catalog), change, records, view)
            .map { mismatch(change.objectName, it) }

    // un vínculo M2M que se crea, juzgado en el extremo que es la fuente de la relación (wasichai llama a las guardas con cada
    // extremo): el proyecto del registro contra el del grupo de interés. deshacer un vínculo no se juzga
    private suspend fun links(change: RecordWrite): List<Finding> {
        val project = Records.uuid(change.before.orEmpty()["project"]) ?: return emptyList()
        return LINKS.mapNotNull { name ->
            val relationship = catalog.manyToMany(name)?.takeIf { it.source == change.objectName } ?: return@mapNotNull null
            val other = Records.uuid(change.attributes.orEmpty()["rel:$name"]) ?: return@mapNotNull null
            if (ProjectLinks
                    .mismatches(
                        project,
                        mapOf(name to records.linkedProject(relationship.target, other))
                    ).isEmpty()
            ) {
                null
            } else {
                mismatch(change.objectName, name)
            }
        }
    }

    private fun mismatch(
        objectName: String,
        field: String
    ) = Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un registro de $objectName solo referencia lo de su proyecto", field)

    companion object {
        // los objetos de F4 (BC-07, BC-08 y BC-09)
        val OBJECTS: Set<String> =
            setOf(
                "grievance",
                "inquiry",
                "stakeholder_request",
                "social_risk",
                "risk_assessment",
                "mitigation_measure",
                "potential_impact",
                "incident",
                "media_mention",
                "early_warning_signal",
                "early_warning_rule",
                "early_warning"
            )

        // los vínculos MANY_TO_MANY de F4 hacia grupos de interés
        val LINKS: List<String> = listOf("social_risk_stakeholder", "incident_stakeholder")
    }
}
