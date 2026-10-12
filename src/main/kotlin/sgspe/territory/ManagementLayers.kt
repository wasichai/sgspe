package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.toCodedException
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageRequest
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordRow
import wasichai.core.metadata.MetadataService
import java.util.UUID

// las capas de gestión LYR-17 a LYR-24 de GET /api/sgspe/territory/layers/{layer} (gis.md §2.2 y §2.4, plan 6.2c). cada fuente
// de registros se ubica por SpatialGate: ids y el punto que se dibuja, con la organización y todos los RecordReadScope de cada
// tabla, así que una QCS confidencial solo sale a quien la lee. sus atributos se leen después por Records como quien llama: un
// elemento lleva lo mismo que la API genérica, sin los campos restringidos que no lee, y un registro que Records no devuelve no
// sale. nunca asPlatform. sin READ sobre un objeto de la capa, 403 antes de leer nada; si falta leer un campo o un objeto
// secundario, la capa trae lo que sí se leyó con available: false y missingFields, nunca un cero (RQ-DSH-032). el tope es el
// de F2 (sgspe.territory.layers.max-features), compartido por las fuentes en orden, con `truncated`. una fuente sin fecha no
// se acota por el periodo y lo dice en periodNotApplied
@Service
class ManagementLayers(
    private val gate: SpatialGate,
    private val records: Records,
    private val metadata: MetadataService,
    private val catalog: ModelCatalog,
    properties: LayersProperties
) {
    private val maxFeatures = properties.maxFeatures

    suspend fun features(
        layer: ManagementLayer,
        projects: List<UUID>,
        box: LayerBox?,
        period: Period?
    ): Map<String, Any?> {
        val readable = Readability(metadata)
        for (objectName in layer.objects) {
            if (!readable.canRead(objectName)) throw ForbiddenException("La capa ${layer.id} exige permiso de lectura sobre $objectName")
        }
        if (layer.aggregate != null) {
            // los agregados (LYR-17 y los aportes de LYR-23) llegan con las Tareas 4 y 5 del plan 6.2c
            throw Finding("LAYER_NOT_FOUND", "La capa ${layer.id} no está disponible todavía", "layer", status = HttpStatus.NOT_FOUND)
                .toCodedException()
        }
        val parts = SpatialParts<Map<String, Any?>>()
        val notApplied = linkedSetOf<String>()
        for (source in layer.sources) {
            if (period != null && source.period == null) notApplied += source.objectName
            // se pide uno más del tope para saber si hubo más
            val remaining = maxFeatures + 1 - parts.rows.size
            if (remaining <= 0) break
            parts.add(placed(layer.id, source, projects, box, period?.takeIf { source.period != null }, remaining))
        }
        return collection(parts, notApplied)
    }

    private fun collection(
        parts: SpatialParts<Map<String, Any?>>,
        notApplied: Set<String>
    ): Map<String, Any?> {
        val features = parts.rows
        val out = linkedMapOf<String, Any?>("type" to "FeatureCollection")
        out += parts.response("features", features.take(maxFeatures), truncated = features.size > maxFeatures)
        if (notApplied.isNotEmpty()) out["periodNotApplied"] = notApplied.toList()
        return out
    }

    private suspend fun placed(
        layer: String,
        source: PlacedSource,
        projects: List<UUID>,
        box: LayerBox?,
        period: Period?,
        limit: Int
    ): GateOutcome<Map<String, Any?>> {
        val outcome =
            gate.select(PlacedSql.reads(source, projects, period), { bind -> PlacedSql.sql(this, bind, source, projects, period, box, limit) }) { row ->
                PlacedRow(
                    row.get("id", UUID::class.java)!!,
                    row.get("kind", String::class.java)!!,
                    row.get("target", UUID::class.java),
                    row.get("geometry", String::class.java)!!
                )
            }
        val placed =
            when (outcome) {
                is GateOutcome.Unavailable -> return outcome
                is GateOutcome.Rows -> outcome.rows
            }
        val rows = rowsOf(source.objectName, placed.map { it.id })
        return GateOutcome.Rows(placed.mapNotNull { p -> rows[p.id]?.let { feature(layer, source, p, it) } })
    }

    // los registros por id como los ve quien llama (Records.rows: permisos de objeto y de campo y alcance), en páginas del
    // tamaño máximo de wasichai. el estado solo en los objetos con workflow: Records.rows no lo trae si no se pide, y pedirlo
    // en un objeto sin workflow rompería la consulta
    private suspend fun rowsOf(
        objectName: String,
        ids: List<UUID>
    ): Map<UUID, RecordRow> {
        val stateful = objectName in catalog.workflowObjects
        return ids
            .chunked(PageRequest.MAX_SIZE)
            .flatMap { chunk ->
                records
                    .rows(
                        objectName,
                        RecordQuery(page = PageRequest.of(0, PageRequest.MAX_SIZE), sort = "id", ids = chunk, count = false, withState = stateful)
                    ).second
            }.associateBy { it.id }
    }

    private fun feature(
        layer: String,
        source: PlacedSource,
        placed: PlacedRow,
        row: RecordRow
    ): Map<String, Any?> {
        val own = placed.kind == PlacedSql.OWN
        val extra = linkedMapOf<String, Any?>("__layer" to layer, "__object" to source.objectName, "__id" to row.id.toString(), "__state" to row.state)
        if (!own) {
            extra["__target"] = Placements.BY_RELATION.getValue(placed.kind).objectName
            extra["__targetId"] = placed.target?.toString()
        }
        return mapOf(
            "type" to "Feature",
            "id" to "${row.id}:${if (own) source.geometry else placed.kind}",
            "geometry" to JSON.readValue(placed.geometry, Map::class.java),
            "properties" to properties(source, row) + extra
        )
    }

    // los atributos que quien llama lee, tal como los da Records. `source` lo usa LYR-24 (Tarea 6 del plan 6.2c)
    @Suppress("UNUSED_PARAMETER")
    private fun properties(
        source: PlacedSource,
        row: RecordRow
    ): Map<String, Any?> = row.attributes

    companion object {
        val JSON: JsonMapper = JsonMapper.builder().build()
    }
}
