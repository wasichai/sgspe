package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException

// los estados propios de /api/sgspe/** que la plataforma no trae (api.md §3.3). el manejador de wasichai los vuelve
// problem+json como a los suyos

// 412: If-Match no coincide con el updatedAt actual (api.md §3.6); errors[] lleva el valor actual
class PreconditionFailedException(
    message: String,
    current: String?
) : WasichaiException(HttpStatus.PRECONDITION_FAILED, message, listOf(FieldViolation("updatedAt", "valor actual ${current ?: "desconocido"}")))

// 428: un comando que exige If-Match llegó sin él
class PreconditionRequiredException(
    message: String
) : WasichaiException(HttpStatus.PRECONDITION_REQUIRED, message, listOf(FieldViolation("If-Match", "envíe el updatedAt que leyó, entre comillas")))

// cualquier otro estado con su detalle y sus campos: lo usan las guardas de reglas (Finding.toCodedException)
class RuleException(
    status: HttpStatus,
    message: String,
    violations: List<FieldViolation> = emptyList()
) : WasichaiException(status, message, violations)
