package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import java.util.UUID

// AUT-12 contra wasichai de verdad (workflows.md, decisión 8 de F8): al entrar en `issued`, la constancia de emisión con su
// correlativo CE y la tabla de todas las versiones del entregable con sus SHA-256 (relatedTable, Decisión 11 del plan
// 8.1). en 8.1 la emisión la hace la prueba como el gerente social, con la marca SgspeWrite que dejará pasar
// CommandTransitionGuard: el endpoint es del plan 8.2b, que mueve este caso a DeliverableIssueApiTest y emite por
// POST /api/sgspe/deliverables/{id}/issue
class IssueCertificateApiTest : F8ApiTest() {
    @Test
    fun `AUT-12 emite la constancia con correlativo y las versiones del entregable`() {
        val id = approvedDeliverable(testProject())
        val base = deliverableVersion(id, 1, "INTERNO")
        val derived = deliverableVersion(id, 2, "EXTERNO", derivedFrom = base)
        platformUpdate("deliverable", id, mapOf("current_version" to base, "current_revision" to "0"))
        // WorkflowService.apply pide una persona: emite el gerente social con la marca SgspeWrite de Records, como lo hará el
        // endpoint de 8.2b; el rol de la transición de WF-H se comprueba de verdad
        asUser(member("SOCIAL_MANAGER")) { platformRecords.transition("deliverable", UUID.fromString(id), "issue", "emisión de prueba") }
        assertEquals("issued", state("deliverable", id))
        assertEquals("SUCCEEDED", awaitRun("deliverable", "aut-12-issue-certificate", id)["status"].asString())
        val documents = tree(send("GET", "/api/objects/deliverable/records/$id/documents", null, HttpStatus.OK)).values()
        assertEquals(1, documents.size, "$documents")
        val certificate = documents.single()
        assertTrue(certificate["number"].asString().startsWith("CE-"), "$certificate")
        assertEquals("VALID", certificate["status"].asString())
        // Decisión 11 del plan 8.1: la copia congelada lleva la tabla de las versiones (relatedTable de
        // deliverable_version_deliverable), cada una con su SHA-256 del PDF
        val related = certificate["snapshot"]["related"].toString()
        for (version in listOf(base, derived)) {
            val sha = record("deliverable_version", version)["attributes"]["pdf_sha256"].asString()
            assertTrue(related.contains(sha), "la constancia no lista la versión $version: $related")
        }
    }

    @Test
    fun `la constancia es el unico tipo de documento de los entregables`() {
        val types = tree(send("GET", "/api/objects/deliverable/document-types", null, HttpStatus.OK)).values()
        assertEquals(mapOf("deliverable-issue-certificate" to "CE"), types.associate { it["name"].asString() to it["prefix"].asString() })
        val automations = tree(send("GET", "/api/objects/deliverable/automations", null, HttpStatus.OK)).values().map { it["name"].asString() }
        assertEquals(listOf("aut-12-issue-certificate"), automations)
    }
}
