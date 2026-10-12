package sgspe.territory

import org.springframework.stereotype.Service
import sgspe.common.ModelCatalog
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.investment.Contributions
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageRequest
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordRow
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
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
        val parts = SpatialParts<Map<String, Any?>>()
        val notApplied = linkedSetOf<String>()
        for (source in layer.sources) {
            if (period != null && source.period == null) notApplied += source.objectName
            // se pide uno más del tope para saber si hubo más
            val remaining = maxFeatures + 1 - parts.rows.size
            if (remaining <= 0) break
            parts.add(placed(layer.id, source, projects, box, period?.takeIf { source.period != null }, remaining))
        }
        when (layer.aggregate) {
            null -> Unit
            LayerAggregate.STAKEHOLDERS_BY_STANCE -> {
                // los grupos de interés no tienen fecha
                if (period != null) notApplied += LocalityAggregateSql.STAKEHOLDER
                val byStance = readable.canRead("${LocalityAggregateSql.STAKEHOLDER}.${LocalityAggregateSql.STANCE}")
                if (!byStance) parts.missing("${LocalityAggregateSql.STAKEHOLDER}.${LocalityAggregateSql.STANCE}")
                val remaining = maxFeatures + 1 - parts.rows.size
                if (remaining > 0) parts.add(stakeholders(layer.id, projects, box, remaining, byStance))
            }
            LayerAggregate.CONTRIBUTIONS_BY_CURRENCY -> {
                val remaining = maxFeatures + 1 - parts.rows.size
                if (remaining > 0) parts.add(contributions(layer.id, projects, box, period, remaining))
            }
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

    // LYR-17: un elemento por localidad con el total de sus grupos de interés y, si quien llama lee la postura, el conteo por
    // postura (SIN DATO para la vacía); nunca el id ni el punto de un grupo de interés (RQ-GIS-015)
    private suspend fun stakeholders(
        layer: String,
        projects: List<UUID>,
        box: LayerBox?,
        limit: Int,
        byStance: Boolean
    ): GateOutcome<Map<String, Any?>> {
        val fields = linkedSetOf(TerritoryNames.LOCALITY)
        if (byStance) fields += LocalityAggregateSql.STANCE
        if (projects.isNotEmpty()) fields += "project"
        val reads = listOf(GateRead(LocalityAggregateSql.STAKEHOLDER, fields, principal = true), LocalityAggregateSql.LOCALITY_READ)
        val outcome =
            gate.select(reads, { bind -> LocalityAggregateSql.stakeholders(this, bind, projects, byStance, box, limit) }) { row ->
                LocalityAggregateSql.row(row) { r ->
                    mapOf(LocalityAggregateSql.STANCE to r.get("stance", String::class.java), "n" to r.get("n", Long::class.javaObjectType))
                }
            }
        return localityFeatures(layer, LocalityAggregateSql.STAKEHOLDER, outcome) { rows ->
            val counts = rows.map { (it.values[LocalityAggregateSql.STANCE] as String?) to (it.values["n"] as Long) }
            mapOf(
                "total" to counts.sumOf { it.second },
                "byStance" to if (byStance) counts.associate { (stance, n) -> (stance ?: LocalityAggregateSql.NO_STANCE) to n } else null
            )
        }
    }

    // los aportes de LYR-23: un elemento por localidad con una fila por moneda (cuántos y cuánto); con periodo, la compuerta lee
    // también las entregas para acotarlos
    private suspend fun contributions(
        layer: String,
        projects: List<UUID>,
        box: LayerBox?,
        period: Period?,
        limit: Int
    ): GateOutcome<Map<String, Any?>> {
        val fields = linkedSetOf(TerritoryNames.LOCALITY, "currency", "total_amount")
        if (projects.isNotEmpty()) fields += "project"
        val reads =
            listOfNotNull(
                GateRead(Contributions.CONTRIBUTION, fields, principal = true),
                LocalityAggregateSql.LOCALITY_READ,
                period?.let { GateRead(Contributions.DELIVERY, setOf(Contributions.CONTRIBUTION, "delivery_date")) }
            )
        val outcome =
            gate.select(reads, { bind -> LocalityAggregateSql.contributions(this, bind, projects, period, box, limit) }) { row ->
                LocalityAggregateSql.row(row) { r ->
                    mapOf(
                        "currency" to r.get("currency", String::class.java),
                        "n" to r.get("n", Long::class.javaObjectType),
                        "amount" to r.get("amount", BigDecimal::class.java)
                    )
                }
            }
        return localityFeatures(layer, Contributions.CONTRIBUTION, outcome) { rows ->
            mapOf("contributions" to rows.map { mapOf("currency" to it.values["currency"], "count" to it.values["n"], "totalAmount" to it.values["amount"]) })
        }
    }

    // un elemento por localidad (las filas llegan ordenadas por localidad): el punto de la localidad, su nombre y tipo, y las
    // cifras que arma `figures` con sus filas
    private fun localityFeatures(
        layer: String,
        aggregate: String,
        outcome: GateOutcome<LocalityRow>,
        figures: (List<LocalityRow>) -> Map<String, Any?>
    ): GateOutcome<Map<String, Any?>> =
        when (outcome) {
            is GateOutcome.Unavailable -> outcome
            is GateOutcome.Rows ->
                GateOutcome.Rows(
                    outcome.rows.groupBy { it.zone }.values.map { rows ->
                        val first = rows.first()
                        val properties =
                            linkedMapOf<String, Any?>(
                                "__layer" to layer,
                                "__object" to TerritoryNames.LOCALITY,
                                "__id" to first.zone.toString(),
                                "__state" to null,
                                "__aggregate" to aggregate,
                                "name" to first.name,
                                "locality_type" to first.localityType
                            )
                        properties += figures(rows)
                        mapOf(
                            "type" to "Feature",
                            "id" to "${first.zone}:${first.field}",
                            "geometry" to JSON.readValue(first.geometry, Map::class.java),
                            "properties" to properties
                        )
                    }
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
