package sgspe.ai

import org.springframework.stereotype.Component
import wasichai.agent.AgentAnswerFilter
import wasichai.agent.AgentResultFilter
import wasichai.core.identity.AuthenticatedUser

// el AgentResultFilter de SGSPE (ASM-130, ai-architecture.md §8): cada resultado de herramienta y el resumen de cada paso pasan por
// ToolResults con la tabla y el diccionario de la pregunta en curso. sin pregunta en curso (AssistantRun) lanza: wasichai cierra
// la corrida y nada sale hacia el modelo. su mensaje nunca lleva el dato
@Component
class PseudonymizingResultFilter : AgentResultFilter {
    override suspend fun filter(
        caller: AuthenticatedUser,
        tool: String,
        input: Map<String, Any?>,
        resultJson: String
    ): String {
        val run = AssistantRun.current() ?: throw IllegalStateException("una herramienta del asistente sin pregunta en curso: no se envía nada")
        return ToolResults.screen(input, resultJson, run.names, run.table)
    }
}

// el AgentAnswerFilter de SGSPE: la respuesta final vuelve con los valores de la tabla de la pregunta. los marcadores de los campos
// restringidos y los que la tabla no conoce quedan como están. wasichai no lo llama en una corrida truncada
@Component
class RestoringAnswerFilter : AgentAnswerFilter {
    override suspend fun restore(
        caller: AuthenticatedUser,
        answer: String
    ): String {
        val run = AssistantRun.current() ?: throw IllegalStateException("una respuesta del asistente sin pregunta en curso")
        return run.table.restore(answer)
    }
}
