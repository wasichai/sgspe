package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException

// un parámetro de consulta desconocido o mal formado es un 422 que lo nombra (ASM-054, api.md §3.3): nunca se ignora en
// silencio, porque un filtro mal escrito devolvería todo. el manejador de wasichai lo vuelve problem+json
class UnprocessableContentException(
    violations: List<FieldViolation>
) : WasichaiException(HttpStatus.valueOf(422), "Parámetro de consulta desconocido o mal formado", violations)

object StrictQuery {
    // los parámetros de la consulta deben estar todos en `allowed`; si no, 422 con uno por desconocido, en orden
    fun requireOnly(
        params: Map<String, List<String>>,
        allowed: Set<String>
    ) {
        val unknown = params.keys.filter { it !in allowed }.sorted()
        if (unknown.isEmpty()) return
        val admitted = allowed.sorted().joinToString(", ")
        throw UnprocessableContentException(unknown.map { FieldViolation(it, "no es un parámetro de esta consulta; se admiten $admitted") })
    }
}
