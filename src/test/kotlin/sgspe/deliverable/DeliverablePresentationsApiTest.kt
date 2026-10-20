package sgspe.deliverable

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import sgspe.privacy.CanonicalJson
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure

// ADR-0031, deliverables.md §16.8, api.md §4.12, security.md §14 («Presentaciones»): el ciclo de una presentación desde una
// versión emitida, con el doble del compositor. el pedido y sus rechazos, JOB-09, las descargas con el permiso del nivel, la
// lista de verificación y export_log, las rutas nativas cerradas, el servicio caído y la regeneración desde lo congelado
class DeliverablePresentationsApiTest : F8ApiTest() {
    // un T1 sintético con hechos tipados (typeVersion 2): el que emiten los tipos desde las presentaciones
    private val model: ByteArray = CanonicalJson.bytes(DeckDemo.model())

    @AfterEach
    fun reset() {
        fakeSlides.unavailable = false
        fakeSlides.failure = null
    }

    // un entregable emitido con el modelo de las presentaciones: la versión INTERNO y la EXTERNO derivada
    private fun issued(project: String): String {
        val manager = member("SOCIAL_MANAGER", project)
        val id = approvedDeliverable(project, manager, model)
        val response = issue(id, manager)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return id
    }

    private fun number(
        id: String,
        level: String
    ): Int =
        tree(send("GET", "/api/objects/deliverable_version/records?deliverable=$id", null, HttpStatus.OK))["content"]
            .values()
            .single { it["attributes"]["disclosure_level"].asString() == level }["attributes"]["version_number"]
            .asInt()

    private fun request(
        id: String,
        version: Int,
        audience: String,
        level: String,
        token: String = this.token
    ) = exchange("POST", "/api/sgspe/deliverables/$id/versions/$version/presentations", mapOf("audience" to audience, "level" to level), token)

    private fun codes(response: Response): List<String> = tree(response.body)["errors"].values().map { it["message"].asString() }

    private fun downloads(presentation: String): Int =
        tree(send("GET", "/api/objects/export_log/records?deliverable_presentation=$presentation", null, HttpStatus.OK))["content"].size()

    @Test
    fun `una presentacion EXTERNO se pide, JOB-09 la compone y se baja con la lista de verificacion y su fila`() {
        val project = testProject()
        val id = issued(project)
        val external = number(id, "EXTERNO")
        val manager = member("SOCIAL_MANAGER", project)
        val asked = request(id, external, "DIRECCION", "EXTERNO", manager.token)
        assertEquals(HttpStatus.ACCEPTED, asked.status, asked.body)
        val presentation = tree(asked.body)["presentation"]["id"].asString()
        assertEquals("EN COLA", tree(asked.body)["presentation"]["status"].asString())
        assertEquals("/api/sgspe/presentations/$presentation", asked.headers.location?.path)
        assertEquals(1, compose())
        val status = tree(exchange("GET", "/api/sgspe/presentations/$presentation", token = manager.token).body)
        assertEquals("LISTO", status["status"].asString(), status.toString())
        assertEquals(DeckPlanner.VERSION, status["plannerVersion"].asString())
        assertEquals("remotion@prueba", status["rendererVersion"].asString())
        assertTrue(status["slideCount"].asInt() >= 6)
        // sin la lista de verificación, 400; con ella, el PDF y la especificación, cada uno con su fila
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/presentations/$presentation/content", token = manager.token).status)
        assertEquals(0, downloads(presentation))
        val pdf = exchange("GET", "/api/sgspe/presentations/$presentation/content?checklistConfirmed=true", token = manager.token)
        assertEquals(HttpStatus.OK, pdf.status, pdf.body)
        assertEquals(status["pdfSha256"].asString(), pdf.headers.eTag?.trim('"'))
        assertEquals("nosniff", pdf.headers.getFirst("X-Content-Type-Options"))
        val spec = exchange("GET", "/api/sgspe/presentations/$presentation/spec?checklistConfirmed=true", token = manager.token)
        assertEquals(HttpStatus.OK, spec.status, spec.body)
        assertEquals(status["specSha256"].asString(), spec.headers.eTag?.trim('"'))
        assertEquals("EXTERNO", tree(spec.body)["meta"]["level"].asString())
        assertEquals(2, downloads(presentation))
        // HEAD no sirve nada ni deja fila
        assertEquals(
            HttpStatus.METHOD_NOT_ALLOWED,
            exchange("HEAD", "/api/sgspe/presentations/$presentation/content?checklistConfirmed=true", token = manager.token).status
        )
        assertEquals(2, downloads(presentation))
        // la lista del entregable la trae
        val list = tree(exchange("GET", "/api/sgspe/deliverables/$id/presentations", token = manager.token).body)
        assertEquals(listOf(presentation), list.values().map { it["id"].asString() })
    }

    @Test
    fun `regenerar desde la version emitida da la misma especificacion aunque los registros vivos cambien`() {
        val project = testProject()
        val id = issued(project)
        val external = number(id, "EXTERNO")
        val presentation = tree(request(id, external, "INSTITUCIONES", "EXTERNO").body)["presentation"]["id"].asString()
        compose()
        val sha = tree(exchange("GET", "/api/sgspe/presentations/$presentation").body)["specSha256"].asString()
        // lo que JOB-09 compuso es la especificación del modelo congelado, no de los registros de hoy
        val expected =
            DeckPlanner
                .plan(
                    DeckPlanner.Input(
                        ReportDisclosure.apply(CanonicalJson.read(model), Disclosure.EXTERNO),
                        Disclosure.EXTERNO,
                        Audience.INSTITUCIONES
                    )
                ).bytes()
        assertEquals(CanonicalJson.sha256(expected), sha)
        assertTrue(fakeSlides.specs.last().contentEquals(expected))
    }

