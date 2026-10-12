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
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.util.UUID

// la parte territorial de la ficha contextual (api.md §4, dashboards.md §5.9): sgspe.territory solo aporta la identificación,
// la geometría y las relaciones territoriales del lugar elegido, leídas por Records como quien llama; las cifras son
// KPI-TER-01. las comunidades de una localidad son las que contienen su límite o su punto (patrón a, la geometría enlazada);
// las áreas de influencia, las versiones aprobadas que la tienen como miembro (con `project`, solo las de ese proyecto). lo
// que quien llama no puede leer sale vacío y su objeto en `unavailable`
@Service
class TerritoryCards(
    private val records: Records,
    private val metadata: MetadataService,
    private val limits: TerritoryQueryProperties
) {
    suspend fun locality(
        id: UUID,
        project: UUID?
    ): Map<String, Any?> {
        val locality = records.find(TerritoryNames.LOCALITY, id) ?: throw notFound("LOCALITY_NOT_FOUND", "La localidad")
        val geometries = Records.geometriesOf(locality)
        val unavailable = mutableListOf<String>()
        val readability = Readability(metadata)
        val missing = readability.missing(LOCALITY_PATHS + UNIT_PATHS + COMMUNITY_TERRITORY).toMutableList()
        val shape = geometries["boundary"] ?: geometries["location"]
        val computable =
            CardReadability.communitiesComputable(
                boundary = readability.canRead("locality.boundary"),
                location = readability.canRead("locality.location"),
                territory = readability.canRead("community.territory")
            )
        if (!computable) missing += TerritoryNames.COMMUNITY
        val communities =
            guarded(TerritoryNames.COMMUNITY, unavailable) {
                if (shape == null || !computable) {
                    emptyList()
                } else {
                    val inside = ZoneCriterion.of(emptyList(), listOf("territory"), Zone(emptyMap(), shape), excludeTouching = true)
                    records.all(TerritoryNames.COMMUNITY, criteria = listOf(inside), max = limits.maxRows).sortedWith(BY_NAME).map(::brief)
                }
            }
        val unit = unitOf(locality, unavailable)
        val areas = areas(TerritoryNames.LOCALITY, id, project, unavailable, readability, missing, "locality")
        return linkedMapOf(
            "locality" to locality,
            "geometry" to mapOf("location" to geometries["location"], "boundary" to geometries["boundary"]),
            "administrativeUnit" to unit,
            "communities" to communities,
            "areasOfInfluence" to areas,
            "unavailable" to CardReadability.collapse(missing + unavailable)
        )
    }

    suspend fun community(
        id: UUID,
        project: UUID?
    ): Map<String, Any?> {
        val community = records.find(TerritoryNames.COMMUNITY, id) ?: throw notFound("COMMUNITY_NOT_FOUND", "La comunidad")
        val unavailable = mutableListOf<String>()
        val readability = Readability(metadata)
        val missing = readability.missing(listOf("community.territory", "community.administrative_unit") + UNIT_PATHS).toMutableList()
        val sectors =
            guarded(TerritoryNames.SECTOR, unavailable) {
                records
                    .all(TerritoryNames.SECTOR, criteria = listOf(Criteria.anyOf(TerritoryNames.COMMUNITY, listOf(id))), max = limits.maxRows)
                    .sortedWith(BY_NAME)
                    .map(::brief)
            }
        val unit = unitOf(community, unavailable)
        val areas = areas(TerritoryNames.COMMUNITY, id, project, unavailable, readability, missing, "community")
        return linkedMapOf(
            "community" to community,
            "geometry" to mapOf("territory" to Records.geometriesOf(community)["territory"]),
            "administrativeUnit" to unit,
            "sectors" to sectors,
            "areasOfInfluence" to areas,
            "unavailable" to CardReadability.collapse(missing + unavailable)
        )
    }

    private suspend fun unitOf(
        record: RecordResponse,
        unavailable: MutableList<String>
    ): Map<String, Any?>? {
        val id = Records.uuid(record.attributes[TerritoryNames.UNIT]) ?: return null
        return guarded(TerritoryNames.UNIT, unavailable) { listOfNotNull(records.find(TerritoryNames.UNIT, id)) }
            .firstOrNull()
            ?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "level" to Values.text(it.attributes["level"])) }
    }

    // las versiones aprobadas de las áreas que tienen el lugar como miembro, con la condición del miembro
    private suspend fun areas(
        field: String,
        id: UUID,
        project: UUID?,
        unavailable: MutableList<String>,
        readability: Readability,
        missing: MutableList<String>,
        memberField: String
    ): List<Map<String, Any?>> {
        missing +=
            readability.missing(
                listOf(
                    "area_of_influence_member.$memberField",
                    "area_of_influence_member.area_of_influence",
                    "area_of_influence_member.member_condition",
                    "area_of_influence.project",
                    "area_of_influence.area_type",
                    "area_of_influence.version_number"
                )
            )
        val members =
            guarded(TerritoryNames.MEMBER, unavailable) {
                records.all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(field, listOf(id))), max = limits.maxRows)
            }
        val conditions =
            members
                .mapNotNull { m ->
                    Records.uuid(m.attributes[TerritoryNames.AREA])?.let { it to Values.text(m.attributes["member_condition"]) }
                }.toMap()
        if (conditions.isEmpty()) return emptyList()
        val criteria = listOfNotNull(Criteria.inState(setOf(APPROVED)), project?.let { Criteria.anyOf("project", listOf(it)) })
        val areas =
            guarded(TerritoryNames.AREA, unavailable) {
                records.all(TerritoryNames.AREA, criteria = criteria, ids = conditions.keys.toList(), max = limits.maxRows)
            }
        return areas
            .sortedWith(compareBy({ Values.text(it.attributes["area_type"]) }, { it.id }))
            .map { area ->
                linkedMapOf(
                    "id" to area.id,
                    "project" to Values.text(area.attributes["project"]),
                    "areaType" to Values.text(area.attributes["area_type"]),
                    "versionNumber" to Values.decimal(area.attributes["version_number"])?.toInt(),
                    "memberCondition" to conditions[Records.uuid(area.id)]
                )
            }
    }

    private suspend fun <T> guarded(
        objectName: String,
        unavailable: MutableList<String>,
        block: suspend () -> List<T>
    ): List<T> =
        try {
            block()
        } catch (_: ForbiddenException) {
            unavailable += objectName
            emptyList()
        }

    private fun brief(record: RecordResponse): Map<String, Any?> = mapOf("id" to record.id, "name" to Values.text(record.attributes["name"]))

    private fun notFound(
        code: String,
        what: String
    ) = Finding(code, "$what no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()

    companion object {
        private const val APPROVED = "approved"
        private val LOCALITY_PATHS = listOf("locality.location", "locality.boundary", "locality.administrative_unit")
        private val UNIT_PATHS = listOf("administrative_unit.name", "administrative_unit.level")
        private val COMMUNITY_TERRITORY = listOf("community.territory")
        private val BY_NAME = compareBy<RecordResponse>({ Values.text(it.attributes["name"]) }, { it.id })
    }
}

// qué se lista en `unavailable` (puro, probado aparte): un objeto ilegible se nombra solo, sin sus campos
object CardReadability {
    fun collapse(paths: List<String>): List<String> {
        val objects = paths.filterNot { '.' in it }.toSet()
        return paths.filter { '.' !in it || it.substringBefore('.') !in objects }.distinct()
    }

    // las comunidades que contienen a la localidad se calculan con su límite o su punto contra el territorio de la comunidad
    fun communitiesComputable(
        boundary: Boolean,
        location: Boolean,
        territory: Boolean
    ): Boolean = territory && (boundary || location)
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory")
class TerritoryCardsController(
    private val cards: TerritoryCards
) {
    @GetMapping("/localities/{id}/card")
    suspend fun locality(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("project"))
        return cards.locality(id, project(request))
    }

    @GetMapping("/communities/{id}/card")
    suspend fun community(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("project"))
        return cards.community(id, project(request))
    }

    private fun project(request: ServerHttpRequest): UUID? {
        val given = QueryParams.uuids("project", request.queryParams["project"])
        if (given.size > 1) throw QueryParams.invalid("project", "no puede repetirse")
        return given.firstOrNull()
    }
}
