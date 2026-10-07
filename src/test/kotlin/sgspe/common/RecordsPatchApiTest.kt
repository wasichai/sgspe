package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.SgspeApiTest
import java.util.UUID

// Records.patch cambia solo lo que nombra: el PUT de wasichai reemplaza el registro, así que manda lo guardado más los
// cambios. find devuelve null para lo que no existe. como un trabajo de fondo, dentro de asPlatform
class RecordsPatchApiTest : SgspeApiTest() {
    @Autowired
    private lateinit var records: Records

    @Test
    fun `patch no borra los campos que no nombra y deja el motivo`() {
        val name = uniqueName("parchado")
        createObject(name, listOf(field("code", "TEXT"), field("note", "TEXT"), field("visits", "INTEGER")))
        val organization = UUID.fromString(me()["organizationId"].asString())
        val id = createRecord(name, mapOf("code" to "P-1", "note" to "se queda", "visits" to 3))
        runBlocking {
            records.asPlatform(organization) {
                records.patch(name, UUID.fromString(id), mapOf("visits" to 4), "derivado: visitas")
                assertNull(records.find(name, UUID.randomUUID()))
            }
        }
        val attributes = tree(send("GET", "/api/objects/$name/records/$id", null, org.springframework.http.HttpStatus.OK))["attributes"]
        assertEquals("se queda", attributes["note"].asString())
        assertEquals(4, attributes["visits"].asInt())
        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, org.springframework.http.HttpStatus.OK))
        assertEquals("derivado: visitas", history.values().first()["reason"].asString())
    }
}
