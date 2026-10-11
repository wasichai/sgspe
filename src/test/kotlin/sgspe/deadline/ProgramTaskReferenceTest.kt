package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog

// una tarea vinculada a un programa escala, en su nivel 1, a los roles de close de WF-G (workflows.md, «Plazos y
// escalamiento»; decisión 11 del plan 5.2b)
class ProgramTaskReferenceTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `una tarea de un programa escala a close de WF-G`() {
        val (objectName, transition) = DeadlineNoticePlan.TASK_REFERENCE.getValue("social_program")
        assertEquals("social_program", objectName)
        assertEquals("close", transition)
        assertNotNull(catalog.transition(objectName, transition))
        // el campo de la tarea por el que DeadlineNotices reconoce el origen existe en el modelo
        assertEquals("social_program", catalog.relationField("task", "social_program"))
    }
}
