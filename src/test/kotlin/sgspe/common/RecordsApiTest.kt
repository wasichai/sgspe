package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

// Records escribe con la marca SgspeWrite y con su motivo; la API genérica de wasichai, sin la marca. una guarda de
// prueba (solo en el contexto de esta clase) anota lo que ve, como harán las guardas de F2
class RecordsApiTest : SgspeApiTest() {
    @TestConfiguration
    class Marks {
        @Bean
        fun marks(): RecordWriteGuard =
            object : RecordWriteGuard {
                override suspend fun beforeWrite(change: RecordWrite) {
                    SEEN.add(change.objectName to (currentCoroutineContext()[SgspeWrite.Key] != null))
                }
            }
    }

    @Autowired
    private lateinit var records: Records

    @Autowired
    private lateinit var recordService: RecordService

    @Test
    fun `una escritura de Records lleva la marca y su motivo, y una de la api generica no lleva la marca`() {
        val name = uniqueName("marcado")
        createObject(name, listOf(field("code", "TEXT")))
        val organization = UUID.fromString(me()["organizationId"].asString())

        // como un trabajo de fondo: la plataforma, sin petición (WAS-ADR-0039)
        val created = runBlocking { recordService.asPlatform(organization) { records.create(name, mapOf("code" to "M-1"), "alta de la prueba") } }
        createRecord(name, mapOf("code" to "G-1"))

        assertEquals(listOf(name to true, name to false), SEEN.filter { it.first == name })
        val history = tree(send("GET", "/api/objects/$name/records/${created.id}/history", null, HttpStatus.OK))
        assertEquals("alta de la prueba", history.values().single()["reason"].asString())
    }

    companion object {
        val SEEN = ConcurrentLinkedQueue<Pair<String, Boolean>>()
    }
}
