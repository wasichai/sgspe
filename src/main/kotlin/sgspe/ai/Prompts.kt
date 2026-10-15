package sgspe.ai

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

// un prompt armado con la versión en uso de su caso: lo que sale hacia el modelo, ya seudonimizado. su toString no lleva el texto
data class RenderedPrompt(
    val id: String,
    val version: String,
    // la clase de salida que declara la cabecera
    val output: String,
    val text: String
) {
    // lo que queda en ai_interaction.prompt_version (ai-architecture.md §9.2)
    val reference: String get() = "$id@$version"

    override fun toString(): String = "RenderedPrompt($reference)"
}

// una versión de un prompt: src/main/resources/sgspe/ai/prompts/<id>/<versión>.md con su cabecera (ai-architecture.md §10)
data class PromptFile(
    val id: String,
    val version: String,
    val useCase: String,
    val output: String,
    val language: String,
    val body: String
) {
    companion object {
        private val KEYS = listOf("id", "version", "use_case", "output", "language")

        // «---», las cinco claves «clave: valor» en ese orden, «---» y el cuerpo. la cabecera tiene que ser la de su ruta, en
        // español y del caso de su id
        fun parse(
            id: String,
            version: String,
            content: String
        ): PromptFile {
            val lines = content.replace("\r\n", "\n").split("\n")
            check(lines.firstOrNull() == "---") { "el prompt $id@$version no empieza con su cabecera" }
            val end =
                lines
                    .drop(1)
                    .indexOf("---")
                    .takeIf { it >= 0 }
                    ?.plus(1) ?: error("el prompt $id@$version no cierra su cabecera")
            val header = lines.subList(1, end).associate { it.substringBefore(":").trim() to it.substringAfter(":", "").trim() }
            check(header.keys.toList() == KEYS) { "la cabecera del prompt $id@$version declara ${header.keys}; se esperan $KEYS" }
            check(header["id"] == id && header["version"] == version) { "la cabecera del prompt $id@$version no es la de su ruta" }
            // un caso puede tener más de un prompt: el suyo (ai-04) y otros con su id de prefijo (ai-04-seccion, plan 8.2c)
            val case = AiProperties.key(header.getValue("use_case"))
            check(id == case || id.startsWith("$case-")) { "el prompt $id@$version es de otro caso" }
            check(header["language"] == "es") { "el prompt $id@$version no está en español" }
            check(header.getValue("output").isNotEmpty()) { "el prompt $id@$version no declara su salida" }
            return PromptFile(
                id,
                version,
                header.getValue("use_case"),
                header.getValue("output"),
                header.getValue("language"),
                lines.drop(end + 1).joinToString("\n").trim()
            )
        }
    }
}

// los prompts versionados (ai-architecture.md §10, RQ-AI-006): la versión en uso de cada caso sale de sgspe.ai.prompts, así que
// volver atrás es un cambio de configuración; las configuradas se cargan al arrancar y una que falta impide arrancar. una versión
// publicada no cambia: PromptManifestTest compara su SHA-256 con prompts/manifest.json
@Component
class Prompts(
    private val properties: AiProperties
) {
    private val loaded = ConcurrentHashMap<String, PromptFile>()

    init {
        properties.prompts.forEach { (useCase, version) -> load(AiProperties.key(useCase), version) }
    }

    fun reference(useCase: String): String = "${AiProperties.key(useCase)}@${properties.promptVersion(useCase)}"

    // el prompt en uso del caso con sus variables: cada {{nombre}} del cuerpo, una sola pasada (un valor con llaves no se vuelve
    // a expandir); una variable que falta o que sobra es un error del código, nunca un prompt a medias
    fun render(
        useCase: String,
        variables: Map<String, String>
    ): RenderedPrompt {
        val id = AiProperties.key(useCase)
        val version = properties.promptVersion(useCase)
        val file = load(id, version)
        return RenderedPrompt(id, version, file.output, fill(file.body, variables, "$id@$version"))
    }

    fun load(
        id: String,
        version: String
    ): PromptFile =
        loaded.getOrPut("$id@$version") {
            val path = "$ROOT/$id/$version.md"
            val content =
                Prompts::class.java.classLoader
                    .getResourceAsStream(path)
                    ?.use { String(it.readAllBytes(), Charsets.UTF_8) }
                    ?: error("falta el prompt $path")
            PromptFile.parse(id, version, content)
        }

    companion object {
        const val ROOT = "sgspe/ai/prompts"
        private val PLACEHOLDER = Regex("""\{\{(\w+)}}""")

        fun fill(
            body: String,
            variables: Map<String, String>,
            reference: String
        ): String {
            val used = PLACEHOLDER.findAll(body).map { it.groupValues[1] }.toSet()
            check(used == variables.keys) { "el prompt $reference usa $used y recibió ${variables.keys}" }
            return PLACEHOLDER.replace(body) { variables.getValue(it.groupValues[1]) }
        }
    }
}
