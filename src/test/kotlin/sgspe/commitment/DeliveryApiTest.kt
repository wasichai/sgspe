package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.IdempotencyKey
import java.math.BigDecimal
import java.util.UUID

// las entregas de un compromiso (RF-COM-008, api.md §4.5, ASM-063): solo capturan datos y recalculan executed_amount. el
// «Acta de entrega» (AUT-03) espera a UPS-23: hasta entonces el acta firmada se sube como evidencia ACTA DE ENTREGA
class DeliveryApiTest : F3DomainApiTest() {
    private fun deliver(
        commitment: String,
        attributes: Map<String, Any?>,
        evidence: List<String> = emptyList(),
        headers: Map<String, String> = withReason(),
        token: String = this.token
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/commitments/$commitment/deliveries",
            mapOf("attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true) + attributes, "evidence" to evidence),
            token,
            headers
        )

    private fun deliveries(commitment: String): Int =
        tree(send("GET", "/api/objects/delivery/records?commitment=$commitment", null, HttpStatus.OK))["totalElements"].asInt()

    @Test
    fun `registrar una entrega no cambia el estado, vincula sus evidencias y suma el ejecutado`() {
        val project = testProject()
        val id = approvedCommitment(project)
        val acta = evidence(project, "ACTA DE ENTREGA")
        val first = deliver(id, mapOf("delivered_amount" to 400), listOf(acta))
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        assertEquals("in_progress", tree(first.body)["commitment"]["state"].asString())
        deliver(id, mapOf("delivered_amount" to "250.5"))
        assertEquals(0, BigDecimal("650.5").compareTo(record("commitment", id)["attributes"]["executed_amount"].decimalValue()))
        val delivery = tree(first.body)["delivery"]["id"].asString()
        assertEquals(
            acta,
            tree(send("GET", "/api/objects/delivery/records/$delivery/related/evidence_delivery", null, HttpStatus.OK))["content"][0]["id"].asString()
        )
    }

    @Test
    fun `un monto negativo o enorme, un atributo desconocido o el compromiso en draft no crean nada`() {
        val project = testProject()
        val id = approvedCommitment(project)
        assertEquals("OUT_OF_RANGE", code(deliver(id, mapOf("delivered_amount" to "1e999999"))))
        assertEquals("OUT_OF_RANGE", code(deliver(id, mapOf("delivered_amount" to -5))))
        assertEquals(HttpStatus.BAD_REQUEST, deliver(id, mapOf("commitment" to id)).status)
        assertEquals(HttpStatus.BAD_REQUEST, deliver(id, emptyMap(), headers = emptyMap()).status)
        val draft = commitment(project)
        assertEquals("STATE_MISMATCH", code(deliver(draft, emptyMap())))
        assertEquals(0, deliveries(id))
        assertEquals(0, deliveries(draft))
    }

    @Test
    fun `una entrega se corrige con If-Match y recalcula el ejecutado, pero no la de un compromiso cumplido`() {
        val project = testProject()
        val id = approvedCommitment(project)
        val delivery = tree(deliver(id, mapOf("delivered_amount" to 100, "conformity_by" to "Junta directiva")).body)["delivery"]["id"].asString()
        val fixed =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("delivered_amount" to 120)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.OK, fixed.status, fixed.body)
        assertEquals(0, BigDecimal("120").compareTo(tree(fixed.body)["commitment"]["attributes"]["executed_amount"].decimalValue()))
        val acta = evidence(project, "ACTA DE ENTREGA")
        val foto = evidence(project, "FOTO", "PUBLICA")
        for (e in listOf(acta, foto)) {
            assertEquals(
                HttpStatus.NO_CONTENT,
                exchange("POST", "/api/sgspe/evidence/$e/links", mapOf("object" to "delivery", "recordId" to delivery), token).status
            )
        }
        move("commitment", id, "register_fulfillment", reason = "entregado")
        val frozen =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("delivered_amount" to 130)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.CONFLICT, frozen.status, frozen.body)
        assertEquals("DELIVERY_FROZEN", code(frozen))
        val unlink = exchange("DELETE", "/api/sgspe/evidence/$foto/links/delivery/$delivery", null, token)
        assertEquals("DELIVERY_FROZEN", code(unlink))
    }

    // DELIVERY_FROZEN mira el compromiso de antes de la entrega: la corrección nunca la cambia de compromiso, que podría sacarla
    // de uno cumplido o meterla en otro ya cumplido
    @Test
    fun `la correccion no cambia el compromiso de una entrega`() {
        val project = testProject()
        val id = approvedCommitment(project)
        val other = approvedCommitment(project)
        val delivery = tree(deliver(id, mapOf("delivered_amount" to 100)).body)["delivery"]["id"].asString()
        val moved =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("commitment" to other)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.BAD_REQUEST, moved.status, moved.body)
        assertTrue("attributes.commitment" in problemFields(moved.body), moved.body)
        assertEquals(id, record("delivery", delivery)["attributes"]["commitment"].asString())
        assertEquals(0, deliveries(other))
    }

    // la entrega escribe dos registros (y sus vínculos): si el comando falla después de la primera escritura, no queda ni la
    // entrega ni la clave (Tarea 3: la clave, el comando y la respuesta se confirman o se revierten juntos)
    @Test
    fun `una entrega que falla despues de crear el registro no deja la entrega ni la clave`() {
        val project = testProject()
        val id = approvedCommitment(project)
        val key = mapOf(IdempotencyKey.HEADER to UUID.randomUUID().toString())
        // la evidencia no existe: el vínculo falla después de crear la delivery
        val missing = listOf(UUID.randomUUID().toString())
        val failed = deliver(id, mapOf("delivered_amount" to 100), missing, withReason() + key)
        assertTrue(failed.status.is4xxClientError, "${failed.status}: ${failed.body}")
        assertEquals(0, deliveries(id))
        assertTrue(record("commitment", id)["attributes"]["executed_amount"].let { it == null || it.isNull || it.decimalValue().signum() == 0 })
        // la misma petición con la misma clave corre otra vez (no hay respuesta guardada que reenviar)
        val again = deliver(id, mapOf("delivered_amount" to 100), missing, withReason() + key)
        assertEquals(failed.status, again.status, again.body)
        assertNull(again.headers.getFirst(IdempotencyKey.REPLAYED))
        // y la clave queda libre para la petición corregida: no es un 422 IDEMPOTENCY_KEY_REUSED
        val fixed = deliver(id, mapOf("delivered_amount" to 100), headers = withReason() + key)
        assertEquals(HttpStatus.CREATED, fixed.status, fixed.body)
        assertEquals(1, deliveries(id))
        val replay = deliver(id, mapOf("delivered_amount" to 100), headers = withReason() + key)
        assertEquals(fixed.body, replay.body)
        assertEquals("true", replay.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals(1, deliveries(id))
    }

    // recipients y conformity_by son restringidos (roles.json): el comando responde como lee quien llama, y quien no está entre
    // sus lectores no los ve en la entrega
    @Test
    fun `los campos restringidos de la entrega solo los ven sus lectores`() {
        val project = testProject()
        val manager = member("COMMITMENT_MANAGER", project)
        val id = approvedCommitment(project, owner = manager)
        val created =
            deliver(
                id,
                mapOf("delivered_amount" to 50, "recipients" to "Asociación Ejemplo", "conformity_by" to "Persona Ejemplo"),
                token = manager.token
            )
        assertEquals(HttpStatus.CREATED, created.status, created.body)
        val delivery = tree(created.body)["delivery"]
        assertEquals("Asociación Ejemplo", delivery["attributes"]["recipients"].asString())
        val approver = member("APPROVER", project)
        val read = record("delivery", delivery["id"].asString(), approver.token)["attributes"]
        assertFalse(read.has("recipients"), read.toString())
        assertFalse(read.has("conformity_by"), read.toString())
        assertNotEquals(null, read["delivered_amount"])
    }
}
