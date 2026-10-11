package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import sgspe.F4ApiTest
import wasichai.core.identity.AuthenticatedUser

// RequestMemoFilter (especificación 4.4 §3.10, Tarea 17 del plan 4.4a): dentro de una petición de /api, la exención del alcance
// (ProjectReach.readsAll, la acción READ_ALL_PROJECTS sobre project) se pregunta una sola vez aunque wasichai pida el
// RecordReadScope para cada objeto y lectura, y otra petición vuelve a preguntar. en lugar de contar consultas en el log de SQL
// (frágil: depende del formato del log, de la caché de definiciones de wasichai y de lo que corra en paralelo) se espía el bean
// Permissions y se cuentan sus preguntas READ_ALL_PROJECTS de la persona de la prueba: es exactamente lo que el memo ahorra
class RequestMemoFilterApiTest : F4ApiTest() {
    @MockitoSpyBean
    private lateinit var permissions: Permissions

    // las preguntas READ_ALL_PROJECTS de `account` desde la última limpieza
    private fun questions(account: Account): Int =
        Mockito.mockingDetails(permissions).invocations.count { call ->
            call.method.name == "has" &&
                (call.arguments[0] as? AuthenticatedUser)?.userId?.toString() == account.id &&
                call.arguments[1] == ProjectReach.READ_ALL_PROJECTS
        }

    @Test
    fun `una lista y un indicador preguntan READ_ALL_PROJECTS una vez por peticion`() {
        val project = testProject()
        stakeholder(project)
        stakeholder(project)
        val analyst = member("ANALYST", project)
        val reads =
            listOf(
                "/api/objects/stakeholder_assessment/records?size=50",
                "/api/objects/stakeholder/records?size=50",
                // el indicador lee varios objetos como quien llama: sin el memo, una pregunta por cada uno
                "/api/sgspe/dashboards/indicators/KPI-STK-01?project=$project"
            )
        for (path in reads) {
            Mockito.clearInvocations(permissions)
            send("GET", path, null, HttpStatus.OK, analyst.token)
            assertEquals(1, questions(analyst), path)
        }
        // el memo no pasa de una petición a otra: un permiso cambiado vale desde la siguiente
        Mockito.clearInvocations(permissions)
        repeat(2) { send("GET", reads.first(), null, HttpStatus.OK, analyst.token) }
        assertEquals(2, questions(analyst))
    }
}
