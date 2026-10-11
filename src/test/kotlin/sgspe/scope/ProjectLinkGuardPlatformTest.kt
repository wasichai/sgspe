package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// una escritura sin usuario (una automatización de wasichai, que corre sin sesión, o la plataforma) no se juzga: la tarea de
// AUT-01, AUT-02, AUT-09, AUT-10 o AUT-11 copia el proyecto de su origen, y leer el origen sin sesión fallaría con
// «Authentication required», así que la automatización quedaría FAILED
class ProjectLinkGuardPlatformTest {
    private val records = mock(Records::class.java)
    private val view = mock(WriteView::class.java)
    private val guard = ProjectLinkGuard(records, view, ModelCatalog.fromClasspath())

    private fun task(userId: UUID?) =
        RecordWrite(
            organizationId = UUID.randomUUID(),
            userId = userId,
            objectId = UUID.randomUUID(),
            objectName = "task",
            recordId = UUID.randomUUID(),
            kind = RecordChangeKind.CREATED,
            attributes = mapOf("project" to UUID.randomUUID().toString(), "grievance" to UUID.randomUUID().toString())
        )

    @Test
    fun `una tarea que crea una automatizacion, sin usuario, no se juzga ni lee su origen`() {
        assertEquals(emptyList<Finding>(), runBlocking { guard.findings(task(null), dryRun = false) })
        verifyNoInteractions(records, view)
    }
}
