package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import wasichai.core.common.UnauthorizedException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// una automatización de wasichai corre sin sesión: leer el origen de la tarea que crea (AUT-01, AUT-02, AUT-09, AUT-10,
// AUT-11) lanza «Authentication required» y la automatización quedaría FAILED. sin sesión la regla no se juzga (la tarea
// copia el proyecto de su origen); la plataforma (asPlatform) sí lee y se juzga, y quien escribe sin poder leer sigue
// recibiendo OBJECT_NOT_READABLE
class ProjectLinkGuardPlatformTest {
    private val view = mock(WriteView::class.java) { throw UnauthorizedException("Authentication required") }
    private val guard = ProjectLinkGuard(mock(Records::class.java), view, ModelCatalog.fromClasspath())

    @Test
    fun `una tarea que crea una automatizacion, sin sesion, no se juzga`() {
        val change =
            RecordWrite(
                organizationId = UUID.randomUUID(),
                userId = null,
                objectId = UUID.randomUUID(),
                objectName = "task",
                recordId = UUID.randomUUID(),
                kind = RecordChangeKind.CREATED,
                attributes = mapOf("project" to UUID.randomUUID().toString(), "grievance" to UUID.randomUUID().toString())
            )
        assertEquals(emptyList<Finding>(), runBlocking { guard.findings(change, dryRun = false) })
    }
}
