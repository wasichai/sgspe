package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// GET /api/sgspe/attention/queue (decisión 5 de la revisión de UX, D10): las quejas, consultas y solicitudes del proyecto en una
// lista paginada, con el conteo de cada vista; un parámetro desconocido es 422 y una vista desconocida 400. desde el issue #160,
// ordena por columna con sort y dir (lista cerrada; fuera de ella, 422) y desempata por id
class AttentionQueueApiTest : F4ApiTest() {
    private fun cola(
        query: String,
        account: Account? = null
    ): JsonNode = tree(send("GET", "/api/sgspe/attention/queue?$query", null, HttpStatus.OK, account?.token ?: token))

    private fun ids(cola: JsonNode): List<String> = cola["content"].values().map { it["record"]["id"].asString() }

    @Test
    fun `sin sort ordena como siempre y con sort por la recepcion o el codigo`() {
        val project = project()
        val base = uniqueName("QJ-")
        val media = grievance(project, attributes = mapOf("code" to "$base-B", "received_at" to "2026-10-02T15:00:00Z"))
        val reciente = grievance(project, attributes = mapOf("code" to "$base-C", "received_at" to "2026-10-03T15:00:00Z"))
        val antigua = grievance(project, attributes = mapOf("code" to "$base-A", "received_at" to "2026-10-01T15:00:00Z"))
        val siempre = ids(cola("project=$project&view=all"))
        assertEquals(setOf(media, reciente, antigua), siempre.toSet())
        assertEquals(siempre, ids(cola("project=$project&view=all")))
        assertEquals(listOf(reciente, media, antigua), ids(cola("project=$project&view=all&sort=received_at&dir=desc")))
        assertEquals(listOf(antigua, media, reciente), ids(cola("project=$project&view=all&sort=code&dir=asc")))
    }

    @Test
    fun `el desempate por id es estable entre paginas`() {
        val project = project()
        val todas = (1..3).map { grievance(project) }
        val paginas = (0..2).flatMap { ids(cola("project=$project&view=all&sort=received_at&dir=desc&size=1&page=$it")) }
        assertEquals(todas.sorted(), paginas)
    }

    @Test
    fun `un sort fuera de la lista o un dir invalido es 422`() {
        for (query in listOf("sort=description", "sort=code&dir=arriba", "dir=desc")) {
            val response = exchange("GET", "/api/sgspe/attention/queue?$query")
            assertEquals(HttpStatus.valueOf(422), response.status, query)
            assertEquals("INVALID_SORT", code(response), query)
        }
    }

    @Test
    fun `una QCS confidencial que quien llama no lee no aparece con ningun orden`() {
        val project = project()
        val receiver = member("COMMUNITY_RELATIONS_OFFICER", project)
        val colleague = member("COMMUNITY_RELATIONS_OFFICER", project)
        val confidencial = grievance(project, receiver, mapOf("confidential" to true))
        val visible = grievance(project, receiver)
        for (orden in listOf("", "&sort=received_at&dir=desc", "&sort=code&dir=asc", "&sort=due_at&dir=desc")) {
            val vista = cola("project=$project&view=all$orden", colleague)
            assertEquals(listOf(visible), ids(vista), orden)
            assertEquals(1, vista["views"]["all"].asInt(), orden)
            assertEquals(setOf(confidencial, visible), ids(cola("project=$project&view=all$orden", receiver)).toSet(), orden)
        }
    }

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
