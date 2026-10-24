package sgspe.attention

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordResponse
import java.util.UUID

// los filtros de la cola de atención (api.md §4.6), ya validados
data class AttentionQueueFilters(
    val project: UUID?,
    val view: String,
    val q: String?,
    val channel: String?,
    val category: String?,
    val severity: String?,
    val stakeholder: UUID?,
    val page: Int,
    val size: Int,
    val sort: QueueSort? = null
)

// el orden por columna que pide quien llama (issue #160): uno de AttentionQueueRows.SORTS, ascendente salvo dir=desc
data class QueueSort(
    val field: String,
    val descending: Boolean
)

// una fila de la cola: su tipo (el objeto) y el registro como lo lee quien llama
data class QueueRow(
    val type: String,
    val record: RecordResponse
)

// la cola de atención, pura (decisión 5 de la revisión de UX, D10): las quejas, consultas y solicitudes en una sola lista para
// atender por plazo, paginada y contada en el servidor. cada fila va en su grupo de plazo (vencidas, por vencer, al día: el
// deadline_status de JOB-01, que cuenta solo en los estados con plazo) y, dentro, primero lo que vence antes; empata la recepción
// más antigua y, luego, el código. las vistas: atención (las abiertas), una por tipo (sus abiertas), mías (abiertas y de quien
// llama) y todas. los filtros son de igualdad (canal, categoría, severidad, grupo de interés) y la búsqueda mira el código y la
// descripción
object AttentionQueueRows {
    const val PARAM_PROJECT = "project"
    const val PARAM_VIEW = "view"
    const val PARAM_Q = "q"
    const val PARAM_CHANNEL = "channel"
    const val PARAM_CATEGORY = "category"
    const val PARAM_SEVERITY = "severity"
    const val PARAM_STAKEHOLDER = "stakeholder"
    const val PARAM_PAGE = "page"
    const val PARAM_SIZE = "size"
    const val PARAM_SORT = "sort"
    const val PARAM_DIR = "dir"
    val PARAMS: Set<String> =
        setOf(
            PARAM_PROJECT,
            PARAM_VIEW,
            PARAM_Q,
            PARAM_CHANNEL,
            PARAM_CATEGORY,
            PARAM_SEVERITY,
            PARAM_STAKEHOLDER,
            PARAM_PAGE,
            PARAM_SIZE,
            PARAM_SORT,
            PARAM_DIR
        )

    const val DEFAULT_SIZE = 25
    const val MAX_SIZE = 200

    const val ATTENTION = "attention"
    const val MINE = "mine"
    const val ALL = "all"
    val BY_TYPE: Map<String, String> = mapOf("grievances" to Attention.GRIEVANCE, "inquiries" to Attention.INQUIRY, "requests" to Attention.REQUEST)
    val VIEWS: List<String> = listOf(ATTENTION) + BY_TYPE.keys + listOf(MINE, ALL)

    const val OVERDUE = "overdue"
    const val DUE_SOON = "dueSoon"
    const val ON_TIME = "onTime"
    val GROUPS: List<String> = listOf(OVERDUE, DUE_SOON, ON_TIME)

    // el campo del plazo que corre en el estado (workflows.md «Plazos y escalamiento»), como dueDate del reporte unificado
    fun dueDate(row: QueueRow): String? = AttentionReportRows.dueDate(row.type, row.record.state, row.record.attributes)

    private fun tracked(row: QueueRow): Boolean =
        (row.type == Attention.GRIEVANCE && row.record.state in AttentionReportRows.GRIEVANCE_TRACKED) ||
            (row.type == Attention.INQUIRY && row.record.state == "received")

    // la situación de plazo cuenta solo en los estados que tienen plazo: fuera de ellos, al día
    fun group(row: QueueRow): String {
        if (!tracked(row)) return ON_TIME
        return when (Values.text(row.record.attributes[Attention.DEADLINE_STATUS])) {
            "VENCIDO" -> OVERDUE
            "POR VENCER" -> DUE_SOON
            else -> ON_TIME
        }
    }

    private fun open(row: QueueRow): Boolean = Attention.isOpen(row.type, row.record.state)

