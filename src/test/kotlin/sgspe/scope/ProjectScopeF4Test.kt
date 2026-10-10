package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// el alcance de escritura de los objetos de F4 (security.md §9.2): las raíces por su proyecto, las revisiones y las medidas por
// su riesgo, y la regla de alerta, que puede ser del tenant, solo cuando trae proyecto
class ProjectScopeF4Test {
    private val project = UUID.randomUUID()
    private val risk = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()
    private val parentProject: suspend (String, UUID) -> UUID? = { _, id -> if (id == risk) project else null }

    private fun resolve(
        objectName: String,
        before: Map<String, Any?>
    ) = runBlocking {
        val write =
            RecordWrite(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectName,
                UUID.randomUUID(),
                RecordChangeKind.UPDATED,
                before,
                null,
                null,
                null
            )
        ProjectResolver.resolve(write, before, catalog::isRequiredRelation, parentProject)
    }

    @Test
    fun `cada objeto de F4 tiene alcance`() {
        for (name in ProjectLinkGuard.OBJECTS - setOf("risk_assessment", "mitigation_measure")) assertTrue(name in ProjectScope.ROOTS, name)
        for (name in listOf("risk_assessment", "mitigation_measure")) assertEquals("social_risk" to "social_risk", ProjectScope.CHILDREN[name], name)
    }

    @Test
    fun `una revision y una medida toman el proyecto de su riesgo`() {
        assertEquals(setOf(project), resolve("risk_assessment", mapOf("social_risk" to risk.toString())).projects)
        assertEquals(setOf(project), resolve("mitigation_measure", mapOf("social_risk" to risk.toString())).projects)
    }

    @Test
    fun `una regla del tenant no pide alcance, y una de proyecto si`() {
        val tenant = resolve("early_warning_rule", mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve("early_warning_rule", mapOf("project" to project.toString())).projects)
        // una queja guardada sin proyecto no tiene dueño: falla cerrada
        assertTrue(resolve("grievance", mapOf("project" to null)).unresolved)
    }
}
