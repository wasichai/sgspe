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
    const val TARGET = "target"

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

    // 422: el archivo de la evidencia pasa sgspe.ai.limits.file-bytes; no se lee entero (ASM-131)
    fun fileTooLarge(limit: Int): WasichaiException =
        Finding("AI_INPUT_TOO_LARGE", "El archivo pasa los $limit bytes que la IA lee", TARGET, status = HttpStatus.valueOf(422)).toCodedException()

    // 502: la corrida se quedó sin margen de tokens o de acciones y no guardó nada (ASM-131)
    fun truncated(): WasichaiException =
        Finding("AI_RUN_TRUNCATED", "La corrida se quedó sin margen antes de terminar y no guardó nada", USE_CASE, status = HttpStatus.BAD_GATEWAY)
            .toCodedException()

    // 422: la fuente no tiene texto que leer: un PDF escaneado, un archivo que no es PDF ni DOCX o una minuta vacía (ASM-132,
    // sin OCR). antes de llamar al modelo, así que no deja interacción
    fun noText(): WasichaiException =
        Finding("AI_NO_TEXT", "La fuente no tiene texto que la IA pueda leer: solo PDF con texto o DOCX, sin OCR", TARGET, status = HttpStatus.valueOf(422))
            .toCodedException()

    // 403: la IA la usa una persona; una cuenta de servicio no pregunta ni pide corridas (requested_by_* nombra a un usuario)
    fun personRequired(): WasichaiException =
        Finding("PERSON_REQUIRED", "La IA la usa una persona: una cuenta de servicio no", status = HttpStatus.FORBIDDEN).toCodedException()
}