    fun inView(
        view: String,
        row: QueueRow,
        user: String
    ): Boolean =
        when (view) {
            ALL -> true
            MINE -> open(row) && Values.text(row.record.attributes[Attention.OWNER]) == user
            ATTENTION -> open(row)
            else -> row.type == BY_TYPE[view] && open(row)
        }

    fun matches(
        row: QueueRow,
        filters: AttentionQueueFilters
    ): Boolean {
        val a = row.record.attributes

        fun equal(
            field: String,
            value: String?
        ) = value == null || Values.text(a[field]) == value
        val q = filters.q?.lowercase()
        return equal("channel", filters.channel) &&
            equal("category", filters.category) &&
            equal(Attention.SEVERITY, filters.severity) &&
            equal("stakeholder", filters.stakeholder?.toString()) &&
            (
                q == null ||
                    Values
                        .text(a["code"])
                        .orEmpty()
                        .lowercase()
                        .contains(q) ||
                    Values
                        .text(a["description"])
                        .orEmpty()
                        .lowercase()
                        .contains(q)
            )
    }

    // el orden de la cola: por grupo y, dentro, por el plazo que corre (sin fecha al final), la recepción y el código
    val ORDER: Comparator<QueueRow> =
        compareBy<QueueRow> { GROUPS.indexOf(group(it)) }
            .thenBy(nullsLast()) { dueDate(it) }
            .thenBy(nullsLast()) { Values.text(it.record.attributes[Attention.RECEIVED_AT]) }
            .thenBy { Values.text(it.record.attributes["code"]).orEmpty() }
            .thenBy { it.record.id }

    // la lista cerrada de sort: la recepción, el código y el plazo que corre (dueDate, el de ORDER)
    val SORTS: Map<String, (QueueRow) -> String?> =
        linkedMapOf(
            "received_at" to { row -> Values.text(row.record.attributes[Attention.RECEIVED_AT]) },
            "code" to { row -> Values.text(row.record.attributes["code"]) },
            "due_at" to { row -> dueDate(row) }
        )
    val DIRS: List<String> = listOf("asc", "desc")

    // el orden con sort: la columna en su sentido, sin valor al final en los dos sentidos y, a igualdad, el id ascendente, para
    // que la paginación sea estable
    fun order(sort: QueueSort?): Comparator<QueueRow> {
        if (sort == null) return ORDER
        val key = SORTS.getValue(sort.field)
        val values: Comparator<String> = if (sort.descending) reverseOrder() else naturalOrder()
        return compareBy<QueueRow, String?>(nullsLast(values)) { key(it) }.thenBy { it.record.id }
    }

    // la respuesta: la página de la vista con sus filtros, el conteo de cada vista y de cada grupo con los filtros, el de la vista
    // sin ellos (el vacío por filtros) y las abiertas por vencer (la cifra de arriba, sin filtros). `readable` son los tipos que
    // quien llama lee
    fun queue(
        rows: List<QueueRow>,
        readable: List<String>,
        filters: AttentionQueueFilters,
        user: String
    ): Map<String, Any?> {
        val filtered = rows.filter { matches(it, filters) }
        val inView = filtered.filter { inView(filters.view, it, user) }.sortedWith(order(filters.sort))
        // el desplazamiento en Long: page * size desborda Int con una página enorme; fuera de rango, la página sale vacía
        val offset = minOf(filters.page.toLong() * filters.size, inView.size.toLong()).toInt()
        val content = inView.drop(offset).take(filters.size).map { linkedMapOf("type" to it.type, "group" to group(it), "record" to it.record) }
        return linkedMapOf(
            "content" to content,
            "page" to filters.page,
            "size" to filters.size,
            "totalElements" to inView.size,
            "totalPages" to (inView.size + filters.size - 1) / filters.size,
            "views" to VIEWS.associateWith { view -> filtered.count { inView(view, it, user) } },
            "groups" to GROUPS.associateWith { g -> inView.count { group(it) == g } },
            "unfiltered" to rows.count { inView(filters.view, it, user) },
            "openDueSoon" to rows.count { open(it) && group(it) == DUE_SOON },
            "readable" to readable
        )
    }

