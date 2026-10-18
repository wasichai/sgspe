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
import sgspe.territory.ReportMapFeatures
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
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
    private val readers: KpiReaders,
    private val features: ReportMapFeatures
) {
    fun of(
        organizationId: UUID,
        type: DeliverableType
    ): ReportSource = Platform(organizationId, type.reads)

    // las lecturas de AI-04 de sección (especificación de F8 §3.9; decisión 16 del plan 8.2c): como la persona que pide, con
    // Records y las mismas proyecciones que la plataforma; lo que no lee (un objeto sin READ, un registro fuera de su alcance)
    // sale vacío (readable), y la Tabla 9.5 la calcula su lector. el tope de filas (400 TOO_MANY_RECORDS) y cualquier otro
    // error se propagan: la corrida falla a la vista, nunca redacta con tablas vacías
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
            return readable(null) { records.find(objectName, id)?.let { SourceRow(it.id, it.state, it.attributes.filterKeys { k -> k in allowed }) } }
        }

        override suspend fun all(
            objectName: String,
            filters: Map<String, String>
        ): List<SourceRow> {
            val allowed = fields(objectName)
            return readable(emptyList()) {
                records.all(objectName, filters, max = MAX_ROWS).map { SourceRow(it.id, it.state, it.attributes.filterKeys { k -> k in allowed }) }
            }
        }

        override suspend fun related(
            objectName: String,
            id: UUID,
            relationship: String,
            target: String
        ): List<SourceRow> {
            val allowed = fields(target)
            return readable(emptyList()) {
                records.related(objectName, id, relationship, max = MAX_ROWS).map { SourceRow(it.id, it.state, it.attributes.filterKeys { k -> k in allowed }) }
            }
        }

        // la consulta de F6 de la persona pide el proyecto, no las concesiones: AI-04 no la usa (el predio de T2 no es asistido)
        override suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>> = emptyList()

        // las microcuencas de 4.2 son SQL propio como la plataforma: AI-04 no las usa
        override suspend fun microbasins(localities: List<UUID>): Map<UUID, String> = emptyMap()

        override suspend fun monitoring(
            project: UUID,
            start: LocalDate,
            end: LocalDate
        ): List<MonitoringRow>? = readable(null) { dashboards.dashboard(Kpis.MONITORING, period(project, start, end)).rows }
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

        // 4.2 de T1 por microcuenca (#80): el cruce espacial por SpatialGate como la plataforma, con lo que el tipo declara
        override suspend fun microbasins(localities: List<UUID>): Map<UUID, String> {
            check(callers.userOrNull() == null) { "ReportSources lee como la plataforma y no corre dentro de una petición" }
            fields("environmental_feature")
            fields("locality")
            return features.microbasins(organizationId, localities)
        }

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
        // una lectura de la persona: lo que no lee (sin READ sobre el objeto, fuera de su alcance o inexistente) es `empty`, como
        // en PersonNames; todo lo demás se propaga
        suspend fun <T> readable(
            empty: T,
            read: suspend () -> T
        ): T =
            try {
                read()
            } catch (_: ForbiddenException) {
                empty
            } catch (_: NotFoundException) {
                empty
            }

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
