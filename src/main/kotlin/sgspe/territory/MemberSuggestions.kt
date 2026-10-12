package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageRequest
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.util.UUID

// las geometrías de referencia de un proyecto, leídas por Records como quien llama: las versiones vigentes de sus
// componentes (la huella, el trazo o el punto) y su área de interés, juntas en una GeometryCollection GeoJSON 4326 que se
// enlaza como parámetro (especificación de F6 §2.2 elección 4). null si no hay ninguna
@Component
class ReferenceGeometries(
    private val records: Records,
    private val limits: TerritoryQueryProperties
) {
    suspend fun components(project: UUID): Map<String, Any?>? = collection(componentShapes(project))

    suspend fun projectAndComponents(project: RecordResponse): Map<String, Any?>? =
        collection(listOfNotNull(Records.geometriesOf(project)["interest_area"]) + componentShapes(UUID.fromString(project.id)))

    private suspend fun componentShapes(project: UUID): List<Map<String, Any?>> {
        val shapes = mutableListOf<Map<String, Any?>>()
        val criteria = listOf(Criteria.anyOf("project", listOf(project)), ZoneCriterion.notTrue("superseded"))
        var page = 0
        do {
            val (_, rows) =
                records.rows(
                    TerritoryNames.COMPONENT,
                    RecordQuery(page = PageRequest.of(page, PageRequest.MAX_SIZE), sort = "id", criteria = criteria, count = false)
                )
            for (row in rows) {
                @Suppress("UNCHECKED_CAST")
                val geometries = row.sections[Records.GEOMETRIES] as Map<String, Map<String, Any?>?>?
                TerritoryNames.COMPONENT_GEOMETRIES.firstNotNullOfOrNull { geometries?.get(it) }?.let(shapes::add)
            }
            if (shapes.size > limits.maxRows) throw Records.tooMany(TerritoryNames.COMPONENT, limits.maxRows, "id")
            page++
        } while (rows.size >= PageRequest.MAX_SIZE)
        return shapes
    }

    private fun collection(shapes: List<Map<String, Any?>?>): Map<String, Any?>? = Companion.collection(shapes.filterNotNull())

    companion object {
        // las formas con tipo y coordenadas no vacías (ZoneRules.usable) en una GeometryCollection; null si no queda ninguna
        fun collection(shapes: List<Map<String, Any?>>): Map<String, Any?>? {
            val usable = shapes.mapNotNull { ZoneRules.usable(it) }
            return if (usable.isEmpty()) null else mapOf("type" to "GeometryCollection", "geometries" to usable)
        }
    }
}