    // los parámetros validados: un valor inválido es 400 INVALID_FILTER sobre el parámetro; uno desconocido, 422, y un sort o
    // un dir fuera de su lista cerrada (o un dir sin sort), 422 INVALID_SORT sobre el parámetro
    fun parse(
        params: Map<String, List<String>>,
        catalog: ModelCatalog
    ): AttentionQueueFilters {
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

        fun id(param: String): UUID? = single(param)?.let { runCatching { UUID.fromString(it) }.getOrElse { invalid(param, "debe ser un UUID") } }

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

        // canal y categoría usan el mismo ENUM en los tres tipos; la severidad es solo de las quejas
        fun option(
            param: String,
            field: String
        ): String? {
            val value = single(param) ?: return null
            val allowed = catalog.enumOf(Attention.GRIEVANCE, field)
            if (value !in allowed) invalid(param, "debe ser uno de ${allowed.joinToString(", ")}")
            return value
        }

        fun badSort(
            param: String,
            detail: String
        ): Nothing = throw Finding("INVALID_SORT", "Orden inválido: $param $detail", param, status = HttpStatus.valueOf(422)).toCodedException()

        fun sortParam(param: String): String? {
            val values = params[param].orEmpty()
            if (values.size > 1) badSort(param, "va una sola vez")
            return values.firstOrNull()?.trim()?.ifEmpty { null }
        }

        fun sort(): QueueSort? {
            val field = sortParam(PARAM_SORT)
            val dir = sortParam(PARAM_DIR)
            if (field == null) {
                if (dir != null) badSort(PARAM_DIR, "va con sort")
                return null
            }
            if (field !in SORTS) badSort(PARAM_SORT, "debe ser uno de ${SORTS.keys.joinToString(", ")}")
            if (dir != null && dir !in DIRS) badSort(PARAM_DIR, "debe ser uno de ${DIRS.joinToString(", ")}")
            return QueueSort(field, dir == "desc")
        }

        val view = single(PARAM_VIEW) ?: ATTENTION
        if (view !in VIEWS) invalid(PARAM_VIEW, "debe ser una de ${VIEWS.joinToString(", ")}")
        return AttentionQueueFilters(
            project = id(PARAM_PROJECT),
            view = view,
            q = single(PARAM_Q),
            channel = option(PARAM_CHANNEL, "channel"),
            category = option(PARAM_CATEGORY, "category"),
            severity = option(PARAM_SEVERITY, Attention.SEVERITY),
            stakeholder = id(PARAM_STAKEHOLDER),
            page = number(PARAM_PAGE, 0, 0..Int.MAX_VALUE),
            size = number(PARAM_SIZE, DEFAULT_SIZE, 1..MAX_SIZE),
            sort = sort()
        )
    }
}

// GET /api/sgspe/attention/queue (api.md §4.6): lee como quien llama, nunca como la plataforma, así que una QCS confidencial que no
// ve no llega (security.md §9.6) y el alcance por proyecto lo pone el servidor (ADR-0029). un tipo sin READ no aporta filas. la
// lectura es acotada por tipo (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS sobre project
@Service
class AttentionQueue(
    private val records: Records,
    private val callers: Callers,
    private val catalog: ModelCatalog,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    suspend fun queue(params: Map<String, List<String>>): Map<String, Any?> {
        val filters = AttentionQueueRows.parse(params, catalog)
        val user = callers.require()
        val read = Attention.OBJECTS.associateWith { type -> rows(type, filters) }
        val readable = read.filterValues { it != null }.keys.toList()
        return AttentionQueueRows.queue(read.values.filterNotNull().flatten(), readable, filters, user.userId.toString())
    }

    // las filas de un tipo como las lee quien llama, con el proyecto; null si no tiene READ sobre el objeto
    private suspend fun rows(
        type: String,
        filters: AttentionQueueFilters
    ): List<QueueRow>? =
        try {
            records.all(type, listOfNotNull(filters.project?.let { "project" to it.toString() }).toMap(), max = maxRows).map { QueueRow(type, it) }
        } catch (_: ForbiddenException) {
            null
        }
}

@RestController
@RequestMapping("/api/sgspe/attention")
class AttentionQueueController(
    private val queue: AttentionQueue
) {
    @GetMapping("/queue")
    suspend fun queue(request: ServerHttpRequest): Map<String, Any?> = queue.queue(request.queryParams)
}
