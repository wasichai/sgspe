package sgspe.dashboard

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.metadata.MetadataService
import java.util.UUID

// los conteos de las pestañas de un proyecto en una sola lectura (B-03 del rediseño, wasichai/sgspe#161, api.md §4.11): por
// cada clave, la suma de los totalElements que la API genérica le daría a quien llama con ?project=<id> y size=1 (Records.count
// lee como la persona, así que aplican el alcance de lectura de ADR-0029 y ConfidentialAttentionScope). las claves son las de
// useConteosDelProyecto del portal: <pestaña>:total y, donde hay algo que atender, <pestaña>:accion, los vencidos
// (deadline_status VENCIDO). una parte cuyo objeto quien llama no lee no se pide, como en el portal; una clave sin ninguna
// parte legible, o con un campo de su filtro que no lee, es null, nunca 0. project es la relación con el proyecto, que el
// filtro de la API genérica admite siempre
data class ProjectCountsResponse(
    @get:JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS) val counts: Map<String, Long?>,
    val confidentialHidden: Boolean
)

object ProjectCounts {
    // una lectura genérica: el objeto y sus filtros además del proyecto
    class Source(
        val objectName: String,
        val filters: Map<String, String> = emptyMap()
    )

    private const val PROJECT = "project"
    private val OVERDUE = mapOf("deadline_status" to "VENCIDO")
    private val ATTENTION = listOf("grievance", "inquiry", "stakeholder_request")

    val KEYS: Map<String, List<Source>> =
        linkedMapOf(
            "stakeholders:total" to listOf(Source("stakeholder")),
            "engagements:total" to listOf(Source("engagement")),
            "commitments:total" to listOf(Source("commitment")),
            "commitments:accion" to listOf(Source("commitment", OVERDUE)),
            "attention:total" to ATTENTION.map { Source(it) },
            "attention:accion" to ATTENTION.map { Source(it, OVERDUE) },
            "risks:total" to listOf(Source("social_risk")),
            "programs:total" to listOf(Source("social_program")),
            "documents:total" to listOf(Source("evidence"))
        )

    // `canRead` recibe un objeto o un objeto.campo (Readability.canRead); `count`, el objeto y los filtros de la lectura
    suspend fun compute(
        project: UUID,
        canRead: suspend (String) -> Boolean,
        count: suspend (String, Map<String, String>) -> Long
    ): Map<String, Long?> =
        KEYS.mapValues { (_, sources) ->
            val readable = sources.filter { canRead(it.objectName) }
            when {
                readable.isEmpty() -> null
                readable.any { s -> s.filters.keys.any { !canRead("${s.objectName}.$it") } } -> null
                else -> readable.sumOf { count(it.objectName, mapOf(PROJECT to project.toString()) + it.filters) }
            }
        }
}

@Service
class ProjectCountsService(
    private val records: Records,
    private val metadata: MetadataService,
    private val callers: Callers,
    private val permissions: Permissions
) {
    suspend fun counts(project: UUID): ProjectCountsResponse {
        val user = callers.require()
        // como la API genérica: no existe, es de otro tenant o no es de sus proyectos, el mismo 404
        val found =
            try {
                records.find(PROJECT, project)
            } catch (_: ForbiddenException) {
                null
            }
        found ?: throw Finding("PROJECT_NOT_FOUND", "El proyecto no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()
        val readable = Readability(metadata)
        val counts = ProjectCounts.compute(project, { readable.canRead(it) }) { objectName, filters -> records.count(objectName, filters) }
        return ProjectCountsResponse(counts, permissions.confidentialHidden(user))
    }

    private companion object {
        const val PROJECT = "project"
    }
}

@RestController
class ProjectCountsController(
    private val service: ProjectCountsService
) {
    @GetMapping("/api/sgspe/projects/{id}/counts")
    suspend fun counts(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): ProjectCountsResponse {
        StrictQuery.requireOnly(request.queryParams, emptySet())
        return service.counts(id)
    }
}
