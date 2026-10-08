package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import sgspe.common.Callers
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// la rama de EditAnyGuard que no necesita base: con userId la escritura es de una persona, y si Callers no la puede
// identificar se niega (falla cerrado); solo userId == null (la plataforma, una automatización) no se juzga. Callers
// sin respuesta devuelve null, que es «no hay quien llame»; Permissions, Records y WriteView no se tocan en esta rama
class EditAnyGuardTest {
    private val guard =
        EditAnyGuard(
            ModelCatalog.fromClasspath(),
            mock(Callers::class.java),
            mock(Permissions::class.java),
            mock(Records::class.java),
            mock(WriteView::class.java)
        )

    private fun write(
        kind: RecordChangeKind,
        userId: UUID?,
        objectName: String = "engagement"
    ) = RecordWrite(
        organizationId = UUID.randomUUID(),
        userId = userId,
        objectId = UUID.randomUUID(),
        objectName = objectName,
        recordId = UUID.randomUUID(),
        kind = kind,
        before = mapOf("title" to "Reunión"),
        attributes = mapOf("title" to "Otra")
    )

    @Test
    fun `con userId y sin quien llama se niega con NOT_OWN_RECORD`() {
        for (kind in listOf(RecordChangeKind.UPDATED, RecordChangeKind.DELETED, RecordChangeKind.TRANSITIONED)) {
            val findings = runBlocking { guard.findings(write(kind, UUID.randomUUID()), dryRun = false) }
            assertEquals(listOf("NOT_OWN_RECORD"), findings.map { it.code }, "$kind")
            assertEquals(HttpStatus.FORBIDDEN, findings.single().status)
        }
    }

    @Test
    fun `sin userId (la plataforma o una automatizacion), un alta o un objeto sin EDIT_ANY no se juzga`() {
        assertTrue(runBlocking { guard.findings(write(RecordChangeKind.UPDATED, userId = null), dryRun = false) }.isEmpty())
        assertTrue(runBlocking { guard.findings(write(RecordChangeKind.CREATED, UUID.randomUUID()), dryRun = false) }.isEmpty())
        assertTrue(runBlocking { guard.findings(write(RecordChangeKind.UPDATED, UUID.randomUUID(), "organization"), dryRun = false) }.isEmpty())
    }
}
