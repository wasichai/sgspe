package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// GET /api/sgspe/attention/queue (decisión 5 de la revisión de UX, D10): las quejas, consultas y solicitudes del proyecto en una
// lista paginada, con el conteo de cada vista; un parámetro desconocido es 422 y una vista desconocida 400
class AttentionQueueApiTest : F4ApiTest() {
    @Test
    fun `junta las QCS del proyecto en una pagina con sus conteos por vista`() {
        val project = project()
        val queja = grievance(project)
        val consulta = inquiry(project)
        grievance(project())
        val cola = tree(send("GET", "/api/sgspe/attention/queue?project=$project", null, HttpStatus.OK))
        assertEquals(
            setOf("grievance:$queja", "inquiry:$consulta"),
            cola["content"]
                .values()
                .map {
                    "${it["type"].asString()}:${it["record"]["id"].asString()}"
                }.toSet()
        )
        assertEquals(2, cola["totalElements"].asInt())
        assertEquals(1, cola["views"]["grievances"].asInt())
        assertEquals(1, cola["views"]["inquiries"].asInt())
        assertEquals(0, cola["views"]["requests"].asInt())
        val quejas = tree(send("GET", "/api/sgspe/attention/queue?project=$project&view=grievances&size=1", null, HttpStatus.OK))
        assertEquals(listOf(queja), quejas["content"].values().map { it["record"]["id"].asString() })
        assertEquals(1, quejas["totalPages"].asInt())
    }

    @Test
    fun `una vista desconocida es 400 y un parametro ajeno 422`() {
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/attention/queue?view=abiertas").status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/attention/queue?estado=x").status)
    }
}
