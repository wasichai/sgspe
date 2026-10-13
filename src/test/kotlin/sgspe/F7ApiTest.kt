package sgspe

import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.reactive.function.BodyInserters
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import wasichai.core.data.RecordResponse
import java.util.UUID

// la base de las pruebas de integración de F7 (plan 7.1; los planes 7.2a, 7.2b y 7.2c la usan): lo de F6ApiTest más las
// salidas de la IA como las escribirá sgspe.ai, como la plataforma, porque los tres objetos son apiOnly y sus escritores
// (Interactions, SuggestionWriter) son de 7.2a y 7.2b: una interacción por llamada, una sugerencia que cuelga de ella y un
// candidato de IA con su cita seudonimizada. los textos llevan solo marcadores ([PERSONA:<id>], [PERSONA]) y datos
// ficticios (RQ-SEC-018). las ayudas son bloqueantes, como las de F5ApiTest
abstract class F7ApiTest : F6ApiTest() {
    // una ai_interaction de `useCase` como la registrará Interactions (plan 7.2a): del proyecto `project` (del tenant si es
    // null), pedida por `requester` (sin nadie si es null, como JOB-07; run_by_platform lo escribe 7.2a)
    protected fun aiInteraction(
        project: String?,
        useCase: String = "AI-03",
        requester: Account? = null
    ): String =
        platformCreate(
            "ai_interaction",
            mapOf(
                "project" to project,
                "use_case" to useCase,
                "provider" to "anthropic",
                "model" to AI_MODEL,
                "prompt_version" to "${useCase.lowercase()}@1",
                "started_at" to "2026-10-13T15:00:00Z",
                "input_reference" to "evidence:00000000-0000-0000-0000-000000000000",
                "output_summary" to "Dos posibles compromisos con cita.",
                "input_tokens" to 1200,
                "output_tokens" to 300,
                "latency_ms" to 2400,
                "requested_by_user_id" to requester?.id?.ifBlank { null },
                "requested_by_user_email" to requester?.email
            ).filterValues { it != null }
        )

