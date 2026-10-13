package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode

// la base de las pruebas de integración de F3 (plan 3.1; el plan 3.2 la usa para las suyas): lo de DomainApiTest más los
// registros de compromisos, acuerdos y tareas que ya cumplen las guardas de 3.2 (D1 y D2 de WF-D, la de AgreementWriteGuard
// para llegar a `active`, un único origen de la tarea), las altas de los objetos apiOnly como la plataforma
// (`platformCreate`, en `F2ApiTest` desde el plan 7.1) y la espera de una automatización, que corre fuera de la petición
// (WAS-ADR-0016)
abstract class F3ApiTest : DomainApiTest() {
    // los campos que WF-D exige para salir de draft y para aprobar (D1, D2), con un verificador distinto del responsable (D11)
    protected fun commitmentAttributes(
        project: String,
        owner: Account,
        verifier: Account
    ): Map<String, Any?> =
        mapOf(
            "project" to project,
            "code" to uniqueName("COM-"),
            "title" to "Mejora del canal de riego",
            "description" to "Mejora del canal de riego de la comunidad.",
            "objective" to "Asegurar el riego de la campaña agrícola.",
            "resource_type" to "MATERIAL",
            "quantity" to 120,
            "unit" to "metros lineales",
            "request_medium" to "ACUERDO EN REUNION",
            "agreed_date" to "2026-10-01",
            "due_date" to "2026-12-15",
            "amount" to 15000,
            "currency" to "PEN",
            "beneficiaries_count" to 40,
            "owner_user_id" to owner.id,
            "owner_user_email" to owner.email,
            "verifier_user_id" to verifier.id,
            "verifier_user_email" to verifier.email
        )

    // un compromiso en draft listo para enviar y aprobar: lo crea `creator` (el admin si es null) y lleva un grupo de interés
    // solicitante (commitment_stakeholder, D1). el vínculo lo hace el admin con motivo: commitment es requiresReason
    protected fun commitment(
        project: String,
        owner: Account,
        verifier: Account,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id =
            createRecord(
                "commitment",
                commitmentAttributes(project, owner, verifier) + attributes,
                mapOf(REASON to reason("alta de prueba")),
                creator?.token ?: token
            )
        val linked = linkWithReason("commitment", id, "commitment_stakeholder", stakeholder(project))
        assertEquals(HttpStatus.NO_CONTENT, linked.status, linked.body)
        return id
    }

    // un vínculo con la cabecera de motivo, que piden los extremos requiresReason (WAS-ADR-0041)
    protected fun linkWithReason(
        objectName: String,
        id: String,
        relationship: String,
        otherId: String,
        token: String = this.token
    ): Response =
        exchange(
            "POST",
            "/api/objects/$objectName/records/$id/related/$relationship",
            mapOf("otherId" to otherId),
            token,
            mapOf(REASON to reason("vínculo de prueba"))
        )

    // un acuerdo en draft que AgreementWriteGuard deja llegar a `active`: un CONVENIO firmado y vigente
    protected fun agreement(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "agreement",
            mapOf(
                "project" to project,
                "code" to uniqueName("ACU-"),
                "title" to "Convenio de cooperación",
                "agreement_type" to "CONVENIO",
                "signed_date" to "2026-09-30",
                "effective_date" to "2026-10-01",
                "expiry_date" to "2027-09-30"
            ) + attributes,
            mapOf(REASON to reason("alta de prueba")),
            creator?.token ?: token
        )

    // una tarea con un único origen (ExactlyOneGuard): una interacción del proyecto, que leen todos los roles que crean tareas
    protected fun task(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "task",
            mapOf("project" to project, "title" to "Coordinar la visita", "engagement" to engagement(project)) + attributes,
            token = creator?.token ?: token
        )

    // la ejecución de la automatización `name` para el registro `recordId`, cuando terminó (SUCCEEDED, FAILED o SKIPPED):
    // el drain de wasichai-automation la toma fuera de la petición, cada wasichai.automation.poll-interval (1 s)
    protected fun awaitRun(
        objectName: String,
        name: String,
        recordId: String,
        seconds: Long = 30
    ): JsonNode {
        val deadline = System.nanoTime() + seconds * 1_000_000_000
        while (System.nanoTime() < deadline) {
            val run =
                tree(send("GET", "/api/objects/$objectName/automations/$name/runs?limit=50", null, HttpStatus.OK))
                    .values()
                    .firstOrNull { it["recordId"]?.asString() == recordId && it["status"].asString() in FINISHED }
            if (run != null) return run
            Thread.sleep(250)
        }
        throw AssertionError("$name no terminó en $seconds s para $objectName $recordId")
    }

    companion object {
        private val FINISHED = setOf("SUCCEEDED", "FAILED", "SKIPPED")
    }
}
