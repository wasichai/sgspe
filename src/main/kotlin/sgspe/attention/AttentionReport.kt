package sgspe.attention

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.AttentionTotals
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Raised
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

// los filtros del reporte unificado (api.md §4.6), ya validados; projects vacío: «mis proyectos» por resolver
data class AttentionReportFilters(
    val projects: List<UUID>,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val types: List<String>,
    val channel: String?,
    val category: String?,
    val page: Int,
    val size: Int
)

// el reporte unificado de QCS, puro (spec de F4 §3.2, api.md §4.6, RF-GRV-005): los conteos con AttentionCounting (cada
// planteamiento una vez, dashboards.md §3.3) y la página de filas sin texto libre ni persona. un tipo que quien llama no lee
// llega como null y sale `available: false`, sin contar
object AttentionReportRows {
    const val PARAM_PROJECT = "project"
    const val PARAM_START = "periodStart"
    const val PARAM_END = "periodEnd"
    const val PARAM_TYPE = "type"
    const val PARAM_CHANNEL = "channel"
    const val PARAM_CATEGORY = "category"
    const val PARAM_PAGE = "page"
    const val PARAM_SIZE = "size"
    val PARAMS: Set<String> = setOf(PARAM_PROJECT, PARAM_START, PARAM_END, PARAM_TYPE, PARAM_CHANNEL, PARAM_CATEGORY, PARAM_PAGE, PARAM_SIZE)

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200

    // la fecha que mide una QCS en su estado (workflows.md «Plazos y escalamiento»): el acuse de una queja en `received` y su
    // resolución en los demás estados con plazo; la respuesta de una consulta en `received`; null fuera de ellos y en las
    // solicitudes, que no tienen plazo en el MVP (ASM-100)
    val GRIEVANCE_TRACKED: Set<String> = setOf("received", "acknowledged", "in_assessment", "in_investigation", "in_action")

    fun dueDate(
        type: String,
        state: String?,
        attributes: Map<String, Any?>
    ): String? =
        when {
            type == Attention.GRIEVANCE && state == "received" -> Values.text(attributes[QcsDueDates.ACK])
            type == Attention.GRIEVANCE && state in GRIEVANCE_TRACKED -> Values.text(attributes[QcsDueDates.RESOLUTION])
            type == Attention.INQUIRY && state == "received" -> Values.text(attributes[QcsDueDates.RESPONSE])
            else -> null
        }

    // una fila de la página: solo estos campos, nunca descripción, respuesta, resolución ni persona
    fun item(row: Raised): Map<String, Any?> {
        val a = row.attributes
        return linkedMapOf(
            "type" to row.type,
            "id" to row.id,
            "code" to Values.text(a["code"]),
            "state" to row.state,
            "channel" to Values.text(a["channel"]),
            "category" to Values.text(a["category"]),
            "severity" to if (row.type == Attention.GRIEVANCE) Values.text(a[Attention.SEVERITY]) else null,
            "receivedAt" to Values.text(a[Attention.RECEIVED_AT]),
            "deadlineStatus" to Values.text(a[Attention.DEADLINE_STATUS]),
            "dueDate" to dueDate(row.type, row.state, a),
            "ownerEmail" to Values.text(a["owner_user_email"]),
            "respondedAt" to Values.text(a[Attention.RESPONDED_AT]),
            "anonymous" to (Values.bool(a["anonymous"]) == true)
        )
    }

    fun totals(totals: AttentionTotals): Map<String, Any?> =
        linkedMapOf(
            "received" to totals.received,
            "open" to totals.open,
            "closed" to totals.closed,
            "rejected" to totals.rejected,
            "withdrawn" to totals.withdrawn,
            "absolved" to totals.absolved,
            "overdue" to totals.overdue
        )

