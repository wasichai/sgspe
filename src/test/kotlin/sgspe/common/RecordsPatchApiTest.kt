package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import sgspe.F2ApiTest
import wasichai.core.common.ValidationException
import java.util.UUID

// Records.patch cambia solo lo que nombra: el PUT de wasichai reemplaza el registro, así que manda lo guardado más los
// cambios. find devuelve null para lo que no existe. como un trabajo de fondo, dentro de asPlatform
//
// como una persona: Records.patch rechaza, sin escribir nada, un cambio que nombra un campo que ella no puede escribir (uno
// restringido, uno de solo lectura de `u` o un nombre que el objeto no tiene) y deja intactos los que no nombra. la ruta de
// prueba solo existe en el contexto de esta clase y llama a Records con el usuario del token
class RecordsPatchApiTest : F2ApiTest() {
    @RestController
    class PatchEndpoint(
        private val records: Records
    ) {
        @PostMapping("/api/records-patch-test/{objectName}/{id}")
        suspend fun patch(
            @PathVariable objectName: String,
            @PathVariable id: UUID,
            @RequestBody changes: Map<String, Any?>
        ): Map<String, Any?> = records.patch(objectName, id, changes, "cambio de la prueba").attributes
    }

    @TestConfiguration
    class Endpoint {
        @Bean
        fun patchEndpoint(records: Records) = PatchEndpoint(records)
    }

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
        val attributes = tree(send("GET", "/api/objects/$name/records/$id", null, HttpStatus.OK))["attributes"]
        assertEquals("se queda", attributes["note"].asString())
        assertEquals(4, attributes["visits"].asInt())
        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, HttpStatus.OK))
        assertEquals("derivado: visitas", history.values().first()["reason"].asString())
    }

    // COMMUNITY_RELATIONS_OFFICER actualiza locality_profile pero no lee religion (restrictedFields): el cambio que nombra
    // religion es un 400 sobre el campo y no escribe nada; el que nombra otro campo se guarda y religion queda como estaba
    @Test
    fun `una persona no cambia un campo que no puede escribir, y lo que no nombra queda como estaba`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val profile = fixture("locality_profile", mapOf("religion" to "dato reservado", "water_source" to "pozo"))
        val path = "/api/records-patch-test/locality_profile/${profile.id}"

        fun stored() = tree(send("GET", "/api/objects/locality_profile/records/${profile.id}", null, HttpStatus.OK))["attributes"]

        val blocked = tree(send("POST", path, mapOf("water_source" to "río", "religion" to "otro"), HttpStatus.BAD_REQUEST, officer.token))
        assertEquals("religion", blocked["errors"].values().single()["field"].asString())
        assertEquals("pozo", stored()["water_source"].asString())
        assertEquals("dato reservado", stored()["religion"].asString())

        val unknown = tree(send("POST", path, mapOf("no_existe" to 1), HttpStatus.BAD_REQUEST, officer.token))
        assertEquals("no_existe", unknown["errors"].values().single()["field"].asString())

        send("POST", path, mapOf("water_source" to "río"), HttpStatus.OK, officer.token)
        assertEquals("río", stored()["water_source"].asString())
        assertEquals("dato reservado", stored()["religion"].asString())
    }

    // la plataforma escribe todos los campos, pero un nombre que el objeto no tiene tampoco se ignora en silencio
    @Test
    fun `la plataforma rechaza un nombre que el objeto no tiene`() {
        val name = uniqueName("parchado_pf")
        createObject(name, listOf(field("code", "TEXT")))
        val organization = UUID.fromString(me()["organizationId"].asString())
        val id = createRecord(name, mapOf("code" to "P-1"))
        val thrown =
            runBlocking {
                records.asPlatform(organization) {
                    runCatching { records.patch(name, UUID.fromString(id), mapOf("codigo" to "P-2"), "error de tipeo") }.exceptionOrNull()
                }
            }
        assertTrue(thrown is ValidationException, "$thrown")
        assertEquals("P-1", tree(send("GET", "/api/objects/$name/records/$id", null, HttpStatus.OK))["attributes"]["code"].asString())
    }
}
