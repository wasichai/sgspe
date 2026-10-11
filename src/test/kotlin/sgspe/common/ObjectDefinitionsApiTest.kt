package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// plan corto de wasichai 1.1.0: wasichai guarda las definiciones y las borra al cambiar el modelo (ADR-066 de wasichai), y
// ObjectDefinitions solo las memoriza por petición. un campo agregado por /api/metadata se ve en la petición siguiente,
// sin esperar ningún ttl de sgspe
class ObjectDefinitionsApiTest : F3ApiTest() {
    @Autowired
    private lateinit var definitions: ObjectDefinitions

    @Test
    fun `un campo agregado se ve en la peticion siguiente`() {
        val name = uniqueName("medicion_tmp").replace('-', '_')
        createObject(name, listOf(field("codigo", "TEXT")))
        val organization = organizationId()
        // calienta la caché de wasichai
        runBlocking { definitions.current(organization, name) }
        send("POST", "/api/metadata/objects/$name/fields", field("nota", "TEXT"), HttpStatus.CREATED)
        val fields = runBlocking { definitions.current(organization, name) }.fields.map { it.name }
        assertTrue("nota" in fields, fields.toString())
    }
}
