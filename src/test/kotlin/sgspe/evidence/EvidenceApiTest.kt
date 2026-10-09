package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import java.util.UUID

// las evidencias de F3 sobre wasichai-files (decisión D1, api.md §4.9, RF-DOC-001, RF-DOC-003, ASM-071): el alta con sus vínculos,
// la inmutabilidad y la reclasificación; la descarga con DOWNLOAD_RESTRICTED por las dos rutas llega en la Tarea 6
class EvidenceApiTest : F3DomainApiTest() {
    @Test
    fun `el alta guarda el descriptor del archivo con su SHA-256 y crea los vinculos en una transaccion`() {
        val project = testProject()
        val engagement = engagement(project)
        val response =
            createEvidence(
                mapOf("file" to stagedFile("evidence", "file"), "evidence_type" to "ACTA", "classification" to "INTERNA", "project" to project),
                listOf(mapOf("object" to "engagement", "recordId" to engagement))
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(created["attributes"]["file"]["sha256"].asString()))
        assertEquals("/api/objects/evidence/records/${created["id"].asString()}", response.headers.location.toString())
        val linked = tree(send("GET", "/api/objects/evidence/records/${created["id"].asString()}/related/evidence_engagement", null, HttpStatus.OK))
        assertEquals(engagement, linked["content"][0]["id"].asString())
    }

    @Test
    fun `un vinculo invalido, un atributo desconocido o un archivo ajeno no crean nada`() {
        val project = testProject()
        val file = stagedFile("evidence", "file")
        val bad =
            createEvidence(
                mapOf("file" to file, "evidence_type" to "ACTA", "classification" to "INTERNA"),
                listOf(
                    mapOf(
                        "object" to "project",
                        "recordId" to project
                    )
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, bad.status)
        assertEquals("INVALID_LINK", code(bad))
        val unknown = createEvidence(mapOf("file" to file, "evidence_type" to "ACTA", "classification" to "INTERNA", "sha256" to "x"))
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status)
        assertEquals("attributes.sha256", problemFields(unknown.body).single())
        // StoredFileGuard: un archivo que subió otra persona no se adjunta
        val other = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = createEvidence(mapOf("file" to file, "evidence_type" to "ACTA", "classification" to "INTERNA", "project" to project), token = other.token)
        assertEquals(HttpStatus.BAD_REQUEST, foreign.status, foreign.body)
        assertEquals(listOf("file"), problemFields(foreign.body))
    }

