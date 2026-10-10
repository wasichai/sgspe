package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import tools.jackson.databind.JsonNode

// UPS-24 (EvidenceFileReadPolicy sobre FileDescriptorReadPolicy de wasichai 1.0.0): quien no tiene DOWNLOAD_RESTRICTED no lee
// el nombre original ni la huella SHA-256 de una evidencia CONFIDENCIAL o DATOS PERSONALES por ninguna ruta de lectura, y sí
// su tipo y su tamaño. las dos evidencias de cada prueba suben los mismos bytes (la misma huella), así que la lista se mira
// por el id de cada registro y las demás rutas, que solo traen la restringida, por su texto
class EvidenceDescriptorApiTest : F3DomainApiTest() {
    private fun reserved(file: JsonNode) {
        assertTrue(file.has("id") && file.has("contentType") && file.has("size"), file.toString())
        assertFalse(file.has("name") || file.has("sha256"), file.toString())
    }

    @Test
    fun `sin DOWNLOAD_RESTRICTED, ni el registro, ni la lista, ni los vinculos, ni la historia dan el nombre o la huella`() {
        val project = testProject()
        val reader = member("ANALYST", project)
        val engagement = engagement(project)
        val restricted = evidence(project, "FOTO", "CONFIDENCIAL", links = listOf(mapOf("object" to "engagement", "recordId" to engagement)))
        val open = evidence(project, "ACTA", "INTERNA")
        val sha = record("evidence", restricted)["attributes"]["file"]["sha256"].asString()

        reserved(tree(send("GET", "/api/objects/evidence/records/$restricted", null, HttpStatus.OK, reader.token))["attributes"]["file"])
        val list = tree(send("GET", "/api/objects/evidence/records?project=$project&size=50", null, HttpStatus.OK, reader.token))["content"]
        reserved(list.values().single { it["id"].asString() == restricted }["attributes"]["file"])
        val shown = list.values().single { it["id"].asString() == open }["attributes"]["file"]
        assertEquals("prueba.pdf", shown["name"].asString())
        assertEquals(sha, shown["sha256"].asString())

        for (path in listOf(
            "/api/objects/engagement/records/$engagement/related/evidence_engagement",
            "/api/objects/evidence/records/$restricted/history"
        )) {
            val body = send("GET", path, null, HttpStatus.OK, reader.token)
            assertTrue(body.contains(restricted), path)
            assertFalse(body.contains("prueba.pdf") || body.contains(sha), "$path: $body")
        }
    }

    @Test
    fun `AUDITOR lee la auditoria de una evidencia restringida sin su nombre ni su huella`() {
        val project = testProject()
        val restricted = evidence(project, "ACTA", "DATOS PERSONALES")
        val sha = record("evidence", restricted)["attributes"]["file"]["sha256"].asString()
        val auditor = userWithRole("AUDITOR")
        val body = send("GET", "/api/audit?recordId=$restricted&limit=500", null, HttpStatus.OK, auditor.token)
        assertTrue(body.contains(restricted), body)
        assertFalse(body.contains("prueba.pdf") || body.contains(sha), body)
    }

    @Test
    fun `con DOWNLOAD_RESTRICTED, ADMIN incluido, el descriptor llega entero, y quien la subio sin la accion ya no lo lee`() {
        val project = testProject()
        val uploader = member("FIELD_AGENT", project)
        val restricted = evidence(project, "FOTO", "CONFIDENCIAL", uploader)
        val manager = member("SOCIAL_MANAGER", project)
        for (account in listOf(manager.token, token)) {
            val file = tree(send("GET", "/api/objects/evidence/records/$restricted", null, HttpStatus.OK, account))["attributes"]["file"]
            assertEquals("prueba.pdf", file["name"].asString())
            assertTrue(Regex("^[0-9a-f]{64}$").matches(file["sha256"].asString()))
        }
        // subirla no da la descarga (security.md §14), así que tampoco el nombre ni la huella al leerla después
        reserved(tree(send("GET", "/api/objects/evidence/records/$restricted", null, HttpStatus.OK, uploader.token))["attributes"]["file"])
    }
}
