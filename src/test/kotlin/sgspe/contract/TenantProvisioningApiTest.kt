package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import java.io.File

// security.md §4.1 y §14 «Alta de un tenant»: los scripts reales sobre un tenant nuevo, en orden, sin errores e
// idempotentes, con el modelo, los roles, los workflows, los tipos de documento y las automatizaciones de F7
class TenantProvisioningApiTest : SgspeApiTest() {
    @Test
    fun `un tenant nuevo se prepara con apply, apply_roles y apply workflows en ese orden`() {
        val admin = provisionTenant()

        // F7: 84 objetos, 39 acciones declaradas (13 EXPORT de las capas territoriales) y 276 relaciones; 25 objetos reciben
        // en la fase 3 los únicos y los índices que nombran el campo de una relación, y delivery.social_contribution su
        // único (Decisión 1 del plan 5.1: wasichai acepta el unique: true del campo de una relación opcional)
        val model = runScript(listOf("model/apply.py"), admin)
        assertEquals(0, model.exit, model.output)
        assertTrue(model.output.contains("done: 399 created, 26 updated, 0 skipped"), model.output)
        assertTrue(model.output.contains("update field delivery.social_contribution (unique)"), model.output)
        val roles = runScript(listOf("model/apply_roles.py"), admin)
        assertEquals(0, roles.exit, roles.output)
        assertTrue(roles.output.contains("done: 15 created, 0 updated, 0 skipped"), roles.output)
        val workflows = runScript(listOf("model/apply.py", "--workflows"), admin)
        assertEquals(0, workflows.exit, workflows.output)
        assertTrue(
            workflows.output.contains(
                "done: 18 workflows (18 created, 0 updated, 0 skipped), 2 document types (2 created, 0 updated, 0 skipped), " +
                    "6 automations (6 created, 0 updated, 0 skipped)"
            ),
            workflows.output
        )
        // los tres pasos son idempotentes contra wasichai de verdad (database-model.md §24)
        val modelAgain = runScript(listOf("model/apply.py"), admin)
        assertEquals(0, modelAgain.exit, modelAgain.output)
        assertTrue(modelAgain.output.contains("done: 0 created, 0 updated, 399 skipped"), modelAgain.output)
        val again = runScript(listOf("model/apply_roles.py"), admin)
        assertEquals(0, again.exit, again.output)
        assertTrue(again.output.contains("done: 0 created, 0 updated, 15 skipped"), again.output)
        val workflowsAgain = runScript(listOf("model/apply.py", "--workflows"), admin)
        assertEquals(0, workflowsAgain.exit, workflowsAgain.output)
        assertTrue(
            workflowsAgain.output.contains(
                "done: 18 workflows (0 created, 0 updated, 18 skipped), 2 document types (0 created, 0 updated, 2 skipped), " +
                    "6 automations (0 created, 0 updated, 6 skipped)"
            ),
            workflowsAgain.output
        )

        val declared =
            JSON
                .readTree(File("model/roles.json"))["roles"]
                .values()
                .map { it["name"].asString() }
                .toSet()
        val names = tree(send("GET", "/api/roles", null, HttpStatus.OK, admin.token)).values().map { it["name"].asString() }.toSet()
        assertEquals(declared + "ADMIN", names)
    }
}