    @Test
    fun `el archivo no cambia y la evidencia no se borra, ni por el comando ni por la API generica`() {
        val id = evidence(testProject())
        val change = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("file" to stagedFile("evidence", "file"))), token, ifMatch("evidence", id))
        assertEquals(HttpStatus.BAD_REQUEST, change.status)
        assertEquals("FIELD_NOT_EDITABLE", code(change))
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/evidence/records/$id", null, token).status)
        val replace =
            multipart("/api/objects/evidence/records/$id/files/file", mapOf("file" to ("otra.pdf" to PDF)), emptyMap(), token, ifMatch("evidence", id))
        // la ruta nativa escribe por la API genérica, que no escribe un objeto apiOnly
        assertEquals(HttpStatus.FORBIDDEN, replace.status, replace.text)
    }

    @Test
    fun `los datos descriptivos cambian con If-Match, y un PATCH sin cambios no mueve updatedAt`() {
        val id = evidence(testProject())
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Acta")), token).status)
        val headers = ifMatch("evidence", id)
        val changed = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Acta firmada")), token, headers)
        assertEquals(HttpStatus.OK, changed.status, changed.body)
        val updatedAt = tree(changed.body)["updatedAt"].asString()
        val same = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Acta firmada")), token, mapOf("If-Match" to "\"$updatedAt\""))
        assertEquals(updatedAt, tree(same.body)["updatedAt"].asString())
        assertEquals(HttpStatus.PRECONDITION_FAILED, patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Otra")), token, headers).status)
    }

    @Test
    fun `bajar la clasificacion de una restringida exige DOWNLOAD_RESTRICTED y motivo`() {
        val project = testProject()
        val agent = member("FIELD_AGENT", project)
        val id = evidence(project, "FOTO", "DATOS PERSONALES", agent)
        val noReason = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("classification" to "INTERNA")), agent.token, ifMatch("evidence", id))
        assertEquals(HttpStatus.BAD_REQUEST, noReason.status)
        val denied =
            patch(
                "/api/sgspe/evidence/$id",
                mapOf("attributes" to mapOf("classification" to "INTERNA")),
                agent.token,
                ifMatch("evidence", id) + withReason()
            )
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals("ACTION_REQUIRED", code(denied))
        val manager = member("SOCIAL_MANAGER", project)
        val allowed =
            patch(
                "/api/sgspe/evidence/$id",
                mapOf("attributes" to mapOf("classification" to "INTERNA")),
                manager.token,
                ifMatch("evidence", id) + withReason()
            )
        assertEquals(HttpStatus.OK, allowed.status, allowed.body)
    }

    @Test
    fun `vincular y desvincular exigen motivo con un destino requiresReason, y FIELD_AGENT no toca la evidencia ajena`() {
        val project = testProject()
        val id = evidence(project)
        val commitment = commitment(project)
        val link = exchange("POST", "/api/sgspe/evidence/$id/links", mapOf("object" to "commitment", "recordId" to commitment), token)
        assertEquals(HttpStatus.BAD_REQUEST, link.status)
        assertEquals("REASON_REQUIRED", code(link))
        assertEquals(
            HttpStatus.NO_CONTENT,
            exchange("POST", "/api/sgspe/evidence/$id/links", mapOf("object" to "commitment", "recordId" to commitment), token, withReason()).status
        )
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/sgspe/evidence/$id/links/commitment/$commitment", null, token, withReason()).status)
        val agent = member("FIELD_AGENT", project)
        val foreign = exchange("POST", "/api/sgspe/evidence/$id/links", mapOf("object" to "engagement", "recordId" to engagement(project)), agent.token)
        assertEquals(HttpStatus.FORBIDDEN, foreign.status, foreign.body)
        assertEquals("NOT_OWN_RECORD", code(foreign))
    }

    // P52: el tope por parte de Spring es el de wasichai.files.max-bytes (20 MB). Spring cuenta solo los bytes del contenido y
    // rechaza los que pasan el tope: 20 MB exactos entran, y un byte más es 400 FILE_TOO_LARGE sobre file (UploadLimits), con
    // código, nunca un 500 ni el texto libre de wasichai-files
    @Test
    fun `un archivo de 20 MB exactos se sube y uno de un byte mas es 400 FILE_TOO_LARGE sobre file`() {
        val max = 20 * 1024 * 1024
        val limit = PDF + ByteArray(max - PDF.size)
        val accepted = multipart("/api/objects/evidence/files/file", mapOf("file" to ("limite.pdf" to limit)), emptyMap())
        assertEquals(HttpStatus.CREATED, accepted.status, accepted.text)
        assertEquals(max.toLong(), tree(accepted.text)["size"].asLong())
        val over = multipart("/api/objects/evidence/files/file", mapOf("file" to ("grande.pdf" to (limit + 0.toByte()))), emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, over.status, over.text)
        assertEquals(listOf("file"), problemFields(over.text))
        assertEquals("FILE_TOO_LARGE", tree(over.text)["errors"][0]["message"].asString())
    }

    // UploadLimitsAdvice traduce solo un multipart: un cuerpo JSON que pasa spring.codec.max-in-memory-size (256 KB) sigue
    // siendo el 413 de Spring, aunque su causa también sea una DataBufferLimitException
    @Test
    fun `un cuerpo JSON mas grande que el tope del codec sigue siendo 413`() {
        val response = createEvidence(mapOf("file" to UUID.randomUUID().toString(), "title" to "x".repeat(300 * 1024)))
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.status, response.body)
    }

    // un archivo es de una sola evidencia (ASM-071): con el mismo archivo, quien no puede bajar la clasificación no crea otra
    // evidencia menos restringida, y un reintento sin Idempotency-Key no deja un duplicado
    @Test
    fun `un archivo ya adjunto no se adjunta a otra evidencia`() {
        val project = testProject()
        val agent = member("FIELD_AGENT", project)
        val file = stagedFile("evidence", "file", agent)
        val attributes = mapOf("file" to file, "evidence_type" to "FOTO", "classification" to "DATOS PERSONALES", "project" to project)
        assertEquals(HttpStatus.CREATED, createEvidence(attributes, token = agent.token).status)
        val again = createEvidence(attributes + ("classification" to "INTERNA"), token = agent.token)
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("EVIDENCE_FILE_IN_USE", code(again))
        assertEquals(listOf("file"), problemFields(again.body))
    }
}
