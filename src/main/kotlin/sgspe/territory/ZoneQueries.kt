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
import sgspe.common.LIMA
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageRequest
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los registros de una zona (casos 2, 3 y 5 de gis.md §5.3) y el punto pulsado (caso 16): patrón a de gis.md §5.2. la zona y
// el punto viajan enlazados en un RecordCriterion (ZoneCriterion) y la lista sale de Records, con permisos de objeto y de
// campo, alcance, paginación y el conteo de la API genérica. nada se escribe: locate solo propone, la persona confirma
@Service
class ZoneQueries(
    private val records: Records,
    private val zones: TerritoryZones,
    private val limits: TerritoryQueryProperties,
    private val metadata: MetadataService
) {
    suspend fun records(
        zoneObject: String,
        id: UUID,
        of: String?,
        period: Period?,
        page: PageRequest
    ): ZoneRecords {
        val target =
            of?.let { ZoneTargets.ALL[it] }
                ?: throw Finding(
                    "INVALID_OF",
                    "of debe ser uno de ${ZoneTargets.ALL.keys.sorted().joinToString(", ")}",
                    "of",
                    status = HttpStatus.BAD_REQUEST
                ).toCodedException()
        val zone = zones.read(zoneObject, id)
        val criteria = mutableListOf(ZoneCriterion.of(target.relations, target.geometries, zone))
        if (period != null) {
            val field =
                target.period
                    ?: throw Finding(
                        "PERIOD_NOT_APPLICABLE",
                        "$of no tiene una fecha por la que acotar el periodo",
                        "periodStart",
                        status = HttpStatus.BAD_REQUEST
                    ).toCodedException()
            criteria +=
                if (field.instant) {
                    Criteria.between(field.field, period.from(), period.until())
                } else {
                    Criteria.dateBetween(field.field, period.start, period.end)
                }
        }
        val result = records.page(of, RecordQuery(page = page, sort = "id", criteria = criteria))
        // el filtro no usa un campo que quien llama no lee: sin decirlo, la lista parecería completa
        val missing = Readability(metadata).missing(ZoneRules.required(of, target, zone, period != null))
        return ZoneRecords(result, missing)
    }

    // la unidad de nivel más fino, la localidad (por su límite) y la comunidad (por su territorio) que contienen el punto;
    // a igual nivel, la primera por nombre e id. lo que quien llama no puede leer sale null y su objeto en `unavailable`
    suspend fun locate(
        longitude: Double,
        latitude: Double
    ): Map<String, Any?> {
        val zone = Zone(emptyMap(), mapOf("type" to "Point", "coordinates" to listOf(longitude, latitude)))
        val unavailable = mutableListOf<String>()
        val readability = Readability(metadata)

        suspend fun containing(
            objectName: String,
            geometry: String
        ): List<RecordResponse> =
            try {
                if (!readability.canRead("$objectName.$geometry")) throw ForbiddenException("sin lectura de $objectName.$geometry")
                records.all(objectName, criteria = listOf(ZoneCriterion.of(emptyList(), listOf(geometry), zone)), max = limits.maxRows, field = "lon")
            } catch (_: ForbiddenException) {
                unavailable += objectName
                emptyList()
            }
        val byName = compareBy<RecordResponse>({ Values.text(it.attributes["name"]) }, { it.id })
        val byLevel = compareBy<RecordResponse> { LEVELS.indexOf(Values.text(it.attributes["level"])).let { i -> if (i < 0) LEVELS.size else i } }
        val unit = containing(TerritoryNames.UNIT, "boundary").sortedWith(byLevel.then(byName)).firstOrNull()
        val locality = containing(TerritoryNames.LOCALITY, "boundary").sortedWith(byName).firstOrNull()
        val community = containing(TerritoryNames.COMMUNITY, "territory").sortedWith(byName).firstOrNull()
        return linkedMapOf(
            "administrativeUnit" to
                unit?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "level" to Values.text(it.attributes["level"])) },
            "locality" to
                locality?.let {
                    mapOf(
                        "id" to it.id,
                        "name" to Values.text(it.attributes["name"]),
                        "localityType" to Values.text(it.attributes["locality_type"])
                    )
                },
            "community" to community?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"])) },
            "unavailable" to unavailable
        )
    }

    companion object {
        // de la unidad más fina a la más gruesa
        val LEVELS = listOf("DISTRITO", "PROVINCIA", "DEPARTAMENTO")
    }
}

// la página de wasichai más `available`: falso, con `missingFields`, si el filtro de la zona usa un campo que quien llama no puede
// leer (la lista trae lo legible, nunca se presenta como completa)
data class ZoneRecords(
    val content: List<RecordResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long?,
    val totalPages: Int?,
    val available: Boolean,
    val missingFields: List<String>? = null
) {
    constructor(result: PageResponse<RecordResponse>, missing: List<String>) :
        this(result.content, result.page, result.size, result.totalElements, result.totalPages, missing.isEmpty(), missing.ifEmpty { null })
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory")
class ZoneQueriesController(
    private val queries: ZoneQueries,
    private val limits: TerritoryQueryProperties,
    private val clock: Clock
) {
    @GetMapping("/zones/{zoneObject}/{id}/records")
    suspend fun records(
        @PathVariable zoneObject: String,
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): ZoneRecords {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("of", "periodStart", "periodEnd", "page", "size"))
        val start = params.getFirst("periodStart")
        val end = params.getFirst("periodEnd")
        val period = if (start == null && end == null) null else QueryParams.period(start, end, LocalDate.now(clock.withZone(LIMA)), limits)
        return queries.records(zoneObject, id, params.getFirst("of"), period, QueryParams.page(params.getFirst("page"), params.getFirst("size")))
    }

    @GetMapping("/locate")
    suspend fun locate(request: ServerHttpRequest): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("lon", "lat"))
        return queries.locate(
            QueryParams.coordinate("lon", params.getFirst("lon"), -180.0, 180.0),
            QueryParams.coordinate("lat", params.getFirst("lat"), -90.0, 90.0)
        )
    }
}
