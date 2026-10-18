package sgspe.deliverable

import java.util.UUID

// una fuente de prueba sin base para los tipos de entregable (plan 8.2c): filas por objeto, vínculos muchos a muchos por
// (objeto, id, relación) y lo que se leyó; lee solo lo que tiene, como ReportSources
// con `reads` hace cumplir lo que el tipo declara, como ReportSources: un objeto no declarado falla y cada fila sale
// proyectada a los campos declarados (sin `reads`, lee todo)
open class FakeReportSource(
    private val reads: Map<String, Set<String>>? = null
) : ReportSource {
    val rows = mutableMapOf<String, MutableList<SourceRow>>()
    val links = mutableMapOf<Triple<String, String, String>, List<SourceRow>>()
    val read = mutableSetOf<String>()

    // la microcuenca de cada localidad (id de la localidad → nombre), como la daría el cruce espacial de ReportSources
    val basins = mutableMapOf<String, String>()

    fun add(
        objectName: String,
        id: String,
        state: String? = null,
        vararg attributes: Pair<String, Any?>
    ): String {
        rows.getOrPut(objectName) { mutableListOf() } += SourceRow(id, state, attributes.toMap())
        return id
    }

    fun link(
        objectName: String,
        id: String,
        relationship: String,
        target: String,
        vararg ids: String
    ) {
        links[Triple(objectName, id, relationship)] = ids.mapNotNull { other -> rows[target]?.firstOrNull { it.id == other } }
    }

    private fun fields(objectName: String): Set<String>? =
        reads?.let { it[objectName] ?: throw IllegalStateException("El tipo no declara lecturas de $objectName") }

    private fun project(
        objectName: String,
        row: SourceRow
    ): SourceRow = fields(objectName)?.let { allowed -> row.copy(attributes = row.attributes.filterKeys { it in allowed }) } ?: row

    override suspend fun one(
        objectName: String,
        id: UUID
    ): SourceRow? =
        rows[objectName]?.firstOrNull { it.id == id.toString() }?.let { project(objectName, it) }.also {
            fields(objectName)
            read += objectName
        }

    override suspend fun all(
        objectName: String,
        filters: Map<String, String>
    ): List<SourceRow> =
        rows[objectName]
            .orEmpty()
            .filter { row -> filters.all { (k, v) -> row.text(k) == v } }
            .map { project(objectName, it) }
            .also {
                fields(objectName)
                read += objectName
            }

    override suspend fun related(
        objectName: String,
        id: UUID,
        relationship: String,
        target: String
    ): List<SourceRow> =
        links[Triple(objectName, id.toString(), relationship)].orEmpty().map { project(target, it) }.also {
            fields(target)
            read += target
        }

    override suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>> = emptyList()

    override suspend fun microbasins(localities: List<UUID>): Map<UUID, String> {
        fields("environmental_feature")
        fields("locality")
        read += "environmental_feature"
        return localities.mapNotNull { id -> basins[id.toString()]?.let { id to it } }.toMap()
    }
}
