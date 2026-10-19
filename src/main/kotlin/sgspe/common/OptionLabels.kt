package sgspe.common

import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper

// un valor ENUM del modelo en forma de oración con tildes («EN SEPARACION» → «En separación»), con el diccionario
// option-words.json, que clasifica toda palabra de todo enum (ReportLabelsTest lo vigila; el portal lo copia). lo usan los
// entregables (ReportLabels.option) y los textos que un trabajo escribe en un registro, como el título de una alerta de JOB-04;
// solo el texto que se ve pasa por aquí: agrupar y comparar sigue siendo por el valor crudo
object OptionLabels {
    private val acronyms: Set<String>
    private val accents: Map<String, String>

    init {
        val resource = ClassPathResource("sgspe/deliverable/option-words.json")
        check(resource.exists()) { "falta sgspe/deliverable/option-words.json" }
        val dictionary = resource.inputStream.use { JsonMapper.builder().build().readTree(it) }
        acronyms =
            dictionary
                .path("acronyms")
                .values()
                .map { it.asString() }
                .toSet()
        accents = dictionary.path("accents").properties().associate { it.key to it.value.asString() }
    }

    // la sigla igual, la palabra con tilde en su forma, las demás en minúsculas; un valor que no está todo en mayúsculas ya
    // viene escrito por una persona y sale igual
    fun option(value: String?): String? {
        if (value == null) return null
        if (value != value.uppercase()) return value
        return value
            .split(' ')
            .joinToString(" ") { word -> if (word in acronyms) word else accents[word] ?: word.lowercase() }
            .replaceFirstChar { it.uppercase() }
    }
}