    @Test
    fun `un nivel menos restrictivo que la version, un publico que no lo admite o un modelo sin hechos son 422`() {
        val project = testProject()
        val id = issued(project)
        val external = number(id, "EXTERNO")
        assertEquals(listOf("LEVEL_NOT_ALLOWED"), codes(request(id, external, "DIRECCION", "INTERNO")))
        assertEquals(listOf("AUDIENCE_LEVEL_MISMATCH"), codes(request(id, external, "COMUNIDAD", "EXTERNO")))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, request(id, external, "OTRO", "EXTERNO").status)
        // un entregable emitido con un modelo v1 (READY_MODEL) no da presentación
        val manager = member("SOCIAL_MANAGER", project)
        val old = approvedDeliverable(project, manager)
        assertEquals(HttpStatus.CREATED, issue(old, manager).status)
        assertEquals(listOf("PRESENTATION_NOT_AVAILABLE"), codes(request(old, number(old, "EXTERNO"), "DIRECCION", "EXTERNO")))
        assertEquals(listOf("PUBLIC_NOT_AVAILABLE"), codes(request(old, number(old, "EXTERNO"), "COMUNIDAD", "PUBLICO")))
    }

    @Test
    fun `pedir y bajar exigen la accion del nivel`() {
        val project = testProject()
        val id = issued(project)
        val internal = number(id, "INTERNO")
        val external = number(id, "EXTERNO")
        // PROJECT_MANAGER tiene EXPORT y no DOWNLOAD_RESTRICTED: pide EXTERNO, no INTERNO
        val reader = member("PROJECT_MANAGER", project)
        assertEquals(HttpStatus.FORBIDDEN, request(id, internal, "DIRECCION", "INTERNO", reader.token).status)
        assertEquals(HttpStatus.ACCEPTED, request(id, external, "DIRECCION", "EXTERNO", reader.token).status)
        // COMMUNITY_RELATIONS_OFFICER lee, pero no pide ni baja
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        assertEquals(HttpStatus.FORBIDDEN, request(id, external, "CAMPO", "EXTERNO", officer.token).status)
        val manager = member("SOCIAL_MANAGER", project)
        val internalOne = tree(request(id, internal, "DIRECCION", "INTERNO", manager.token).body)["presentation"]["id"].asString()
        compose()
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/presentations/$internalOne/content", token = reader.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/sgspe/presentations/$internalOne/content", token = manager.token).status)
    }

    @Test
    fun `las rutas nativas de sus archivos no sirven ni suben nada`() {
        val project = testProject()
        val id = issued(project)
        val presentation = tree(request(id, number(id, "EXTERNO"), "DIRECCION", "EXTERNO").body)["presentation"]["id"].asString()
        compose()
        for (field in listOf("pdf", "spec")) {
            assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/deliverable_presentation/records/$presentation/files/$field").status, field)
        }
        assertEquals(0, downloads(presentation))
        assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/objects/deliverable_presentation/records/$presentation/files/pdf").status)
    }

    @Test
    fun `con el compositor caido queda FALLIDO sin archivos y pedirla otra vez la devuelve a la cola`() {
        val project = testProject()
        val id = issued(project)
        val external = number(id, "EXTERNO")
        fakeSlides.unavailable = true
        val presentation = tree(request(id, external, "DIRECCION", "EXTERNO").body)["presentation"]["id"].asString()
        compose()
        val failed = record("deliverable_presentation", presentation)["attributes"]
        assertEquals("FALLIDO", failed["presentation_status"].asString())
        assertEquals("SLIDES_UNAVAILABLE", failed["failure_code"].asString())
        assertTrue(failed["pdf"] == null || failed["pdf"].isNull)
        assertEquals(HttpStatus.CONFLICT, exchange("GET", "/api/sgspe/presentations/$presentation/content?checklistConfirmed=true").status)
        fakeSlides.unavailable = false
        val again = request(id, external, "DIRECCION", "EXTERNO")
        assertEquals(presentation, tree(again.body)["presentation"]["id"].asString())
        assertEquals("EN COLA", tree(again.body)["presentation"]["status"].asString())
        compose()
        assertEquals("LISTO", record("deliverable_presentation", presentation)["attributes"]["presentation_status"].asString())
    }

    @Test
    fun `ningun nombre llega a la especificacion EXTERNO ni a la PUBLICO`() {
        val project = testProject()
        val id = issued(project)
        val external = number(id, "EXTERNO")
        val before = fakeSlides.specs.size
        request(id, external, "INSTITUCIONES", "EXTERNO")
        request(id, external, "COMUNIDAD", "PUBLICO")
        compose()
        val composed = fakeSlides.specs.drop(before)
        assertEquals(2, composed.size)
        composed.forEach { spec ->
            val text = String(spec, Charsets.UTF_8)
            T1Source.personNames.forEach { assertFalse(it in text, it) }
        }
    }
}
