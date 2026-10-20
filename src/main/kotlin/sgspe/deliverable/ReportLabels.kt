package sgspe.deliverable

import org.springframework.core.io.ClassPathResource
import sgspe.common.OptionLabels
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// los rótulos que imprimen los entregables (#104): un enum del modelo sale en forma de oración con tildes («EN SEPARACION» →
// «En separación»), un estado de workflow con su label de model/workflows/<objeto>.json y un rol con su label de roles.json.
// solo el texto impreso pasa por aquí: agrupar y comparar sigue siendo por el valor crudo (y ordenar, por el orden declarado
// del enum en model.json, enumValues). los archivos viajan dentro del jar (processResources) y se leen una sola vez
object ReportLabels {
    private val json = JsonMapper.builder().build()

    private fun read(path: String): JsonNode? = ClassPathResource(path).takeIf { it.exists() }?.inputStream?.use { json.readTree(it) }

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

    // un valor ENUM en forma de oración (OptionLabels, en sgspe.common: también lo usa JOB-04)
    fun option(value: String?): String? = OptionLabels.option(value)

    // los estados del workflow del objeto por nombre, con su label, en el orden declarado
    private fun workflowStates(objectName: String): Map<String, String> =
        stateLabels.getOrPut(objectName) {
            read("model/workflows/$objectName.json")
                ?.path("definition")
                ?.path("states")
                ?.values()
                ?.associate { it.path("name").asString() to it.path("label").asString() }
                .orEmpty()
        }

    // el label de un estado del workflow del objeto; si no lo encuentra, el valor tal cual
    fun state(
        objectName: String,
        state: String?
    ): String? {
        if (state == null) return null
        return workflowStates(objectName)[state]?.takeIf { it.isNotBlank() } ?: state
    }

    // los estados del workflow del objeto en su orden declarado (la grilla de un conteo por estado, ASM-088); vacío si no existe
    fun states(objectName: String): List<String> = workflowStates(objectName).keys.toList()

    // el label de un rol de roles.json; si no lo encuentra, el valor tal cual
    fun role(name: String?): String? = name?.let { roleLabels[it]?.takeIf { label -> label.isNotBlank() } ?: it }
}
