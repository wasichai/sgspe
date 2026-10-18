package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.Records
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import wasichai.files.StoredFileRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ASM-060 (gis.md §4.5): la evidencia va en la transacción de los registros. una falla del alta de la evidencia, después del
// stage y de escribir los componentes, revierte todo: no queda ni componente ni evidencia, y el archivo subido queda sin
// adjuntar (lo borra StoredFileCleanup pasado wasichai.files.cleanup.delay)
class ImportEvidenceRollbackApiTest : DomainApiTest() {
    // rechaza el alta de la evidencia de los lotes marcados y anota el archivo que traía
    class FailEvidence : RecordWriteGuard {
        val batches: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val files = ConcurrentHashMap<String, String>()

        override suspend fun beforeWrite(change: RecordWrite) {
            if (change.objectName != "evidence" || change.kind != RecordChangeKind.CREATED) return
            val batch = change.attributes.orEmpty()["import_batch_id"]?.toString() ?: return
            if (batch !in batches) return
            files[batch] = Records.uuid(change.attributes.orEmpty()["file"]).toString()
            throw ValidationException("Falla de prueba", "evidence", "reversión después del stage")
        }
    }

    @TestConfiguration
    class Failing {
        @Bean
        fun failEvidence() = FailEvidence()
    }

    @Autowired
    private lateinit var guard: FailEvidence

    @Autowired
    private lateinit var storedFiles: StoredFileRepository

    @Test
    fun `una falla despues del stage no deja registros ni evidencia y el archivo queda sin adjuntar`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val columns = """{"code": "Codigo", "easting": "Este", "northing": "Norte", "elevation": "Cota"}"""
        val file = "pozos.csv" to "Codigo;Este;Norte;Cota\nTST-REV-1;812345;9123456;3100".toByteArray()
        val fields = mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns)
        val preview = tree(multipart("/api/sgspe/territory/imports", mapOf("file" to file), fields, analyst.token).text)
        val batch = preview["importBatchId"].asString()
        guard.batches += batch
        val done =
            multipart(
                "/api/sgspe/territory/imports/$batch/confirm",
                mapOf("file" to file),
                fields + ("sha256" to preview["sha256"].asString()),
                analyst.token
            )
        assertEquals(HttpStatus.BAD_REQUEST, done.status, done.text)
        assertEquals(0, tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt())
        assertEquals(0, tree(send("GET", "/api/objects/evidence/records?import_batch_id=$batch", null, HttpStatus.OK))["content"].size())
        // el archivo se subió en su transacción propia y sigue ahí, sin ninguna evidencia que lo nombre
        val organization = UUID.fromString(me()["organizationId"].asString())
        val staged = runBlocking { storedFiles.find(organization, UUID.fromString(guard.files.getValue(batch))) }
        assertNotNull(staged)
        assertEquals(preview["sha256"].asString(), staged!!.sha256)
    }
}
