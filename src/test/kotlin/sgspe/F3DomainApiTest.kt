package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus

// la base de las pruebas de integración del plan 3.2 (F3) sobre F3ApiTest del plan 3.1, que ya da los compromisos con D1, D2
// y un solicitante, los acuerdos, las tareas, las altas como la plataforma y la espera de las automatizaciones; F2ApiTest da
// la subida para un campo FILE (stagedFile) y DomainApiTest, el motivo, If-Match y el código de un error. aquí solo lo que
// suman los comandos de 3.2: evidencias registradas con POST /api/sgspe/evidence y compromisos en draft o aprobados con un
// responsable y un verificador que no los mueven
abstract class F3DomainApiTest : F3ApiTest() {
    // el verificador de los compromisos de la prueba: D11 solo mira que quien verifica no sea el responsable
    private val verifier: Account by lazy { userWithRole("COMMITMENT_MANAGER") }

    // ---- evidencias ----

    protected fun createEvidence(
        attributes: Map<String, Any?>,
        links: List<Map<String, String>> = emptyList(),
        token: String = this.token,
        headers: Map<String, String> = emptyMap()
    ): Response = exchange("POST", "/api/sgspe/evidence", mapOf("attributes" to attributes, "links" to links), token, headers)

    // una evidencia subida y registrada por `account` (el admin si es null); su id
    protected fun evidence(
        project: String?,
        type: String = "ACTA DE ENTREGA",
        classification: String = "INTERNA",
        account: Account? = null,
        links: List<Map<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap()
    ): String {
        val attributes =
            mapOf("file" to stagedFile("evidence", "file", account), "evidence_type" to type, "classification" to classification) +
                listOfNotNull(project?.let { "project" to it })
        val response = createEvidence(attributes, links, account?.token ?: token, headers)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["id"].asString()
    }

    // ---- compromisos ----

    // un compromiso en draft de F3ApiTest.commitment (D1, D2 y un solicitante), creado por `creator` (el admin si es null),
    // con un responsable nuevo si no se da
    protected fun commitment(
        project: String,
        attributes: Map<String, Any?> = emptyMap(),
        creator: Account? = null,
        owner: Account = userWithRole("COMMITMENT_MANAGER")
    ): String = commitment(project, owner, verifier, creator, attributes)

    // un compromiso aprobado por un APPROVER asignado (no quien lo registró, D4) y, si `start`, en ejecución
    protected fun approvedCommitment(
        project: String,
        owner: Account = userWithRole("COMMITMENT_MANAGER"),
        attributes: Map<String, Any?> = emptyMap(),
        start: Boolean = true
    ): String {
        val id = commitment(project, attributes, owner = owner)
        move("commitment", id, "submit", reason = "envío")
        move("commitment", id, "approve", member("APPROVER", project).token, "aprobación")
        if (start) move("commitment", id, "start", reason = "inicio")
        return id
    }
}
