package sgspe.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

// la configuración de la IA del despliegue (ai-architecture.md §6.2, §10, §11 y §12.1): las organizaciones con la IA activada
// por su slug (ASM-128), el proveedor que queda en ai_interaction (GAP-015), el modelo y la versión del prompt de cada caso por
// su clave en minúsculas, ai-01 a ai-08 (ASM-129, RQ-AI-006), y los límites de una corrida (ASM-131). AI-05 toma su modelo de
// wasichai.agent.model, no de aquí
@ConfigurationProperties("sgspe.ai")
data class AiProperties(
    val enabledOrganizations: List<String> = emptyList(),
    val provider: String = "anthropic",
    val models: Map<String, String> = emptyMap(),
    val prompts: Map<String, String> = emptyMap(),
    val limits: AiLimits = AiLimits()
) {
    // la IA está activada para la organización de ese slug, sin distinguir mayúsculas; con la lista vacía, para nadie (la IA viene
    // apagada, ASM-015)
    fun enabled(slug: String?): Boolean = !slug.isNullOrBlank() && enabledOrganizations.any { it.trim().equals(slug, ignoreCase = true) }

    fun modelFor(useCase: String): String = models[key(useCase)] ?: error("sgspe.ai.models.${key(useCase)} no está configurado")

    fun promptVersion(useCase: String): String = prompts[key(useCase)] ?: error("sgspe.ai.prompts.${key(useCase)} no está configurado")

    companion object {
        // AI-05 → ai-05: la clave de la configuración y el id del prompt
        fun key(useCase: String): String = useCase.trim().lowercase()
    }
}

// ASM-131: caracteres de la fuente de una corrida (422 AI_INPUT_TOO_LARGE antes de llamar), tokens de salida de una llamada,
// llamadas al modelo de una corrida (los agentes de 7.2b; pasarlas es 502 AI_RUN_TRUNCATED), caracteres de una pregunta al
// asistente y, desde 7.2b, bytes del archivo de una evidencia que se lee (más, 422 AI_INPUT_TOO_LARGE) y propuestas que una
// llamada puede dejar (las demás se descartan: la respuesta de runs cabe en el tope de Idempotency-Key)
data class AiLimits(
    val inputChars: Int = 200_000,
    val outputTokens: Int = 8_192,
    val actions: Int = 8,
    val questionChars: Int = 4_000,
    val fileBytes: Int = 26_214_400,
    val proposals: Int = 50
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties::class)
class AiConfiguration