// las sugerencias de miembros de un área de influencia (casos 1 y 9 de gis.md §5.3, RN-TER-004) y las localidades en un
// buffer de los componentes (caso 14): patrón a de gis.md §5.2, por Records con la referencia enlazada. nada se escribe.
// la referencia de un área es su polígono; sin él (es opcional, CON-025), el área de interés y los componentes de su
// proyecto. INTERSECTS: el candidato cruza la referencia; WITHIN_BUFFER: no la cruza pero está a menos de bufferM, medido en
// el working_crs. un candidato que quien llama no puede leer no se sugiere y su objeto va en missingFields
@Service
class MemberSuggestions(
    private val records: Records,
    private val references: ReferenceGeometries,
    private val frames: ProjectFrames,
    private val limits: TerritoryQueryProperties,
    private val metadata: MetadataService
) {
    private class Candidate(
        val objectName: String,
        val geometries: List<String>,
        val filters: Map<String, String> = emptyMap()
    )

    suspend fun members(
        areaId: UUID,
        bufferM: Int?
    ): Map<String, Any?> {
        val area =
            records.find(TerritoryNames.AREA, areaId)
                ?: throw Finding("AREA_NOT_FOUND", "El área de influencia no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND)
                    .toCodedException()
        val projectId = Records.uuid(area.attributes["project"])
        val srid =
            if (bufferM == null) {
                null
            } else {
                frames
                    .of(
                        projectId
                            ?: throw Finding(
                                "PROJECT_NOT_FOUND",
                                "El área no tiene proyecto legible",
                                "project",
                                status = HttpStatus.NOT_FOUND
                            ).toCodedException()
                    ).srid
            }
        val parts = SpatialParts<Map<String, Any?>>()
        val readability = Readability(metadata)
        // la referencia: el polígono del área; sin él, el área de interés y los componentes. un campo que quien llama no lee no
        // se reemplaza en silencio por el siguiente: la lista sale no disponible
        val boundary = ZoneRules.usable(Records.geometriesOf(area)["boundary"])
        val reference =
            if (boundary != null) {
                boundary
            } else if (!readability.canRead("${TerritoryNames.AREA}.boundary")) {
                parts.missing("${TerritoryNames.AREA}.boundary")
                return parts.response("suggestions", emptyList())
            } else {
                val project = projectId?.let { records.find(TerritoryNames.PROJECT, it) }
                parts.missing(*readability.missing(listOf("${TerritoryNames.PROJECT}.interest_area") + componentFields()).toTypedArray())
                // sin proyecto legible no hay con qué cruzar: no se presenta una lista vacía como completa
                if (project == null) parts.missing("${TerritoryNames.AREA}.project")
                project?.let { references.projectAndComponents(it) }
            }
        if (reference == null) return parts.response("suggestions", emptyList())
        val already = already(areaId, parts, readability)
        val rows = mutableListOf<Map<String, Any?>>()
        for (candidate in CANDIDATES) {
            val name = candidate.objectName
            val needed = (candidate.geometries + "name" + candidate.filters.keys).map { "$name.$it" }
            val absent = readability.missing(needed)
            parts.missing(*absent.toTypedArray())
            // sin ninguna geometría legible, el filtro no tendría con qué cruzar
            if (candidate.geometries.all { "$name.$it" in absent }) continue
            try {
                val crossing =
                    records.all(
                        name,
                        candidate.filters,
                        listOf(ZoneCriterion.of(emptyList(), candidate.geometries, Zone(emptyMap(), reference))),
                        max = limits.maxRows,
                        field = "bufferM"
                    )
                val near =
                    if (bufferM == null || srid == null) {
                        emptyList()
                    } else {
                        records.all(
                            name,
                            candidate.filters,
                            listOf(ZoneCriterion.within(candidate.geometries, reference, bufferM, srid)),
                            max = limits.maxRows,
                            field = "bufferM"
                        )
                    }
                val crossingIds = crossing.map { it.id }.toSet()
                rows += crossing.map { suggestion(candidate, it, INTERSECTS, already) }
                rows += near.filter { it.id !in crossingIds }.map { suggestion(candidate, it, WITHIN_BUFFER, already) }
            } catch (_: ForbiddenException) {
                parts.missing(name)
            }
        }
        return parts.response("suggestions", rows)
    }

    private fun componentFields(): List<String> = TerritoryNames.COMPONENT_GEOMETRIES.map { "${TerritoryNames.COMPONENT}.$it" }

    suspend fun bufferLocalities(
        projectId: UUID,
        bufferM: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val readability = Readability(metadata)
        val parts = SpatialParts<Map<String, Any?>>()
        parts.missing(
            *readability.missing(componentFields() + listOf("${TerritoryNames.LOCALITY}.location", "${TerritoryNames.LOCALITY}.boundary")).toTypedArray()
        )
        val reference = references.components(projectId) ?: return parts.response("localities", emptyList())
        val found =
            records.all(
                TerritoryNames.LOCALITY,
                criteria = listOf(ZoneCriterion.within(listOf("location", "boundary"), reference, bufferM, frame.srid)),
                max = limits.maxRows,
                field = "bufferM"
            )
        return parts.response(
            "localities",
            found
                .sortedWith(compareBy({ Values.text(it.attributes["name"]) }, { it.id }))
                .map { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "localityType" to Values.text(it.attributes["locality_type"])) }
        )
    }

    // los miembros que el área ya tiene, como "<objeto>:<id>"; null si quien llama no puede leer los miembros
    private suspend fun already(
        areaId: UUID,
        parts: SpatialParts<*>,
        readability: Readability
    ): Set<String>? {
        // un miembro cuyo campo no se lee no se vería: sin todos, no se sabe quién ya es miembro
        val absent = readability.missing(MEMBER_FIELDS.map { "${TerritoryNames.MEMBER}.$it" } + "${TerritoryNames.MEMBER}.${TerritoryNames.AREA}")
        if (absent.isNotEmpty()) {
            parts.missing(*absent.toTypedArray())
            return null
        }
        return try {
            records
                .all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(TerritoryNames.AREA, listOf(areaId))), max = limits.maxRows)
                .flatMap { member -> MEMBER_FIELDS.mapNotNull { field -> Values.text(member.attributes[field])?.let { "$field:$it" } } }
                .toSet()
        } catch (_: ForbiddenException) {
            parts.missing(TerritoryNames.MEMBER)
            null
        }
    }

    private fun suggestion(
        candidate: Candidate,
        record: RecordResponse,
        relation: String,
        already: Set<String>?
    ): Map<String, Any?> =
        linkedMapOf(
            "object" to candidate.objectName,
            "recordId" to record.id,
            "name" to Values.text(record.attributes["name"]),
            "relation" to relation,
            "alreadyMember" to already?.contains("${candidate.objectName}:${record.id}")
        )

    companion object {
        const val INTERSECTS = "INTERSECTS"
        const val WITHIN_BUFFER = "WITHIN_BUFFER"
        private val MEMBER_FIELDS = listOf(TerritoryNames.LOCALITY, TerritoryNames.COMMUNITY, TerritoryNames.UNIT)

        // los distritos, no las provincias ni los departamentos, que contendrían cualquier área
        private val CANDIDATES =
            listOf(
                Candidate(TerritoryNames.LOCALITY, listOf("location", "boundary")),
                Candidate(TerritoryNames.COMMUNITY, listOf("territory")),
                Candidate(TerritoryNames.UNIT, listOf("boundary"), mapOf("level" to "DISTRITO"))
            )
    }
}

// api.md §4 (F6)
@RestController
class MemberSuggestionsController(
    private val suggestions: MemberSuggestions,
    private val limits: TerritoryQueryProperties
) {
    @GetMapping("/api/sgspe/areas-of-influence/{id}/member-suggestions")
    suspend fun members(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("bufferM"))
        return suggestions.members(id, QueryParams.bufferM(request.queryParams.getFirst("bufferM"), limits, default = null))
    }

    @GetMapping("/api/sgspe/territory/projects/{id}/buffer-localities")
    suspend fun bufferLocalities(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("bufferM"))
        return suggestions.bufferLocalities(id, QueryParams.bufferM(request.queryParams.getFirst("bufferM"), limits)!!)
    }
}
