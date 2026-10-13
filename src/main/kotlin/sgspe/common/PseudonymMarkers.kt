package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.WasichaiException

// un marcador de seudonimización de la IA, por llamada ([PERSONA_1]) o estable ([PERSONA], [PERSONA:<id>]), escrito en un
// valor: ningún marcador llega a un registro de dominio (ai-architecture.md §4.2 y §12.2). lo usan la aceptación de una
// sugerencia (sgspe.ai) y la de un candidato a compromiso (sgspe.commitment), que no depende de sgspe.ai
object PseudonymMarkers {
    const val ATTRIBUTES = "attributes"

    private val MARKER = Regex("""\[(PERSONA|DOCUMENTO|TELEFONO|CORREO|CONTACTO|DATO_SENSIBLE|CITA)(_\d+|:[0-9a-fA-F-]{36})?]""")

    fun has(text: String): Boolean = MARKER.containsMatchIn(text)

    // la ruta del primer marcador en `value`, también dentro de una lista o un objeto (ningún marcador pasa escondido en un
    // valor compuesto), o null
    fun pathOf(
        path: String,
        value: Any?
    ): String? =
        when (value) {
            is String -> path.takeIf { has(value) }
            is Map<*, *> -> value.entries.firstNotNullOfOrNull { (key, inner) -> pathOf("$path.$key", inner) }
            is Collection<*> -> path.takeIf { value.any { pathOf(path, it) != null } }
            else -> null
        }

    // 400 PSEUDONYM_NOT_ALLOWED sobre attributes.<ruta> del primer marcador en cualquier atributo, antes de escribir
    fun requireNone(attributes: Map<String, Any?>) {
        attributes.forEach { (key, value) -> pathOf(key, value)?.let { throw notAllowed(it) } }
    }

    fun notAllowed(path: String): WasichaiException =
        Finding(
            "PSEUDONYM_NOT_ALLOWED",
            "Reemplace los marcadores ([PERSONA], [DOCUMENTO]…) por los valores reales antes de aceptar",
            "$ATTRIBUTES.$path",
            status = HttpStatus.BAD_REQUEST
        ).toCodedException()
}
