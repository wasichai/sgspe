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

    // los atributos de un registro que manda un comando (`prefix` es donde van en el cuerpo, attributes. por defecto): campos del
    // objeto o sus relaciones MANY_TO_ONE, nunca un derivado ni un campo que fija el propio comando (`fixed`). un nombre que el
    // objeto no tiene es un 400 que lo nombra; la API genérica lo ignoraría en silencio (api.md §3.2). desde F3
    fun requireAttributes(
        catalog: ModelCatalog,
        objectName: String,
        attributes: Map<String, Any?>,
        fixed: Set<String> = emptySet(),
        prefix: String = "attributes."
    ) {
        val obj = catalog.objectOrNull(objectName)
        val known = obj?.fields?.keys.orEmpty() + catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }
        val derived = catalog.derivedFields(objectName)
        val violations =
            attributes.keys.sorted().mapNotNull { name ->
                when {
                    name !in known -> FieldViolation("$prefix$name", "no es un campo de $objectName")
                    name in derived -> FieldViolation("$prefix$name", "es derivado: lo calcula SGSPE")
                    name in fixed -> FieldViolation("$prefix$name", "lo fija este comando")
                    else -> null
                }
            }
        if (violations.isNotEmpty()) throw ValidationException("Atributo no admitido", violations)
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
