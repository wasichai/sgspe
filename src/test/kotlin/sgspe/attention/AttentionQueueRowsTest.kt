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
        channel: String? = null
    ) = AttentionQueueFilters(null, view, q, channel, null, null, null, page, size)

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

    private fun status(e: Exception): Int = (e as wasichai.core.common.WasichaiException).status.value()
}
