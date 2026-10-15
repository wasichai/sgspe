package sgspe.deliverable

import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.Callers
import sgspe.common.Records
import sgspe.dashboard.Dashboards
import sgspe.dashboard.KpiReaders
import sgspe.dashboard.Kpis
import sgspe.dashboard.MonitoringRow
import sgspe.territory.ProjectProximityQueries
import wasichai.core.common.WasichaiException
import java.time.LocalDate
import java.util.UUID

// las lecturas del ensamblado como la plataforma (especificación de F8 §3.3, paso 2; deliverables.md §7.3): el contenido lo
// define el tipo, no los permisos de quien genera. cada fila sale proyectada a los campos que el tipo declara en `reads`
// (ReportSourcesTest), y un objeto que el tipo no declara no se lee. corre dentro de Records.asPlatform de JOB-05: es el único
// archivo de sgspe.deliverable, con DraftFiles y DeliverableGenerationJob, que lee como la plataforma (ArchitectureTest)
@Component
class ReportSources(
    private val records: Records,
    private val callers: Callers,
    private val proximity: ProjectProximityQueries,
    private val dashboards: Dashboards,
    private val readers: KpiReaders
) {
    fun of(
        organizationId: UUID,
        type: DeliverableType
    ): ReportSource = Platform(organizationId, type.reads)

    // las lecturas de AI-04 de sección (especificación de F8 §3.9; decisión 16 del plan 8.2c): como la persona que pide, con
    // Records y las mismas proyecciones que la plataforma; lo que no lee (un objeto sin READ, un registro fuera de su alcance)
    // sale vacío, nunca un error, y la Tabla 9.5 la calcula su lector
    fun person(type: DeliverableType): ReportSource = Person(type.reads)

    private inner class Person(
        private val reads: Map<String, Set<String>>
    ) : ReportSource {
        private fun fields(objectName: String): Set<String> = reads[objectName] ?: throw IllegalStateException("El tipo no declara lecturas de $objectName")

        override suspend fun one(
            objectName: String,
            id: UUID
        ): SourceRow? {
            val allowed = fields(objectName)
            return try {
                records.find(objectName, id)?.let { SourceRow(it.id, it.state, it.attributes.filterKeys { k -> k in allowed }) }
            } catch (_: WasichaiException) {
                null
            }
        }

        override suspend fun all(
            objectName: String,
            filters: Map<String, String>
        ): List<SourceRow> {
            val allowed = fields(objectName)
            return try {
                records.all(objectName, filters, max = MAX_ROWS).map { SourceRow(it.id, it.state, it.attributes.filterKeys { k -> k in allowed }) }
            } catch (_: WasichaiException) {
                emptyList()
            }
        }

        override suspend fun related(
            objectName: String,
            id: UUID,
            relationship: String,
            target: String
        ): List<SourceRow> {
            val allowed = fields(target)
            return try {
                records.related(objectName, id, relationship, max = MAX_ROWS).map { SourceRow(it.id, it.state, it.attributes.filterKeys { k -> k in allowed }) }
            } catch (_: WasichaiException) {
                emptyList()
            }
        }

        // la consulta de F6 de la persona pide el proyecto, no las concesiones: AI-04 no la usa (el predio de T2 no es asistido)
        override suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>> = emptyList()

        override suspend fun monitoring(
            project: UUID,
            start: LocalDate,
            end: LocalDate
        ): List<MonitoringRow>? =
            try {
                dashboards.dashboard(Kpis.MONITORING, period(project, start, end)).rows
            } catch (_: WasichaiException) {
                null
            }
    }

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

        // la Tabla 9.5 con el lector de la plataforma: las mismas funciones que el tablero (RQ-DSH-034, §2.2.16)
        override suspend fun monitoring(
            project: UUID,
            start: LocalDate,
            end: LocalDate
        ): List<MonitoringRow>? {
            check(callers.userOrNull() == null) { "ReportSources lee como la plataforma y no corre dentro de una petición" }
            return dashboards.dashboard(Kpis.MONITORING, period(project, start, end), readers.platform(organizationId)).rows
        }
    }

    companion object {
        // los parámetros del tablero monitoring para un proyecto y un periodo
        fun period(
            project: UUID,
            start: LocalDate,
            end: LocalDate
        ): LinkedMultiValueMap<String, String> =
            LinkedMultiValueMap<String, String>().apply {
                add("project", project.toString())
                add("periodStart", start.toString())
                add("periodEnd", end.toString())
            }

        // el tope de filas de un conjunto leído al ensamblar: pasarlo deja el borrador FALLIDO, nunca recortado
        const val MAX_ROWS = 20_000
    }
}
