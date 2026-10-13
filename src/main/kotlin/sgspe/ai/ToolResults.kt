package sgspe.ai

import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

// lo que una herramienta del asistente devuelve, como sale hacia el modelo (ai-architecture.md §8, ASM-130): el valor de cada campo
// restringido del objeto leído, con su marcador tipado sin número (RestrictedFields), y todo texto seudonimizado con la tabla y el
// diccionario de la pregunta. el objeto leído es el relatedObject del resultado (related_records), si no su object (query_records,
// count_records, record_history…) o, si no trae ninguno, el object de la entrada (get_record) (decisión 2 del plan 7.2a). se
// enmascaran los atributos de un registro y el antes y el después de un cambio del historial. un valor restringido que se distingue
// queda reservado en la tabla y se enmascara también en el texto libre que siga. un texto que no es un objeto ni una lista json (el
// resumen de un paso, un error) solo se seudonimiza. los números, los booleanos y las claves no se tocan
object ToolResults {
    private const val ATTRIBUTES = "attributes"
    private val json = JsonMapper.builder().build()

    fun screen(
        input: Map<String, Any?>,
        text: String,
        names: NameDictionary,
        table: PseudonymTable
    ): String {
        val parsed = parse(text) ?: return Pseudonymizer.pseudonymize(text, names, table)
        val masked = mask(parsed, RestrictedFields.of(objectOf(parsed, input)), table, null)
        return json.writeValueAsString(pseudonymized(masked, names, table))
    }

    fun objectOf(
        parsed: Any?,
        input: Map<String, Any?>
    ): String? {
        val payload = parsed as? Map<*, *>
        return (payload?.get("relatedObject") ?: payload?.get("object") ?: input["object"])?.toString()
    }

    private fun parse(text: String): Any? {
        val start = text.trimStart().firstOrNull()
        if (start != '{' && start != '[') return null
        return try {
            json.readValue(text, Any::class.java)
        } catch (_: JacksonException) {
            null
        }
    }

    // `key` es la clave bajo la que cuelga `node`: los campos de un mapa que cuelga de attributes son los del registro
    private fun mask(
        node: Any?,
        fields: Map<String, Marker>,
        table: PseudonymTable,
        key: String?
    ): Any? =
        when (node) {
            is Map<*, *> -> {
                val change = if ("before" in node || "after" in node) node["field"]?.toString()?.let { fields[it] } else null
                node.entries.associate { (k, v) ->
                    val name = k.toString()
                    val marker =
                        when {
                            key == ATTRIBUTES -> fields[name]
                            change != null && (name == "before" || name == "after") -> change
                            else -> null
                        }
                    name to if (marker != null && v != null) reserve(v, marker, table) else mask(v, fields, table, name)
                }
            }
            is List<*> -> node.map { mask(it, fields, table, key) }
            else -> node
        }

    // el marcador del campo; cada texto del valor (y cada número: un documento puede llegar como cifra) queda reservado en la
    // tabla si se distingue
    private fun reserve(
        value: Any,
        marker: Marker,
        table: PseudonymTable
    ): String {
        texts(value).forEach { table.reserve(marker, it) }
        return marker.reserved
    }

    private fun texts(node: Any?): List<String> =
        when (node) {
            is String -> listOf(node)
            is Number -> listOf(node.toString())
            is Map<*, *> -> node.values.flatMap { texts(it) }
            is List<*> -> node.flatMap { texts(it) }
            else -> emptyList()
        }

    private fun pseudonymized(
        node: Any?,
        names: NameDictionary,
        table: PseudonymTable
    ): Any? =
        when (node) {
            is String -> Pseudonymizer.pseudonymize(node, names, table)
            is Map<*, *> -> node.entries.associate { (k, v) -> k.toString() to pseudonymized(v, names, table) }
            is List<*> -> node.map { pseudonymized(it, names, table) }
            else -> node
        }
}
