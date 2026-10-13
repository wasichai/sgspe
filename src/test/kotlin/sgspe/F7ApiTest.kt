package sgspe

import kotlinx.coroutines.runBlocking
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
                "requested_by_user_id" to requester?.id,
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
            platformService.asPlatform(organizationId()) {
                platformRecords.update(objectName, UUID.fromString(id), attributes, "cambio de prueba como la plataforma")
            }
        }

    protected fun platformDelete(
        objectName: String,
        id: String
    ) {
        runBlocking {
            platformService.asPlatform(organizationId()) { platformRecords.delete(objectName, UUID.fromString(id), "baja de prueba como la plataforma") }
        }
    }

    companion object {
        const val AI_MODEL = "modelo-de-prueba"
    }
}
