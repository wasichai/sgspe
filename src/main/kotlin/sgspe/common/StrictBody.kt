package sgspe.common

import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException

// el cuerpo de un comando de /api/sgspe/** no admite propiedades desconocidas: un 400 que nombra cada una (api.md §3.2).
// es más estricto que la api genérica de wasichai 0.3.2, que ignora los atributos que el objeto no tiene
object StrictBody {
    fun requireOnly(
        body: Map<String, Any?>,
        allowed: Set<String>,
        prefix: String = ""
    ) {
        val unknown = body.keys.filter { it !in allowed }.sorted()
        if (unknown.isEmpty()) return
        throw ValidationException(
            "Propiedad desconocida en el cuerpo",
            unknown.map { FieldViolation("$prefix$it", "no es una propiedad de este comando; se admiten ${allowed.sorted().joinToString(", ")}") }
        )
    }

    // un objeto json del cuerpo, o vacío si no vino; cualquier otra cosa es un 400 sobre la propiedad
    @Suppress("UNCHECKED_CAST")
    fun objectOf(
        body: Map<String, Any?>,
        name: String
    ): Map<String, Any?> =
        when (val value = body[name]) {
            null -> emptyMap()
            is Map<*, *> -> value as Map<String, Any?>
            else -> throw ValidationException("Cuerpo inválido", name, "debe ser un objeto json")
        }
}
