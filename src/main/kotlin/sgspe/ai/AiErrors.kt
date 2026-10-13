package sgspe.ai

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import wasichai.core.common.WasichaiException

// los errores de la IA, con su código en errors[0].message y su campo en errors[0].field (api.md §3.3 y §4.14). ningún mensaje
// lleva un texto de la fuente, de la pregunta ni de la respuesta
object AiErrors {
    const val ORGANIZATION = "organization"
    const val PROVIDER = "provider"
    const val USE_CASE = "useCase"

    // 503: la organización no tiene la IA activada (ASM-128), o no hay proveedor o no respondió
    fun unavailable(field: String): WasichaiException =
        Finding(
            "AI_UNAVAILABLE",
            if (field == ORGANIZATION) "La IA no está activada para su organización" else "El proveedor de IA no está disponible: intente más tarde",
            field,
            status = HttpStatus.SERVICE_UNAVAILABLE
        ).toCodedException()

    // 422: el texto pasa el tamaño de sgspe.ai.limits, antes de llamar al modelo (ASM-131)
    fun inputTooLarge(
        field: String,
        limit: Int
    ): WasichaiException =
        Finding("AI_INPUT_TOO_LARGE", "El texto pasa los $limit caracteres que admite la IA", field, status = HttpStatus.valueOf(422))
            .toCodedException()

    // 502: la corrida se quedó sin margen de tokens o de acciones y no guardó nada (ASM-131)
    fun truncated(): WasichaiException =
        Finding("AI_RUN_TRUNCATED", "La corrida se quedó sin margen antes de terminar y no guardó nada", USE_CASE, status = HttpStatus.BAD_GATEWAY)
            .toCodedException()

    // 403: la IA la usa una persona; una cuenta de servicio no pregunta ni pide corridas (requested_by_* nombra a un usuario)
    fun personRequired(): WasichaiException =
        Finding("PERSON_REQUIRED", "La IA la usa una persona: una cuenta de servicio no", status = HttpStatus.FORBIDDEN).toCodedException()
}
