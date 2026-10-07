package sgspe.common

import kotlinx.coroutines.withContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import wasichai.core.common.PageRequest
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordRow
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.data.toResponse
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.workflow.WorkflowService
import java.util.UUID

// el RecordService de wasichai para el código de sgspe (como Registros de caja-backend@dc9e391,
// src/main/kotlin/caja/comun/Registros.kt). RecordService comprueba los permisos de objeto y de campo de quien llama y
// valida cada escritura, así que aquí no se repite. cada escritura, vínculo y transición lleva la marca SgspeWrite, para
// que las guardas distingan el código propio de la API genérica, y su motivo, que wasichai deja en audit_log.reason
// (ADR-0011). es el único lugar fuera de las pruebas que toca RecordService (ArchitectureTest)
//
// RecordService, RelatedRecordService y WorkflowService llegan por ObjectProvider: las guardas y los listeners de sgspe
// usan Records, y wasichai los inyecta en esos mismos servicios (RecordWriteGuards, RecordChangeListener), así que una
// inyección directa sería un ciclo de beans
@Component
class Records(
    recordService: ObjectProvider<RecordService>,
    relatedService: ObjectProvider<RelatedRecordService>,
    workflowService: ObjectProvider<WorkflowService>,
    private val metadata: MetadataService,
    private val callers: Callers
) {
    private val records by lazy { recordService.getObject() }
    private val related by lazy { relatedService.getObject() }
    private val workflows by lazy { workflowService.getObject() }

    suspend fun get(
        objectName: String,
        id: UUID
    ): RecordResponse = records.get(objectName, id)

    // null si no existe o el que llama no lo ve (404 de wasichai)
    suspend fun find(
        objectName: String,
        id: UUID
    ): RecordResponse? =
        try {
            records.get(objectName, id)
        } catch (_: NotFoundException) {
            null
        }

    // una página con su conteo, para la lista de un endpoint propio (gancho de F3: en F2 los endpoints leen con all)
    suspend fun page(
        objectName: String,
        query: RecordQuery
    ): PageResponse<RecordResponse> = records.list(objectName, query)

    // todos los que cumplen los filtros, por cursor y ordenados por id: cada fila sale una sola vez aunque otras columnas
    // empaten (la lección de Registros.all de caja), y sin COUNT(*) en cada página (WAS-ADR-0036)
    suspend fun all(
        objectName: String,
        filters: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null
    ): List<RecordResponse> {
        if (ids != null && ids.isEmpty()) return emptyList()
        val rows = mutableListOf<RecordResponse>()
        var cursor: String? = null
        do {
            val page =
                records.list(
                    objectName,
                    RecordQuery(
                        page = PageRequest.of(0, PageRequest.MAX_SIZE),
                        sort = "id",
                        filters = filters,
                        criteria = criteria,
                        ids = ids,
                        count = false,
                        after = cursor
                    )
                )
            rows += page.content
            cursor = page.nextCursor
        } while (cursor != null)
        return rows
    }

    // las filas con sus secciones (las geometrías) y la definición que las leyó, para quien arma GeoJSON
    suspend fun rows(
        objectName: String,
        query: RecordQuery
    ): Pair<ObjectDefinition, List<RecordRow>> = records.rows(objectName, query)

    suspend fun count(
        objectName: String,
        filters: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null
    ): Long {
        if (ids != null && ids.isEmpty()) return 0
        return records
            .list(objectName, RecordQuery(page = PageRequest.of(0, 1), filters = filters, criteria = criteria, ids = ids))
            .totalElements ?: 0
    }

    suspend fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = create(objectName, attributes, reason, emptyMap())

    // geometries: GeoJSON EPSG:4326 por campo, la sección "geometries" de wasichai-gis
    suspend fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String?,
        geometries: Map<String, Any?>
    ): RecordResponse = withContext(SgspeWrite) { records.create(objectName, request(attributes, geometries), reason) }

    // el update de wasichai tal cual: reemplaza cada campo escribible, y uno que falta queda vacío. el código de sgspe usa
    // patch, que no borra lo que no nombra
    suspend fun update(
        objectName: String,
        id: UUID,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { records.update(objectName, id, RecordRequest(attributes), reason) }

    // cambia solo `changes` (y las geometrías nombradas): lee el registro y manda lo guardado más los cambios. una persona
    // manda solo los campos que puede escribir, según el objeto como ella lo ve (MetadataService.definitionOf), porque
    // wasichai rechaza la escritura entera que nombra uno bloqueado y deja como estaban los que no se mandan; la
    // plataforma (un trabajo dentro de asPlatform) los escribe todos
    suspend fun patch(
        objectName: String,
        id: UUID,
        changes: Map<String, Any?>,
        reason: String?,
        geometries: Map<String, Any?> = emptyMap()
    ): RecordResponse {
        val stored = records.get(objectName, id).attributes
        val merged = stored + changes
        val body =
            if (callers.userOrNull() == null) {
                merged
            } else {
                val writable =
                    metadata
                        .definitionOf(objectName)
                        .fields
                        .filter { it.editable }
                        .map { it.name }
                        .toSet()
                merged.filterKeys { it in writable }
            }
        return withContext(SgspeWrite) { records.update(objectName, id, request(body, geometries), reason) }
    }

    suspend fun delete(
        objectName: String,
        id: UUID,
        reason: String?
    ) = withContext(SgspeWrite) { records.delete(objectName, id, reason) }

    suspend fun link(
        objectName: String,
        id: UUID,
        relationship: String,
        otherId: UUID,
        reason: String?
    ) = withContext(SgspeWrite) { related.link(objectName, id, relationship, otherId, reason) }

    // los registros del otro lado de una relación, como los ve quien llama
    suspend fun related(
        objectName: String,
        id: UUID,
        relationship: String
    ): List<RecordResponse> =
        relatedPages { query ->
            related
                .relatedRecords(objectName, id, relationship, query)
                .second.content
                .map { it.toResponse() }
        }

    // lo mismo para un trabajo de fondo, sin usuario: la plataforma no tiene permisos que aplicar (WAS-ADR-0039)
    suspend fun relatedAsPlatform(
        organizationId: UUID,
        objectName: String,
        id: UUID,
        relationship: String
    ): List<RecordRow> =
        relatedPages { query ->
            related.relatedRows(organizationId, objectName, id, relationship, query).second.content
        }

    // recorre las páginas de una relación (sin cursor: la relación se pagina por número) hasta una incompleta
    private suspend fun <T> relatedPages(fetch: suspend (RecordQuery) -> List<T>): List<T> {
        val rows = mutableListOf<T>()
        var page = 0
        do {
            val content = fetch(RecordQuery(page = PageRequest.of(page, PageRequest.MAX_SIZE), sort = "id"))
            rows += content
            page++
        } while (content.size >= PageRequest.MAX_SIZE)
        return rows
    }

    // una transición como la persona que llama (WorkflowService exige un usuario), con la marca: así
    // CommandTransitionGuard deja pasar las transiciones de comando que aplica un endpoint de sgspe
    suspend fun transition(
        objectName: String,
        id: UUID,
        transition: String,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { workflows.apply(objectName, id, transition, reason) }

    // el trabajo de fondo de una organización (WAS-ADR-0039): sin usuario, sin permisos, nunca dentro de una petición
    suspend fun <T> asPlatform(
        organizationId: UUID,
        block: suspend () -> T
    ): T = records.asPlatform(organizationId, block)

    private fun request(
        attributes: Map<String, Any?>,
        geometries: Map<String, Any?>
    ): RecordRequest = RecordRequest(attributes).also { if (geometries.isNotEmpty()) it.section(GEOMETRIES, geometries) }

    companion object {
        // la sección en que wasichai-gis lleva las geometrías de un registro (wasichai.gis.GEOMETRIES)
        const val GEOMETRIES = "geometries"

        // las geometrías de una respuesta, por campo
        @Suppress("UNCHECKED_CAST")
        fun geometriesOf(response: RecordResponse): Map<String, Map<String, Any?>?> =
            (response.sections[GEOMETRIES] as Map<String, Map<String, Any?>?>?).orEmpty()

        fun uuid(value: Any?): UUID? = Values.text(value)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