    // filas { key, value } de más a menos y, a igual cuenta, por clave; un valor vacío cuenta como «sin dato»
    fun counts(keys: List<String?>): List<Map<String, Any?>> =
        keys
            .map { it ?: NO_VALUE }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }

    const val NO_VALUE = "sin dato"

    // el reporte: `read` trae, por tipo pedido, sus filas como las lee quien llama o null si no lo lee
    fun report(
        read: Map<String, List<Raised>?>,
        confidentialHidden: Boolean,
        page: Int,
        size: Int
    ): Map<String, Any?> {
        val counted = AttentionCounting.counted(read.values.filterNotNull().flatten())
        val byType =
            read.map { (type, rows) ->
                if (rows == null) {
                    linkedMapOf("type" to type, "available" to false)
                } else {
                    val mine = counted.filter { it.type == type }
                    linkedMapOf<String, Any?>("type" to type, "available" to true) + totals(AttentionCounting.totals(mine)) +
                        mapOf(
                            "byState" to counts(mine.map { it.state }),
                            "byChannel" to counts(mine.map { Values.text(it.attributes["channel"]) }),
                            "byCategory" to counts(mine.map { Values.text(it.attributes["category"]) })
                        )
                }
            }
        val sorted =
            counted.sortedWith(
                compareByDescending<Raised> { Values.text(it.attributes[Attention.RECEIVED_AT]).orEmpty() }.thenBy { it.id }
            )
        // el desplazamiento en Long: page * size desborda Int con una página enorme; fuera de rango, la página sale vacía
        val offset = minOf(page.toLong() * size, sorted.size.toLong()).toInt()
        val items = sorted.drop(offset).take(size).map { item(it) }
        return linkedMapOf(
            "totals" to totals(AttentionCounting.totals(counted)),
            "byType" to byType,
            "confidentialHidden" to confidentialHidden,
            "page" to linkedMapOf("items" to items, "page" to page, "size" to size, "totalElements" to counted.size)
        )
    }

    // los parámetros validados: un valor inválido es 400 INVALID_FILTER sobre el parámetro; uno desconocido, 422
    fun parse(
        params: Map<String, List<String>>,
        catalog: ModelCatalog
    ): AttentionReportFilters {
        StrictQuery.requireOnly(params, PARAMS)

        fun invalid(
            param: String,
            detail: String
        ): Nothing = throw Finding("INVALID_FILTER", "Filtro inválido: $param $detail", param, status = HttpStatus.BAD_REQUEST).toCodedException()

        fun single(param: String): String? {
            val values = params[param].orEmpty()
            if (values.size > 1) invalid(param, "va una sola vez")
            return values.firstOrNull()?.trim()?.ifEmpty { null }
        }

        fun date(param: String): LocalDate? =
            single(param)?.let {
                try {
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    invalid(param, "debe ser una fecha AAAA-MM-DD")
                }
            }

        fun number(
            param: String,
            default: Int,
            range: IntRange
        ): Int {
            val raw = single(param) ?: return default
            val value = raw.toIntOrNull()
            if (value == null || value !in range) invalid(param, "debe ser un entero de ${range.first} a ${range.last}")
            return value
        }

        fun option(
            param: String,
            field: String
        ): String? {
            val value = single(param) ?: return null
            val allowed = catalog.enumOf(Attention.GRIEVANCE, field)
            if (value !in allowed) invalid(param, "debe ser uno de ${allowed.joinToString(", ")}")
            return value
        }
        val projects =
            params[PARAM_PROJECT].orEmpty().map { raw -> runCatching { UUID.fromString(raw.trim()) }.getOrElse { invalid(PARAM_PROJECT, "debe ser un UUID") } }
        val types = params[PARAM_TYPE].orEmpty().map { it.trim() }
        types.firstOrNull { it !in Attention.OBJECTS }?.let { invalid(PARAM_TYPE, "debe ser uno de ${Attention.OBJECTS.joinToString(", ")}") }
        val start = date(PARAM_START)
        val end = date(PARAM_END)
        if (start != null && end != null && start.isAfter(end)) invalid(PARAM_START, "no puede ser posterior a periodEnd")
        return AttentionReportFilters(
            projects = projects.distinct(),
            periodStart = start,
            periodEnd = end,
            types = Attention.OBJECTS.filter { types.isEmpty() || it in types },
            channel = option(PARAM_CHANNEL, "channel"),
            category = option(PARAM_CATEGORY, "category"),
            page = number(PARAM_PAGE, 0, 0..Int.MAX_VALUE),
            size = number(PARAM_SIZE, DEFAULT_SIZE, 1..MAX_SIZE)
        )
    }
}

// GET /api/sgspe/attention/report (api.md §4.6): lee como quien llama, nunca como la plataforma, así que una QCS confidencial
// que no ve no cuenta, y lo dice confidentialHidden, sin el número de excluidas (security.md §9.6). sin `project`, «mis
// proyectos» de ProjectReach: todos los que lee quien lee todo (ADMIN y READ_ALL_PROJECTS) y, si no, las asignaciones vigentes
// (ninguna, sin asignaciones). la lectura es acotada por tipo (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS
@Service
class AttentionReport(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val reach: ProjectReach,
    private val catalog: ModelCatalog,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    suspend fun report(params: Map<String, List<String>>): Map<String, Any?> {
        val filters = AttentionReportRows.parse(params, catalog)
        val user = callers.require()
        val projects = if (filters.projects.isNotEmpty()) filters.projects else reach.mine(user) ?: allProjects()
        val read = filters.types.associateWith { type -> rows(type, projects, filters) }
        return AttentionReportRows.report(read, permissions.confidentialHidden(user), filters.page, filters.size)
    }

    // las filas de un tipo como las lee quien llama; null si no tiene READ sobre el objeto
    private suspend fun rows(
        type: String,
        projects: List<UUID>,
        filters: AttentionReportFilters
    ): List<Raised>? {
        if (projects.isEmpty()) return emptyList()
        val equal = listOfNotNull(filters.channel?.let { "channel" to it }, filters.category?.let { "category" to it }).toMap()
        val criteria =
            listOfNotNull(
                Criteria.anyOf("project", projects),
                if (filters.periodStart == null && filters.periodEnd == null) {
                    null
                } else {
                    Criteria.between(
                        Attention.RECEIVED_AT,
                        (filters.periodStart ?: LocalDate.of(1900, 1, 1)).atStartOfDay(LIMA).toInstant(),
                        (filters.periodEnd ?: LocalDate.of(9999, 12, 30)).plusDays(1).atStartOfDay(LIMA).toInstant()
                    )
                }
            )
        return try {
            records.all(type, equal, criteria, max = maxRows).map { Raised(type, it.id, it.state, it.attributes) }
        } catch (_: ForbiddenException) {
            null
        }
    }

    private suspend fun allProjects(): List<UUID> =
        try {
            records.all("project", max = maxRows).map { UUID.fromString(it.id) }
        } catch (_: ForbiddenException) {
            emptyList()
        }
}

@RestController
@RequestMapping("/api/sgspe/attention")
class AttentionReportController(
    private val report: AttentionReport
) {
    @GetMapping("/report")
    suspend fun report(request: ServerHttpRequest): Map<String, Any?> = report.report(request.queryParams)
}