    // una ai_suggestion en proposed (WF-K) de `kind` del catálogo «Tipo de sugerencia de IA», con su interacción de AI-08 del
    // mismo proyecto; `target` es (objeto, id) del registro al que se refiere, si lo hay. la cita lleva un marcador sin id
    protected fun aiSuggestion(
        project: String?,
        kind: String = "CLASIFICACION",
        target: Pair<String, String>? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        platformCreate(
            "ai_suggestion",
            mapOf(
                "project" to project,
                "suggestion_kind" to kind,
                "target_object" to target?.first,
                "target_record_id" to target?.second,
                "suggested_value" to "AMBIENTAL",
                "evidence_quote" to "[PERSONA] dijo que el agua del canal llega turbia",
                "confidence" to 0.82,
                "model" to AI_MODEL,
                "ai_interaction" to aiInteraction(project, "AI-08")
            ).filterValues { it != null } + attributes
        )

    // un commitment_candidate de la IA en proposed, como lo guardará SuggestionWriter (plan 7.2b): ai_generated, confianza,
    // cita seudonimizada, su interacción y, si se dan, la evidencia y la interacción de origen; suggested_owner_user_email
    // vacío (el responsable lo elige quien acepta)
    protected fun aiCandidate(
        project: String,
        sourceEvidence: String? = null,
        engagement: String? = null,
        interaction: String = aiInteraction(project)
    ): String =
        platformCreate(
            "commitment_candidate",
            mapOf(
                "project" to project,
                "proposed_text" to "Reparar el canal de riego de la Comunidad Ejemplo antes de la siembra",
                "evidence_quote" to "[PERSONA:00000000-0000-0000-0000-000000000001] pidió reparar el canal",
                "beneficiaries_text" to "[PERSONA] y las familias del sector",
                "confidence" to 0.74,
                "ai_generated" to true,
                "ai_interaction" to interaction,
                "source_evidence" to sourceEvidence,
                "engagement" to engagement
            ).filterValues { it != null }
        )

    // un cambio y una baja como la plataforma, con la marca SgspeWrite: así escribirán los comandos de sgspe.ai
    protected fun platformUpdate(
        objectName: String,
        id: String,
        attributes: Map<String, Any?>
    ): RecordResponse =
        runBlocking {
            platformRecords.asPlatform(organizationId()) {
                platformRecords.patch(objectName, UUID.fromString(id), attributes, "cambio de prueba como la plataforma")
            }
        }

    protected fun platformDelete(
        objectName: String,
        id: String
    ) {
        runBlocking {
            platformRecords.asPlatform(organizationId()) { platformRecords.delete(objectName, UUID.fromString(id), "baja de prueba como la plataforma") }
        }
    }

    @Autowired
    private lateinit var jwtDecoder: ReactiveJwtDecoder

    // un bloque como `account` fuera de una petición http: el contexto de seguridad de Reactor que llevaría su petición, con su
    // token decodificado (como asCaller de AgentEmbabelTest de wasichai 1.2.0). así se llama a un servicio de sgspe.ai como la
    // persona (plan 7.2a)
    protected fun <T> asUser(
        account: Account,
        block: suspend () -> T
    ): T {
        val jwt = jwtDecoder.decode(account.token.removePrefix("Bearer ")).block()!!
        return runBlocking(ReactiveSecurityContextHolder.withAuthentication(JwtAuthenticationToken(jwt)).asCoroutineContext()) { block() }
    }

    // ---- plan 7.2b: corridas y aceptaciones ----

    // una evidencia con el archivo `bytes` (un PDF o un DOCX de TestDocuments), subida y registrada por `account` (el admin si es
    // null), con sus vínculos ({object, recordId}); su id. wasichai-files reconoce el tipo por los primeros bytes
    protected fun textEvidence(
        project: String?,
        bytes: ByteArray,
        fileName: String,
        type: String = "ACTA",
        classification: String = "INTERNA",
        account: Account? = null,
        links: List<Map<String, String>> = emptyList()
    ): String {
        val parts = MultipartBodyBuilder().apply { part("file", bytes).filename(fileName) }.build()
        val staged =
            client
                .post()
                .uri("/api/objects/evidence/files/file")
                .header(HttpHeaders.AUTHORIZATION, account?.token ?: token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        val attributes =
            mapOf("file" to tree(staged)["id"].asString(), "evidence_type" to type, "classification" to classification) +
                listOfNotNull(project?.let { "project" to it })
        val response = createEvidence(attributes, links, account?.token ?: token)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["id"].asString()
    }

    // POST /api/sgspe/ai/runs como `account`, con Idempotency-Key si se da
    protected fun aiRun(
        account: Account,
        useCase: String,
        objectName: String,
        recordId: String,
        key: String? = null
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/ai/runs",
            mapOf("useCase" to useCase, "target" to mapOf("object" to objectName, "recordId" to recordId)),
            account.token,
            key?.let { mapOf(IdempotencyKey.HEADER to it) }.orEmpty()
        )

    // POST /api/sgspe/ai-suggestions/{id}/accept como `account`, con el If-Match de la sugerencia leída y el motivo
    protected fun acceptSuggestion(
        account: Account,
        id: String,
        attributes: Map<String, Any?>,
        reason: String? = "revisada en la bandeja",
        headers: Map<String, String> = emptyMap()
    ): Response {
        val etag = mapOf(IfMatch.HEADER to "\"${record("ai_suggestion", id)["updatedAt"].asString()}\"")
        val motive = reason?.let { withReason(it) }.orEmpty()
        return exchange("POST", "/api/sgspe/ai-suggestions/$id/accept", mapOf("attributes" to attributes), account.token, etag + motive + headers)
    }

    // una cuenta de servicio con `role`, asignada al proyecto de la prueba (como EarlyWarningWorkflowApiTest)
    protected fun serviceAccount(role: String): Account {
        val created = tree(send("POST", "/api/service-accounts", mapOf("name" to uniqueName("cuenta-"), "roles" to listOf(role)), HttpStatus.CREATED))
        val issued =
            tree(
                send(
                    "POST",
                    "/api/auth/token",
                    mapOf("clientId" to created["clientId"].asString(), "clientSecret" to created["clientSecret"].asString()),
                    HttpStatus.OK,
                    token = null
                )
            )
        val bearer = "Bearer ${issued["token"].asString()}"
        val me = me(bearer)
        val account = Account(bearer, me["email"].asString(), me["userId"].asString())
        assign(account, testProject(), role)
        return account
    }

    companion object {
        const val AI_MODEL = "modelo-de-prueba"
    }
}
