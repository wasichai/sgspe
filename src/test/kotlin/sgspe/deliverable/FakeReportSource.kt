package sgspe.deliverable

import java.util.UUID

// una fuente de prueba sin base para los tipos de entregable (plan 8.2c): filas por objeto, vínculos muchos a muchos por
// (objeto, id, relación) y lo que se leyó; lee solo lo que tiene, como ReportSources
open class FakeReportSource : ReportSource {
    val rows = mutableMapOf<String, MutableList<SourceRow>>()
    val links = mutableMapOf<Triple<String, String, String>, List<SourceRow>>()
    val read = mutableSetOf<String>()

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

    override suspend fun one(
        objectName: String,
        id: UUID
    ): SourceRow? = rows[objectName]?.firstOrNull { it.id == id.toString() }.also { read += objectName }

    override suspend fun all(
        objectName: String,
        filters: Map<String, String>
    ): List<SourceRow> = rows[objectName].orEmpty().filter { row -> filters.all { (k, v) -> row.text(k) == v } }.also { read += objectName }

    override suspend fun related(
        objectName: String,
        id: UUID,
        relationship: String,
        target: String
    ): List<SourceRow> = links[Triple(objectName, id.toString(), relationship)].orEmpty().also { read += target }

    override suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>> = emptyList()
}
