package sgspe.territory

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
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
import wasichai.core.common.ForbiddenException
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

// el tope de elementos por capa (api.md §4.2): 5000 salvo que sgspe.territory.layers.max-features diga otro; las pruebas lo
// bajan para no crear miles de registros
@ConfigurationProperties("sgspe.territory.layers")
data class LayersProperties(
    val maxFeatures: Int = 5000
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LayersProperties::class)
class LayersConfiguration

// GET /api/sgspe/territory/layers/{layer} en F2 (api.md §4.2, gis.md §2.2 y §2.4): las dieciséis capas básicas LYR-01 a
// LYR-16 del mapa del proyecto como GeoJSON EPSG:4326 con la forma de wasichai-gis (id `<registro>:<geometría>`,
// geometry, properties). el GeoJSON sale de Records (las geometrías ya vienen en EPSG:4326, sin cuentas de GeoSql) y no
// depende de GeoServer, que F2 no publica (P14). lee con RecordService como quien llama, así que un elemento solo lleva
// los atributos que esa persona puede leer, los mismos de la API genérica (RQ-GIS-001, RQ-SEC-011): una partida registral
// o el nombre de una persona restringidos no salen. ninguna capa de F2 recorre relaciones hacia personas (no se muestra
// land_right ni stakeholder). sin READ sobre un objeto de la capa, 403.
// la lectura es acotada: un tope de maxFeatures elementos por capa (`truncated` dice si había más) y bbox en SQL. se lee
// por páginas de PageRequest.MAX_SIZE ordenadas por id, todas del mismo tamaño porque wasichai desplaza page * size; el
// tope se aplica al final con take. project acota las capas de proyecto y en las demás se ignora (gis.md §3.3: no
// pertenecen a un solo proyecto); sin él se ve todo el tenant (alcance opción A). periodStart y periodEnd se aceptan para
// las capas temáticas de F6 y no filtran estas capas, que no tienen fecha. los errores llevan su código en
// errors[0].message (P15). un campo de filtro que quien llama no puede leer deja la capa vacía (falla cerrado), no es un 400
@Service
class Layers(
    private val records: Records,
    properties: LayersProperties
) {
    private val maxFeatures = properties.maxFeatures

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
        val box = bbox?.let(::normalizeBbox)
        val features = mutableListOf<Map<String, Any?>>()
        // se pide uno más del tope para saber si hubo más
        for (source in definition.sources) {
            val concessions = if (source.projectScope == LayerScope.CONCESSION) concessionsOf(projects) else emptySet()
            for (geometry in source.geometries) {
                val remaining = maxFeatures + 1 - features.size
                if (remaining <= 0) break
                features += read(definition.id, source, geometry, projects, box, concessions, remaining)
            }
        }
        val truncated = features.size > maxFeatures
        return mapOf("type" to "FeatureCollection", "features" to features.take(maxFeatures), "truncated" to truncated)
    }

    // los ids de las concesiones de los proyectos pedidos, como los ve quien llama. un proyecto que no existe o que no puede
    // leer no aporta ninguna: la capa sigue siendo legible por su READ sobre mining_concession y un 403 de la relación no
    // debe filtrarse como 403 de la capa (hoy todo rol con READ sobre mining_concession lo tiene también sobre project)
    private suspend fun concessionsOf(projects: List<UUID>): Set<String> =
        projects
            .flatMap {
                try {
                    records.related("project", it, "project_concession")
                } catch (_: NotFoundException) {
                    emptyList()
                } catch (_: ForbiddenException) {
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
        source.fixed.forEach { (field, values) -> criteria += textIn(field, values) }
        source.states?.let { criteria += Criteria.inState(it) }
        if (source.excludeSuperseded) criteria += notTrue("superseded")
        if (projects.isNotEmpty()) {
            when (source.projectScope) {
                LayerScope.FIELD -> criteria += projectIn(projects)
                LayerScope.SELF -> criteria += RecordCriterion { _, bind -> "id = ANY(${bind(projects.toTypedArray())})" }
                LayerScope.CONCESSION, LayerScope.NONE -> Unit
            }
        }
        bbox?.let { BboxQuery().parse(mapOf("bbox" to it, "geometry" to geometry))?.let(criteria::add) }
        val features = mutableListOf<Map<String, Any?>>()
        var page = 0
        do {
            val (_, rows) =
                records.rows(
                    source.objectName,
                    RecordQuery(page = PageRequest.of(page, PageRequest.MAX_SIZE), sort = "id", criteria = criteria, count = false)
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
        } while (rows.size >= PageRequest.MAX_SIZE && features.size < limit)
        return features.take(limit)
    }

    // el campo tiene valor. si quien llama no puede leer el campo, la definición no lo trae y no hay nada que mostrar
    private fun hasValue(field: String): RecordCriterion = RecordCriterion { definition, _ -> condition(columnOf(definition, field)) { "$it IS NOT NULL" } }

    // el campo no es verdadero (falso o vacío). si no se puede leer no se sabe cuáles están reemplazados: no se muestra nada
    private fun notTrue(field: String): RecordCriterion = RecordCriterion { definition, _ -> condition(columnOf(definition, field)) { "$it IS NOT TRUE" } }

    private fun textIn(
        field: String,
        values: Set<String>
    ): RecordCriterion = RecordCriterion { definition, bind -> condition(columnOf(definition, field)) { "$it = ANY(${bind(values.toTypedArray())})" } }

    private fun projectIn(projects: List<UUID>): RecordCriterion =
        RecordCriterion { definition, bind -> condition(columnOf(definition, "project")) { "$it = ANY(${bind(projects.toTypedArray())})" } }

    private fun columnOf(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    companion object {
        const val MAX_PROJECTS = 50
        private val LOCALITIES = setOf("CASERIO", "SECTOR", "CENTRO POBLADO", "ANEXO")

        // la condición de un filtro sobre una columna. sin columna (la definición que wasichai entrega no trae el campo
        // porque quien llama no puede leerlo) nada cumple: la capa sale vacía y no filtra por un valor que no se ve
        internal fun condition(
            column: String?,
            expression: (String) -> String
        ): String = column?.let(expression) ?: "FALSE"

        // minX,minY,maxX,maxY en EPSG:4326, tolerante con lo que manda un mapa alejado (P26): se quitan los espacios, las
        // longitudes se recortan a [-180, 180] y las latitudes a [-90, 90]. si lo recortado cubre todo el globo no hay nada que
        // filtrar y devuelve null. rechaza lo que no son cuatro números finitos y un mínimo mayor que el máximo después de
        // recortar (INVALID_BBOX)
        internal fun normalizeBbox(raw: String): String? {
            val parts = raw.split(",").map { it.trim().toDoubleOrNull() }
            if (parts.size != 4 || parts.any { it == null || !it.isFinite() }) throw invalidBbox()
            val (rawMinX, rawMinY, rawMaxX, rawMaxY) = parts.map { it!! }
            val minX = rawMinX.coerceIn(-180.0, 180.0)
            val maxX = rawMaxX.coerceIn(-180.0, 180.0)
            val minY = rawMinY.coerceIn(-90.0, 90.0)
            val maxY = rawMaxY.coerceIn(-90.0, 90.0)
            if (minX > maxX || minY > maxY) throw invalidBbox()
            if (maxX - minX >= 360.0 && maxY - minY >= 180.0) return null
            return "$minX,$minY,$maxX,$maxY"
        }

        private fun invalidBbox() =
            Finding(
                "INVALID_BBOX",
                "bbox debe ser minX,minY,maxX,maxY en EPSG:4326, con el mínimo no mayor que el máximo",
                "bbox",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()

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
