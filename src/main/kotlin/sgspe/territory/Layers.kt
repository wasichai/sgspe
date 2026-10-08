package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import wasichai.core.common.NotFoundException
import wasichai.core.common.PageRequest
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQuery
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.gis.BboxQuery
import java.util.UUID

// una capa del catálogo de gis.md §2.2: de qué objeto y geometría sale, con qué filtro fijo y si se acota por proyecto
data class LayerDefinition(
    val id: String,
    val sources: List<LayerSource>
)

data class LayerSource(
    val objectName: String,
    val geometries: List<String>,
    // filtro fijo de igualdad o de conjunto (campo -> valores)
    val fixed: Map<String, Set<String>> = emptyMap(),
    // cómo se acota por proyecto: por su campo project, por ser el proyecto mismo o por la relación project_concession
    val projectScope: LayerScope = LayerScope.NONE,
    val excludeSuperseded: Boolean = false,
    val states: Set<String>? = null
)

enum class LayerScope { NONE, FIELD, SELF, CONCESSION }

// GET /api/sgspe/territory/layers/{layer} en F2 (api.md §4.2, gis.md §2.2 y §2.4): las dieciséis capas básicas LYR-01 a
// LYR-16 del mapa del proyecto como GeoJSON EPSG:4326 con la forma de wasichai-gis (id `<registro>:<geometría>`,
// geometry, properties). el GeoJSON sale de Records (las geometrías ya vienen en EPSG:4326, sin cuentas de GeoSql) y no
// depende de GeoServer, que F2 no publica (P14). lee con RecordService como quien llama, así que un elemento solo lleva
// los atributos que esa persona puede leer, los mismos de la
// API genérica (RQ-GIS-001, RQ-SEC-011): una partida registral o el nombre de una persona restringidos no salen. ninguna
// capa de F2 recorre relaciones hacia personas (no se muestra land_right ni stakeholder). sin READ sobre un objeto de la
// capa, 403. la lectura es acotada: un tope de MAX_FEATURES elementos por capa (el resultado dice `truncated` si hubo más)
// y bbox en SQL, con páginas de PageRequest.MAX_SIZE ordenadas por id. project acota las capas de proyecto; sin él se ve
// todo el tenant (alcance opción A). periodStart y periodEnd se aceptan para las capas temáticas de F6 y no filtran estas
// capas, que no tienen fecha. los errores llevan su código en errors[0].message (P15)
@Service
class Layers(
    private val records: Records
) {
    suspend fun features(
        layer: String,
        projects: List<UUID>,
        bbox: String?
    ): Map<String, Any?> {
        val definition =
            CATALOG[layer]
                ?: throw Finding(
                    "LAYER_NOT_FOUND",
                    "La capa $layer no existe o no está disponible en esta fase",
                    "layer",
                    status = HttpStatus.NOT_FOUND
                ).toCodedException()
        val box = bbox?.let(::validBbox)
        val features = mutableListOf<Map<String, Any?>>()
        // se pide uno más del tope para saber si hubo más
        for (source in definition.sources) {
            val concessions = if (source.projectScope == LayerScope.CONCESSION) concessionsOf(projects) else emptySet()
            for (geometry in source.geometries) {
                val remaining = MAX_FEATURES + 1 - features.size
                if (remaining <= 0) break
                features += read(definition.id, source, geometry, projects, box, concessions, remaining)
            }
        }
        val truncated = features.size > MAX_FEATURES
        return mapOf("type" to "FeatureCollection", "features" to features.take(MAX_FEATURES), "truncated" to truncated)
    }

    // los ids de las concesiones de los proyectos pedidos (como los ve quien llama); un proyecto que no existe no aporta
    private suspend fun concessionsOf(projects: List<UUID>): Set<String> =
        projects
            .flatMap {
                try {
                    records.related("project", it, "project_concession")
                } catch (_: NotFoundException) {
                    emptyList()
                }
            }.map { it.id.toString() }
            .toSet()

    private suspend fun read(
        layer: String,
        source: LayerSource,
        geometry: String,
        projects: List<UUID>,
        bbox: String?,
        concessions: Set<String>,
        limit: Int
    ): List<Map<String, Any?>> {
        val criteria = mutableListOf<RecordCriterion>(hasValue(geometry))
        source.fixed.forEach { (field, values) -> criteria += Criteria.textIn(field, values) }
        source.states?.let { criteria += Criteria.inState(it) }
        if (source.excludeSuperseded) criteria += notTrue("superseded")
        if (projects.isNotEmpty()) {
            when (source.projectScope) {
                LayerScope.FIELD -> criteria += Criteria.anyOf("project", projects)
                LayerScope.SELF -> criteria += RecordCriterion { _, bind -> "id = ANY(${bind(projects.toTypedArray())})" }
                LayerScope.CONCESSION, LayerScope.NONE -> Unit
            }
        }
        bbox?.let { BboxQuery().parse(mapOf("bbox" to it, "geometry" to geometry))?.let(criteria::add) }
        val features = mutableListOf<Map<String, Any?>>()
        var page = 0
        do {
            val size = minOf(PageRequest.MAX_SIZE, limit - features.size)
            val (_, rows) =
                records.rows(
                    source.objectName,
                    RecordQuery(page = PageRequest.of(page, size), sort = "id", criteria = criteria, count = false)
                )
            for (row in rows) {
                @Suppress("UNCHECKED_CAST")
                val shape = (row.sections[Records.GEOMETRIES] as Map<String, Any?>?)?.get(geometry) ?: continue
                val extra =
                    mutableMapOf<String, Any?>(
                        "__layer" to layer,
                        "__object" to source.objectName,
                        "__id" to row.id.toString(),
                        "__state" to row.state
                    )
                if (source.projectScope == LayerScope.CONCESSION && projects.isNotEmpty()) extra["ofProject"] = row.id.toString() in concessions
                features += mapOf("type" to "Feature", "id" to "${row.id}:$geometry", "geometry" to shape, "properties" to row.attributes + extra)
            }
            page++
        } while (rows.size >= size && features.size < limit)
        return features
    }

    // el campo tiene valor. si quien llama no puede leer el campo, la definición no lo trae y no hay nada que mostrar
    private fun hasValue(field: String): RecordCriterion =
        RecordCriterion { definition, _ -> columnOf(definition, field)?.let { "$it IS NOT NULL" } ?: "FALSE" }

    // el campo no es verdadero (falso o vacío). si no se puede leer no se sabe cuáles están reemplazados: no se muestra nada
    private fun notTrue(field: String): RecordCriterion = RecordCriterion { definition, _ -> columnOf(definition, field)?.let { "$it IS NOT TRUE" } ?: "FALSE" }

    private fun columnOf(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    // minX,minY,maxX,maxY en EPSG:4326: cuatro números finitos dentro del globo y con el mínimo no mayor que el máximo
    private fun validBbox(raw: String): String {
        val parts = raw.split(",").map { it.trim().toDoubleOrNull() }
        val ok =
            parts.size == 4 &&
                parts.all { it != null && it.isFinite() } &&
                parts.map { it!! }.let { (minX, minY, maxX, maxY) ->
                    minX in -180.0..180.0 && maxX in -180.0..180.0 && minY in -90.0..90.0 && maxY in -90.0..90.0 && minX <= maxX && minY <= maxY
                }
        if (!ok) {
            throw Finding(
                "INVALID_BBOX",
                "bbox debe ser minX,minY,maxX,maxY en EPSG:4326, con el mínimo no mayor que el máximo",
                "bbox",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        return raw
    }

    companion object {
        const val MAX_FEATURES = 5000
        const val MAX_PROJECTS = 50
        private val LOCALITIES = setOf("CASERIO", "SECTOR", "CENTRO POBLADO", "ANEXO")

        private fun components(
            id: String,
            types: Set<String>,
            vararg geometries: String
        ) = LayerDefinition(
            id,
            listOf(LayerSource("project_component", geometries.toList(), mapOf("component_type" to types), LayerScope.FIELD, excludeSuperseded = true))
        )

        val CATALOG: Map<String, LayerDefinition> =
            listOf(
                LayerDefinition("LYR-01", listOf(LayerSource("administrative_unit", listOf("boundary")))),
                LayerDefinition("LYR-02", listOf(LayerSource("locality", listOf("location", "boundary"), mapOf("locality_type" to LOCALITIES)))),
                LayerDefinition("LYR-03", listOf(LayerSource("locality", listOf("location"), mapOf("locality_type" to setOf("CIUDAD"))))),
                LayerDefinition("LYR-04", listOf(LayerSource("road_access", listOf("trace")))),
                LayerDefinition("LYR-05", listOf(LayerSource("community", listOf("territory")))),
                LayerDefinition("LYR-06", listOf(LayerSource("community_sector", listOf("boundary")))),
                LayerDefinition("LYR-07", listOf(LayerSource("mining_concession", listOf("boundary"), projectScope = LayerScope.CONCESSION))),
                LayerDefinition("LYR-08", listOf(LayerSource("environmental_feature", listOf("boundary", "trace", "location")))),
                LayerDefinition("LYR-09", listOf(LayerSource("project", listOf("location", "interest_area"), projectScope = LayerScope.SELF))),
                components("LYR-10", setOf("SONDAJE"), "location"),
                components("LYR-11", setOf("PLATAFORMA", "CAMPAMENTO", "CAPTACION"), "footprint", "location"),
                components("LYR-12", setOf("ACCESO"), "trace"),
                LayerDefinition(
                    "LYR-13",
                    listOf(LayerSource("area_of_influence", listOf("boundary"), projectScope = LayerScope.FIELD, states = setOf("approved")))
                ),
                LayerDefinition(
                    "LYR-14",
                    listOf(LayerSource("land_parcel", listOf("boundary"), mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL"))))
                ),
                LayerDefinition(
                    "LYR-15",
                    listOf(LayerSource("land_parcel", listOf("boundary"), mapOf("tenure_type" to setOf("PROPIEDAD DE LA EMPRESA"))))
                ),
                LayerDefinition("LYR-16", listOf(LayerSource("school", listOf("location")), LayerSource("health_facility", listOf("location"))))
            ).associateBy { it.id }
    }
}

@RestController
@RequestMapping("/api/sgspe/territory/layers")
class LayersController(
    private val layers: Layers
) {
    @GetMapping("/{layer}")
    suspend fun layer(
        @PathVariable layer: String,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("project", "periodStart", "periodEnd", "bbox"))
        val projects =
            params["project"].orEmpty().map {
                runCatching { UUID.fromString(it) }.getOrElse {
                    throw Finding("INVALID_PROJECT", "project debe ser un UUID", "project", status = HttpStatus.BAD_REQUEST).toCodedException()
                }
            }
        if (projects.size > Layers.MAX_PROJECTS) {
            throw Finding(
                "TOO_MANY_PROJECTS",
                "Pida como máximo ${Layers.MAX_PROJECTS} proyectos por capa",
                "project",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        return layers.features(layer, projects, params.getFirst("bbox"))
    }
}
