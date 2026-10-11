package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import sgspe.F5ApiTest
import sgspe.common.Records

// el memo del comando (plan corto de la caché del esquema, Tarea 6): un comando de /api/sgspe lee las asignaciones de quien
// escribe una vez por proyecto aunque ProjectScopeGuard pregunte en el alta y en los dos extremos de cada vínculo, y otro
// comando vuelve a leerlas (nada pasa de una petición a otra). en lugar de contar consultas en el log de SQL se espía Records y
// se cuentan sus lecturas de project_assignment filtradas por quien escribe (Assignments.isAssigned), como
// RequestMemoFilterApiTest cuenta las preguntas de permisos
class AssignmentMemoApiTest : F5ApiTest() {
    @MockitoSpyBean
    private lateinit var records: Records

    // las lecturas de project_assignment de `account` desde la última limpieza
    private fun assignmentReads(account: Account): Int =
        Mockito.mockingDetails(records).invocations.count { call ->
            call.method.name == "all" &&
                call.arguments[0] == Assignments.OBJECT &&
                (call.arguments[1] as? Map<*, *>)?.get("member_user_id") == account.id
        }

    @Test
    fun `el alta de una entrega con tres evidencias lee las asignaciones de quien la captura una vez`() {
        val project = testProject()
        val manager = member("SOCIAL_INVESTMENT_MANAGER", project)
        val contribution = approvedContribution(project, manager, procure = true)
        val evidence = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE").map { evidence(project, it, account = manager) }
        val headers = ifMatch("social_contribution", contribution, manager.token) + withReason("entrega")
        val body =
            mapOf(
                "attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva de la escuela"),
                "evidence" to evidence
            )
        Mockito.clearInvocations(records)
        val captured = exchange("POST", "/api/sgspe/social-contributions/$contribution/deliveries", body, manager.token, headers)
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        assertEquals(1, assignmentReads(manager))
        // otro comando vuelve a leerlas: el memo dura lo que el comando
        val file = stagedFile("evidence", "file", manager)
        Mockito.clearInvocations(records)
        val other =
            createEvidence(mapOf("file" to file, "evidence_type" to "FOTO", "classification" to "INTERNA", "project" to project), token = manager.token)
        assertEquals(HttpStatus.CREATED, other.status, other.body)
        assertEquals(1, assignmentReads(manager))
    }
}
