package sgspe.deliverable

import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// los rótulos que imprimen los entregables (#104): un enum del modelo sale en forma de oración con tildes («EN SEPARACION» →
// «En separación»), un estado de workflow con su label de model/workflows/<objeto>.json y un rol con su label de roles.json.
// solo el texto impreso pasa por aquí: agrupar y comparar sigue siendo por el valor crudo (y ordenar, por el orden declarado
// del enum en model.json, enumValues). los archivos viajan dentro del jar (processResources) y se leen una sola vez
object ReportLabels {
    private val json = JsonMapper.builder().build()

    private fun read(path: String): JsonNode? = ClassPathResource(path).takeIf { it.exists() }?.inputStream?.use { json.readTree(it) }

    private fun words(
        node: JsonNode,
        key: String
    ): Set<String> =
        node
            .path(key)
            .values()
            .map { it.asString() }
            .toSet()

    private val acronyms: Set<String>
    private val accents: Map<String, String>
    private val plain: Set<String>

    init {
        val dictionary = checkNotNull(read("sgspe/deliverable/option-words.json")) { "falta sgspe/deliverable/option-words.json" }
        acronyms = words(dictionary, "acronyms")
        accents = dictionary.path("accents").properties().associate { it.key to it.value.asString() }
        plain = words(dictionary, "plain")
    }

    private val roleLabels: Map<String, String> by lazy {
        read("model/roles.json")
            ?.path("roles")
            ?.values()
            ?.associate { it.path("name").asString() to it.path("label").asString() }
            .orEmpty()
    }

    private val enums: Map<String, List<String>> by lazy {
        read("model/model.json")
            ?.path("enums")
            ?.properties()
            ?.associate { it.key to it.value.values().map { v -> v.asString() } }
            .orEmpty()
    }

    // los valores de un enum de model.json en su orden declarado (el que ordena lo que DOC-01 lista así); vacío si no existe
    fun enumValues(name: String): List<String> = enums[name].orEmpty()

    private val stateLabels = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    // un valor ENUM en forma de oración: la sigla igual, la palabra con tilde en su forma, las demás en minúsculas; un valor que
    // no está todo en mayúsculas ya viene escrito por una persona y sale igual
    fun option(value: String?): String? {
        if (value == null) return null
        if (value != value.uppercase()) return value
        return value
            .split(' ')
            .joinToString(" ") { word -> if (word in acronyms) word else accents[word] ?: word.lowercase() }
            .replaceFirstChar { it.uppercase() }
    }

    // el label de un estado del workflow del objeto; si no lo encuentra, el valor tal cual
    fun state(
        objectName: String,
        state: String?
    ): String? {
        if (state == null) return null
        val labels =
            stateLabels.getOrPut(objectName) {
                read("model/workflows/$objectName.json")
                    ?.path("definition")
                    ?.path("states")
                    ?.values()
                    ?.associate { it.path("name").asString() to it.path("label").asString() }
                    .orEmpty()
            }
        return labels[state]?.takeIf { it.isNotBlank() } ?: state
    }

    // el label de un rol de roles.json; si no lo encuentra, el valor tal cual
    fun role(name: String?): String? = name?.let { roleLabels[it]?.takeIf { label -> label.isNotBlank() } ?: it }
}
