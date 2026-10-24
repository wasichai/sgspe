package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import wasichai.core.data.RecordResponse
import java.util.UUID

// la cola de atención (decisión 5 de la revisión de UX, D10): las tres QCS en una lista por plazo, paginada y contada en el
// servidor
class AttentionQueueRowsTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val yo = UUID.randomUUID().toString()

    private fun fila(
        type: String,
        code: String,
        state: String,
        vararg attributes: Pair<String, Any?>
    ) = QueueRow(type, RecordResponse(code.lowercase(), null, null, mapOf("code" to code) + attributes, state))

    private val vencida =
        fila("grievance", "QUE-001", "acknowledged", "deadline_status" to "VENCIDO", "resolution_due_date" to "2026-10-10", "owner_user_id" to yo)
    private val vencidaAntes = fila("inquiry", "CON-001", "received", "deadline_status" to "VENCIDO", "response_due_date" to "2026-10-05")
    private val porVencer =
        fila(
            "grievance",
            "QUE-002",
            "received",
            "deadline_status" to "POR VENCER",
            "ack_due_date" to "2026-10-22",
            "channel" to "PRESENCIAL"
        )

    // en un estado sin plazo, su deadline_status no cuenta: al día
    private val sinPlazo = fila("stakeholder_request", "SOL-001", "in_review", "deadline_status" to "VENCIDO", "description" to "Agua para el anexo")
    private val cerrada = fila("grievance", "QUE-003", "closed", "owner_user_id" to yo)
    private val filas = listOf(cerrada, sinPlazo, porVencer, vencida, vencidaAntes)

    private fun filtros(
        view: String = "attention",
        page: Int = 0,
        size: Int = 25,
        q: String? = null,
        channel: String? = null,
        sort: QueueSort? = null
    ) = AttentionQueueFilters(null, view, q, channel, null, null, null, page, size, sort)

    @Suppress("UNCHECKED_CAST")
    private fun codigos(respuesta: Map<String, Any?>) =
        (respuesta["content"] as List<Map<String, Any?>>).map { (it["record"] as RecordResponse).attributes["code"] }

    @Test
    fun `atencion ordena por grupo de plazo y, dentro, por lo que vence antes, y cuenta cada vista y cada grupo`() {
        val r = AttentionQueueRows.queue(filas, listOf("grievance", "inquiry", "stakeholder_request"), filtros(), yo)
        assertEquals(listOf("CON-001", "QUE-001", "QUE-002", "SOL-001"), codigos(r))
        assertEquals(4, r["totalElements"])
        assertEquals(
            mapOf("attention" to 4, "grievances" to 2, "inquiries" to 1, "requests" to 1, "mine" to 1, "all" to 5),
            r["views"]
        )
        assertEquals(mapOf("overdue" to 2, "dueSoon" to 1, "onTime" to 1), r["groups"])
        assertEquals(1, r["openDueSoon"])
    }

    @Test
    fun `la pagina es la de la vista, y los filtros cuentan en las vistas pero no en el vacio sin filtros`() {
        val segunda = AttentionQueueRows.queue(filas, emptyList(), filtros(page = 1, size = 3), yo)
        assertEquals(listOf("SOL-001"), codigos(segunda))
        assertEquals(2, segunda["totalPages"])
        val presencial = AttentionQueueRows.queue(filas, emptyList(), filtros(channel = "PRESENCIAL"), yo)
        assertEquals(listOf("QUE-002"), codigos(presencial))
        assertEquals(4, presencial["unfiltered"])
        val busqueda = AttentionQueueRows.queue(filas, emptyList(), filtros(view = "all", q = "agua"), yo)
        assertEquals(listOf("SOL-001"), codigos(busqueda))
    }

    @Test
    fun `los parametros se validan, una vista desconocida o un id mal formado es 400, y un parametro ajeno 422`() {
        val bien = AttentionQueueRows.parse(mapOf("view" to listOf("mine"), "page" to listOf("2"), "channel" to listOf("PRESENCIAL")), catalog)
        assertEquals("mine", bien.view)
        assertEquals(2, bien.page)
        assertEquals("attention", AttentionQueueRows.parse(emptyMap(), catalog).view)
        for (malo in listOf(mapOf("view" to listOf("abiertas")), mapOf("project" to listOf("x")), mapOf("size" to listOf("500")))) {
            assertEquals(400, assertThrows<Exception> { AttentionQueueRows.parse(malo, catalog) }.let { status(it) })
        }
        assertEquals(422, status(assertThrows<Exception> { AttentionQueueRows.parse(mapOf("estado" to listOf("x")), catalog) }))
    }

    // el orden por columna (issue #160): con sort, ese campo y, a igualdad, el id; sin fecha, al final en los dos sentidos
    private val recibidas =
        listOf(
            fila("grievance", "QUE-020", "received", "received_at" to "2026-10-03T10:00:00Z", "ack_due_date" to "2026-10-08"),
            fila("inquiry", "CON-010", "received", "received_at" to "2026-10-01T10:00:00Z", "response_due_date" to "2026-10-20"),
            fila("grievance", "QUE-005", "received", "received_at" to "2026-10-02T10:00:00Z"),
            fila("stakeholder_request", "SOL-030", "received", "received_at" to "2026-10-02T10:00:00Z")
        )

    @Test
    fun `con sort la cola ordena por esa columna, en su sentido, y desempata por id`() {
        fun orden(
            field: String,
            descending: Boolean
        ) = codigos(AttentionQueueRows.queue(recibidas, emptyList(), filtros(sort = QueueSort(field, descending)), yo))
        assertEquals(listOf("QUE-020", "QUE-005", "SOL-030", "CON-010"), orden("received_at", true))
        assertEquals(listOf("CON-010", "QUE-005", "SOL-030", "QUE-020"), orden("received_at", false))
        assertEquals(listOf("CON-010", "QUE-005", "QUE-020", "SOL-030"), orden("code", false))
        assertEquals(listOf("SOL-030", "QUE-020", "QUE-005", "CON-010"), orden("code", true))
        assertEquals(listOf("QUE-020", "CON-010", "QUE-005", "SOL-030"), orden("due_at", false))
        assertEquals(listOf("CON-010", "QUE-020", "QUE-005", "SOL-030"), orden("due_at", true))
    }

    @Test
    fun `el desempate por id es estable entre paginas`() {
        val iguales = (1..5).map { fila("grievance", "QUE-1$it", "received", "received_at" to "2026-10-02T10:00:00Z") }.shuffled()
        val paginas =
            (0..4).flatMap { codigos(AttentionQueueRows.queue(iguales, emptyList(), filtros(page = it, size = 1, sort = QueueSort("received_at", true)), yo)) }
        assertEquals(listOf("QUE-11", "QUE-12", "QUE-13", "QUE-14", "QUE-15"), paginas)
    }

    @Test
    fun `sort y dir se validan contra su lista cerrada y fuera de ella son 422`() {
        assertEquals(null, AttentionQueueRows.parse(emptyMap(), catalog).sort)
        assertEquals(QueueSort("received_at", true), AttentionQueueRows.parse(mapOf("sort" to listOf("received_at"), "dir" to listOf("desc")), catalog).sort)
        assertEquals(QueueSort("code", false), AttentionQueueRows.parse(mapOf("sort" to listOf("code")), catalog).sort)
        assertEquals(QueueSort("due_at", false), AttentionQueueRows.parse(mapOf("sort" to listOf("due_at"), "dir" to listOf("asc")), catalog).sort)
        for (malo in listOf(
            mapOf("sort" to listOf("description")),
            mapOf("sort" to listOf("code"), "dir" to listOf("arriba")),
            mapOf("dir" to listOf("desc")),
            mapOf("sort" to listOf("code", "due_at"))
        )) {
            assertEquals(422, status(assertThrows<Exception> { AttentionQueueRows.parse(malo, catalog) }), malo.toString())
        }
    }

    private fun status(e: Exception): Int = (e as wasichai.core.common.WasichaiException).status.value()
}
