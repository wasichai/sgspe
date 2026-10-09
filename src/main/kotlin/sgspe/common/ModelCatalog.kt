package sgspe.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// model/model.json, model/roles.json y model/workflows/*.json leídos como configuración: viajan dentro del jar
// (processResources de build.gradle.kts) y son los mismos archivos que aplican apply.py y apply_roles.py. de aquí salen
// los campos derivados ("derived": true, que wasichai no conoce), los objetos que declaran una acción, los catálogos ENUM,
// los roles declarados (AssignmentRoleGuard) y los roles de cada transición (destinatarios por rol, ASM-070). así ningún
// nombre de rol vive en el código (RQ-SEC-005)
class ModelCatalog(
    model: JsonNode,
    roles: JsonNode,
    workflows: Map<String, JsonNode>
) {
    data class Field(
        val name: String,
        val type: String,
        val required: Boolean,
        val derived: Boolean,
        val enum: String?
    )

    data class ModelObject(
        val name: String,
        val fields: Map<String, Field>,
        val actions: Set<String>,
        val apiOnly: Boolean,
        val appendOnly: Boolean,
        val requiresReason: Boolean
    )

    data class Relationship(
        val name: String,
        val type: String,
        val source: String,
        val target: String,
        val fieldName: String?,
        val required: Boolean
    )

    data class Transition(
        val name: String,
        val from: String,
        val to: String,
        val roles: List<String>
    )

    val objects: Map<String, ModelObject> =
        model.path("objects").values().uniqueAssociate("objeto") { node ->
            val name = node.path("name").asString()
            name to
                ModelObject(
                    name = name,
                    fields =
                        node.path("fields").values().uniqueAssociate("campo de $name") { field ->
                            field.path("name").asString() to
                                Field(
                                    name = field.path("name").asString(),
                                    type = field.path("type").asString(),
                                    required = field.path("required").asBoolean(false),
                                    derived = field.path("derived").asBoolean(false),
                                    enum = field.path("enum").takeIf { it.isString }?.asString()
                                )
                        },
                    actions =
                        node
                            .path("actions")
                            .values()
                            .map { it.path("name").asString() }
                            .toSet(),
                    apiOnly = node.path("apiOnly").asBoolean(false),
                    appendOnly = node.path("appendOnly").asBoolean(false),
                    requiresReason = node.path("requiresReason").asBoolean(false)
                )
        }

    val relationships: List<Relationship> =
        model.path("relationships").values().map { node ->
            Relationship(
                name = node.path("name").asString(),
                type = node.path("type").takeIf { it.isString }?.asString() ?: "MANY_TO_ONE",
                source = node.path("source").asString(),
                target = node.path("target").asString(),
                fieldName = node.path("fieldName").takeIf { it.isString }?.asString(),
                required = node.path("required").asBoolean(false)
            )
        }

    val enums: Map<String, List<String>> =
        model.path("enums").properties().associate { (key, values) -> key to values.values().map { it.asString() } }

    // los 15 roles declarados de roles.json; los de mappedToAdmin no son roles de wasichai (son ADMIN)
    val declaredRoles: Set<String> =
        roles
            .path("roles")
            .values()
            .map { it.path("name").asString() }
            .toSet()

    private val transitionsByObject: Map<String, List<Transition>> =
        workflows.mapValues { (_, node) ->
            node.path("definition").path("transitions").values().map { transition ->
                Transition(
                    name = transition.path("name").asString(),
                    from = transition.path("from").asString(),
                    to = transition.path("to").asString(),
                    roles = transition.path("roles").values().map { it.asString() }
                )
            }
        }

    // exige lo que fromClasspath() no puede dejar vacío: un JSON sin la clave o un patrón de processResources mal escrito se
    // degradarían en silencio (path() de Jackson) y las guardas decidirían sobre un catálogo vacío
    fun requireComplete(): ModelCatalog {
        check(objects.isNotEmpty()) { "model.json no declara objetos: revise el archivo empaquetado" }
        check(declaredRoles.isNotEmpty()) { "roles.json no declara roles: revise el archivo empaquetado" }
        check(transitionsByObject.isNotEmpty()) { "no hay workflows en classpath:model/workflows/: revise processResources" }
        return this
    }

    // los objetos con workflow (model/workflows/<objeto>.json): solo ellos tienen la columna workflow_state
    val workflowObjects: Set<String> get() = transitionsByObject.keys

    fun objectOrNull(name: String): ModelObject? = objects[name]

    fun derivedFields(objectName: String): Set<String> =
        objects[objectName]
            ?.fields
            ?.values
            ?.filter { it.derived }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()

    fun objectsDeclaring(action: String): Set<String> =
        objects.values
            .filter { action in it.actions }
            .map { it.name }
            .toSet()

    fun enumOf(
        objectName: String,
        fieldName: String
    ): List<String> =
        objects[objectName]
            ?.fields
            ?.get(fieldName)
            ?.enum
            ?.let { enums[it] }
            .orEmpty()

    fun enumValues(key: String): List<String> = enums[key].orEmpty()

    fun transitions(objectName: String): List<Transition> = transitionsByObject[objectName].orEmpty()

    fun transition(
        objectName: String,
        name: String
    ): Transition? = transitions(objectName).firstOrNull { it.name == name }

    // los estados del workflow de un objeto en el orden en que aparecen sus transiciones (de y a), sin repetir: la grilla de
    // las tablas que cuentan por estado (la matriz de compromisos en PUBLICO)
    fun states(objectName: String): List<String> = transitions(objectName).flatMap { listOf(it.from, it.to) }.distinct()

    // la relación MANY_TO_MANY `name` del modelo, o null
    fun manyToMany(name: String): Relationship? = relationships.firstOrNull { it.type == "MANY_TO_MANY" && it.name == name }

    // el campo RELATION de `source` que apunta a `target` (MANY_TO_ONE), o null
    fun relationField(
        source: String,
        target: String
    ): String? = relationships.firstOrNull { it.type == "MANY_TO_ONE" && it.source == source && it.target == target }?.fieldName

    // las relaciones MANY_TO_ONE obligatorias que apuntan a `target`: (objeto, campo). wasichai las declara NOT NULL con ON DELETE
    // SET NULL, así que borrar un `target` del que todavía dependen falla en la base (DependentsDeleteGuard lo vuelve un 409)
    fun requiredDependents(target: String): List<Pair<String, String>> =
        relationships
            .filter { it.type == "MANY_TO_ONE" && it.required && it.target == target && it.fieldName != null }
            .map { it.source to it.fieldName!! }

    // si la relación MANY_TO_ONE `fieldName` de `source` es obligatoria en el modelo (false si no existe)
    fun isRequiredRelation(
        source: String,
        fieldName: String
    ): Boolean = relationships.firstOrNull { it.type == "MANY_TO_ONE" && it.source == source && it.fieldName == fieldName }?.required == true

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()

        fun parse(
            model: String,
            roles: String,
            workflows: Map<String, String> = emptyMap()
        ): ModelCatalog = ModelCatalog(JSON.readTree(model), JSON.readTree(roles), workflows.mapValues { JSON.readTree(it.value) })

        // los archivos que empaqueta processResources bajo classpath:model/. un workflow se llama como su objeto
        fun fromClasspath(): ModelCatalog {
            val resolver = PathMatchingResourcePatternResolver()
            val model = resolver.getResource("classpath:model/model.json")
            val roles = resolver.getResource("classpath:model/roles.json")
            check(model.exists() && roles.exists()) {
                "faltan model/model.json o model/roles.json en el classpath: revise processResources"
            }
            val workflows =
                resolver.getResources("classpath*:model/workflows/*.json").associate { resource ->
                    resource.filename!!.removeSuffix(".json") to resource.inputStream.use { JSON.readTree(it) }
                }
            return ModelCatalog(
                model.inputStream.use { JSON.readTree(it) },
                roles.inputStream.use { JSON.readTree(it) },
                workflows
            ).requireComplete()
        }

        // como associate, pero un nombre repetido es un error del modelo y no una sobrescritura silenciosa
        private fun <V> Iterable<JsonNode>.uniqueAssociate(
            what: String,
            entry: (JsonNode) -> Pair<String, V>
        ): Map<String, V> {
            val result = LinkedHashMap<String, V>()
            for (node in this) {
                val (name, value) = entry(node)
                check(result.put(name, value) == null) { "$what repetido en el modelo: $name" }
            }
            return result
        }
    }
}

@Configuration(proxyBeanMethods = false)
class ModelCatalogConfiguration {
    @Bean
    fun modelCatalog(): ModelCatalog = ModelCatalog.fromClasspath()
}
