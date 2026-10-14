package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import java.util.UUID

// AUT-12 contra wasichai de verdad (workflows.md, decisión 8 de F8): al entrar en `issued`, la constancia de emisión con su
// correlativo CE, el código, el título, el tipo y la revisión vigente. La constancia no lleva la tabla de las versiones
// (Decisión 11 del plan 8.1, UPS-32). en 8.1 la emisión la hace la prueba como el gerente social, con la marca SgspeWrite que dejará pasar
// CommandTransitionGuard: el endpoint es del plan 8.2b, que mueve este caso a DeliverableIssueApiTest y emite por
// POST /api/sgspe/deliverables/{id}/issue
class IssueCertificateApiTest : F8ApiTest() {
    @Test
    fun `AUT-12 emite la constancia con correlativo y los datos del entregable`() {
        val id = approvedDeliverable(testProject())
        val base = deliverableVersion(id, 1, "INTERNO")
        deliverableVersion(id, 2, "EXTERNO", derivedFrom = base)
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
        // sin la tabla de las versiones: wasichai 1.2.0 no puede congelar un relatedTable cuyas filas traen un campo nulo
        // (RelatedSnapshot.rows con jackson-module-kotlin 3 falla con InvalidNullException) y deliverable_version siempre
        // los trae vacíos; UPS-32 pide el arreglo y entonces vuelve el relatedTable de deliverable_version_deliverable
        val values = certificate["snapshot"]["values"]
        assertEquals(record("deliverable", id)["attributes"]["document_code"].asString(), values["document_code"].asString(), "$certificate")
        assertEquals("0", values["current_revision"].asString(), "$certificate")
        assertTrue(certificate["snapshot"]["related"].isEmpty, "${certificate["snapshot"]["related"]}")
    }

    @Test
    fun `la constancia es el unico tipo de documento de los entregables`() {
        val types = tree(send("GET", "/api/objects/deliverable/document-types", null, HttpStatus.OK)).values()
        assertEquals(mapOf("deliverable-issue-certificate" to "CE"), types.associate { it["name"].asString() to it["prefix"].asString() })
        val automations = tree(send("GET", "/api/objects/deliverable/automations", null, HttpStatus.OK)).values().map { it["name"].asString() }
        assertEquals(listOf("aut-12-issue-certificate"), automations)
    }
}
