package sgspe.deliverable

import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Records
import sgspe.territory.ProjectProximityQueries
import java.util.UUID

// las lecturas del ensamblado como la plataforma (especificación de F8 §3.3, paso 2; deliverables.md §7.3): el contenido lo
// define el tipo, no los permisos de quien genera. cada fila sale proyectada a los campos que el tipo declara en `reads`
// (ReportSourcesTest), y un objeto que el tipo no declara no se lee. corre dentro de Records.asPlatform de JOB-05: es el único
// archivo de sgspe.deliverable, con DraftFiles y DeliverableGenerationJob, que lee como la plataforma (ArchitectureTest)
@Component
class ReportSources(
    private val records: Records,
    private val callers: Callers,
    private val proximity: ProjectProximityQueries
) {
    fun of(
        organizationId: UUID,
        type: DeliverableType
    ): ReportSource = Platform(organizationId, type.reads)

    private inner class Platform(
        private val organizationId: UUID,
        private val reads: Map<String, Set<String>>
    ) : ReportSource {
        private fun fields(objectName: String): Set<String> = reads[objectName] ?: throw IllegalStateException("El tipo no declara lecturas de $objectName")

        private fun project(
            objectName: String,
            id: String,
            state: String?,
            attributes: Map<String, Any?>
        ): SourceRow {
            val allowed = fields(objectName)
            return SourceRow(id, state, attributes.filterKeys { it in allowed })
        }

        override suspend fun one(
            objectName: String,
            id: UUID
        ): SourceRow? {
            fields(objectName)
            return records.find(objectName, id)?.let { project(objectName, it.id, it.state, it.attributes) }
        }

        override suspend fun all(
            objectName: String,
            filters: Map<String, String>
        ): List<SourceRow> {
            fields(objectName)
            return records.all(objectName, filters, max = MAX_ROWS).map { project(objectName, it.id, it.state, it.attributes) }
        }

        override suspend fun related(
            objectName: String,
            id: UUID,
            relationship: String,
            target: String
        ): List<SourceRow> {
            check(callers.userOrNull() == null) { "ReportSources lee como la plataforma y no corre dentro de una petición" }
            val allowed = fields(target)
            val rows = records.relatedAsPlatform(organizationId, objectName, id, relationship)
            check(rows.size <= MAX_ROWS) { "La relación $relationship pasa de $MAX_ROWS vínculos" }
            return rows.map { row -> SourceRow(row.id.toString(), row.state, row.attributes.filterKeys { it in allowed }) }
        }

        override suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>> =
            proximity.concessionTerritoriesAsPlatform(organizationId, concessions)
    }

    companion object {
        // el tope de filas de un conjunto leído al ensamblar: pasarlo deja el borrador FALLIDO, nunca recortado
        const val MAX_ROWS = 20_000
    }
}
