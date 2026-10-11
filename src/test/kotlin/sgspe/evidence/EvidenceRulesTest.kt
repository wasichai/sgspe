package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// la evidencia inmutable y su clasificación (RF-DOC-001, RF-DOC-003, ASM-071), sin base
class EvidenceRulesTest {
    private val descriptor = mapOf("id" to "11111111-1111-1111-1111-111111111111", "name" to "acta.pdf", "sha256" to "a".repeat(64))

    @Test
    fun `el id del archivo sale del descriptor que da una lectura o del id que manda un alta`() {
        assertEquals("11111111-1111-1111-1111-111111111111", EvidenceRules.fileId(descriptor))
        assertEquals("11111111-1111-1111-1111-111111111111", EvidenceRules.fileId("11111111-1111-1111-1111-111111111111"))
        assertNull(EvidenceRules.fileId(null))
    }

    @Test
    fun `reenviar el descriptor tal como se leyo no cambia el archivo, otro id, otra version o otra importacion si`() {
        val before = mapOf("file" to descriptor, "replaces" to null, "import_batch_id" to null, "title" to "Acta")
        assertEquals(emptyList<String>(), EvidenceRules.changedImmutable(before, before + ("title" to "Acta firmada")))
        assertEquals(emptyList<String>(), EvidenceRules.changedImmutable(before, before + ("file" to descriptor["id"])))
        assertEquals(listOf("file"), EvidenceRules.changedImmutable(before, before + ("file" to "22222222-2222-2222-2222-222222222222")))
        assertEquals(listOf("replaces", "import_batch_id"), EvidenceRules.changedImmutable(before, before + ("replaces" to "x") + ("import_batch_id" to "y")))
    }

    @Test
    fun `bajar la clasificacion es pasar de restringida a no restringida`() {
        assertTrue(EvidenceRules.downgrade("CONFIDENCIAL", "INTERNA"))
        assertTrue(EvidenceRules.downgrade("DATOS PERSONALES", "PUBLICA"))
        assertFalse(EvidenceRules.downgrade("DATOS PERSONALES", "CONFIDENCIAL"))
        assertFalse(EvidenceRules.downgrade("INTERNA", "PUBLICA"))
        assertFalse(EvidenceRules.downgrade("INTERNA", "CONFIDENCIAL"))
    }

    // la descarga falla cerrado: una clasificación que quien llama no lee (el permiso de campo la quita del registro) o que
    // falta se trata como restringida
    @Test
    fun `descargar exige DOWNLOAD_RESTRICTED con una clasificacion restringida, ausente o vacia`() {
        assertTrue(EvidenceRules.downloadRestricted(mapOf("classification" to "CONFIDENCIAL")))
        assertTrue(EvidenceRules.downloadRestricted(mapOf("classification" to "DATOS PERSONALES")))
        assertTrue(EvidenceRules.downloadRestricted(mapOf("title" to "Acta")))
        assertTrue(EvidenceRules.downloadRestricted(mapOf("classification" to null)))
        assertTrue(EvidenceRules.downloadRestricted(mapOf("classification" to " ")))
        assertFalse(EvidenceRules.downloadRestricted(mapOf("classification" to "INTERNA")))
        assertFalse(EvidenceRules.downloadRestricted(mapOf("classification" to "PUBLICA")))
    }

    // UPS-24: quien no descarga una evidencia restringida lee su descriptor sin el nombre original ni la huella
    @Test
    fun `el descriptor reservado conserva el id, el tipo y el tamano y quita el nombre y la huella`() {
        val full = descriptor + mapOf("contentType" to "application/pdf", "size" to 2048L)
        assertEquals(
            mapOf("id" to "11111111-1111-1111-1111-111111111111", "contentType" to "application/pdf", "size" to 2048L),
            EvidenceRules.reserved(full)
        )
        assertEquals(EvidenceRules.reserved(full), EvidenceRules.reserved(EvidenceRules.reserved(full)))
    }

    @Test
    fun `cada vinculo es evidence_ mas el objeto, y lo descriptivo nunca incluye el archivo`() {
        assertEquals("evidence_delivery", EvidenceRules.linkRelationship("delivery"))
        assertFalse(EvidenceRules.FILE in EvidenceRules.DESCRIPTIVE)
        assertTrue(EvidenceRules.CLASSIFICATION in EvidenceRules.DESCRIPTIVE)
    }

    // vincular una entrega toma el candado de su padre hasta el fin de la transacción (DELIVERY_FROZEN): commitment:<id> o, desde
    // F5, social_contribution:<id>. el alta vincula las entregas por la clave ascendente de ese candado y, dentro de una, por su
    // id, así dos altas con los mismos padres en orden inverso no se interbloquean (decisión 6 del plan 5.2a)
    @Test
    fun `el alta vincula las entregas por la clave del candado de su padre, despues de lo que no toma candado`() {
        val low = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val high = UUID.fromString("f0000000-0000-0000-0000-000000000000")
        val d1 = EvidenceLink("delivery", UUID.fromString("90000000-0000-0000-0000-000000000001"))
        val d2 = EvidenceLink("delivery", UUID.fromString("10000000-0000-0000-0000-000000000002"))
        val d3 = EvidenceLink("delivery", UUID.fromString("50000000-0000-0000-0000-000000000003"))
        val unknown = EvidenceLink("delivery", UUID.fromString("20000000-0000-0000-0000-000000000004"))
        val engagement = EvidenceLink("engagement", UUID.fromString("30000000-0000-0000-0000-000000000005"))
        val ofContribution = EvidenceLink("delivery", UUID.fromString("00000000-0000-0000-0000-000000000006"))
        val locks =
            mapOf(
                d1.recordId to "commitment:$low",
                d2.recordId to "commitment:$high",
                d3.recordId to "commitment:$low",
                ofContribution.recordId to "social_contribution:$low"
            )
        val expected = listOf(unknown, engagement, d3, d1, d2, ofContribution)
        assertEquals(expected, EvidenceRules.lockOrder(listOf(ofContribution, d2, unknown, d1, engagement, d3), locks))
        // el orden pedido no importa: la misma lista en orden inverso da el mismo orden de candados
        assertEquals(listOf(d3, d1, d2), EvidenceRules.lockOrder(listOf(d1, d3, d2), locks))
        assertEquals(EvidenceRules.lockOrder(listOf(d2, d1), locks), EvidenceRules.lockOrder(listOf(d1, d2), locks))
    }

    @Test
    fun `el candado de una entrega es el de su compromiso o el de su aporte`() {
        val id = UUID.randomUUID()
        assertEquals("commitment:$id", EvidenceRules.deliveryLock(mapOf("commitment" to id.toString())))
        assertEquals("social_contribution:$id", EvidenceRules.deliveryLock(mapOf("social_contribution" to id.toString())))
        assertEquals(null, EvidenceRules.deliveryLock(emptyMap()))
    }
}
