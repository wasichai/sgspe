package sgspe.dashboard

import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.territory.ComponentLand
import sgspe.territory.ComponentLandRow
import sgspe.territory.GateOutcome
import sgspe.territory.GateTables
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.io.File
import java.time.LocalDate
import java.util.UUID

// los datos de prueba de un KPI calculado sin base: un Records simulado que contesta, para cada objeto del modelo, una fila por
// estado de su workflow y por fecha de `dates` (cada campo DATE o DATETIME con esa fecha, cada referencia al primer registro de
// su objeto, el proyecto el del filtro) y que anota cada lectura con sus criterios ya escritos en SQL y sus valores
// enlazados. así se ve si un KPI depende del periodo pedido: por lo que lee de la base y por lo que hace con las filas
class KpiFixtureData(
    private val project: UUID,
    private val dates: List<LocalDate>
) {
    private val catalog = ModelCatalog.fromClasspath()
    private val targets: Map<Pair<String, String>, String> =
        catalog.relationships.filter { it.fieldName != null }.associate { (it.source to it.fieldName!!) to it.target }

    // lo que leyó cada cálculo, en orden
    val reads = mutableListOf<String>()

    private fun id(
        objectName: String,
        index: Int
    ): UUID = if (objectName == "project") project else UUID.nameUUIDFromBytes("$objectName-$index".toByteArray())

    private fun states(objectName: String): List<String?> {
        val file = File("model/workflows/$objectName.json")
        if (!file.exists()) return listOf(null)
        return JSON
            .readTree(file)
            .path("definition")
            .path("states")
            .values()
            .map { it.path("name").asString() }
    }

    private fun rows(objectName: String): List<RecordResponse> {
        val model = catalog.objects[objectName] ?: return emptyList()
        val combos = states(objectName).flatMap { state -> dates.map { state to it } }
        return combos.mapIndexed { index, (state, date) ->
            val attributes =
                model.fields.values.associate { field ->
                    field.name to
                        when (field.type) {
                            "DATE" -> date.toString()
                            "DATETIME" -> "${date}T15:00:00Z"
                            "INTEGER" -> 1
                            "DECIMAL" -> "1.00"
                            "BOOLEAN" -> false
                            "ENUM" -> field.enum?.let { catalog.enums[it]?.getOrNull(index % (catalog.enums[it]?.size ?: 1)) }
                            "UUID" -> targets[objectName to field.name]?.let { id(it, 0).toString() }
                            "EMAIL" -> "persona-ejemplo-1@sgspe.test"
                            "GEOMETRY", "FILE" -> null
                            else -> "Texto de ejemplo"
                        }
                } + mapOf("project" to project.toString())
            RecordResponse(id(objectName, index).toString(), null, null, attributes.filterValues { it != null }, state)
        }
    }

    // la definición del objeto con todos sus campos del modelo, cada uno con su nombre de columna: lo que necesitan los
    // criterios de sgspe.common.Criteria para escribirse
    private fun definition(objectName: String): ObjectDefinition =
        ObjectDefinition(
            CustomObject(UUID.randomUUID(), UUID.randomUUID(), objectName, objectName, objectName, null, true, "${objectName}_tbl", null, null),
            catalog.objects[objectName]?.fields?.keys.orEmpty().map { name ->
                CustomField(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    name,
                    name,
                    FieldType.TEXT,
                    name,
                    false,
                    false,
                    null,
                    null,
                    0,
                    null,
                    null,
                    emptyMap(),
                    true,
                    true
                )
            }
        )

    private fun written(
        objectName: String,
        criteria: List<RecordCriterion>
    ): List<String> =
        criteria.map { criterion ->
            val bound = mutableListOf<Any?>()
            val text =
                runCatching {
                    criterion.condition(definition(objectName)) { value ->
                        bound += if (value is Array<*>) value.toList() else value
                        "?"
                    }
                }.getOrElse { "error ${it.javaClass.simpleName}" }
            "$text $bound"
        }

    @Suppress("UNCHECKED_CAST")
    val records: Records =
        org.mockito.Mockito.mock(Records::class.java) { call ->
            val args = call.arguments
            when (call.method.name) {
                "all" -> {
                    val objectName = args[0] as String
                    reads += "all $objectName ${args[1]} ${written(objectName, args[2] as List<RecordCriterion>)} ${args[3]}"
                    val ids = (args[3] as List<UUID>?)?.map { it.toString() }?.toSet()
                    rows(objectName).filter { ids == null || it.id in ids }
                }
                "related" -> {
                    reads += "related ${args[0]} ${args[2]}"
                    val relationship = catalog.relationships.firstOrNull { it.name == args[2] }
                    relationship?.let { rows(if (it.source == args[0]) it.target else it.source) }.orEmpty()
                }
                "page" -> {
                    val objectName = args[0] as String
                    val query = args[1] as RecordQuery
                    reads += "page $objectName ${written(objectName, query.criteria)}"
                    PageResponse(rows(objectName).take(1), 0, 1, null, null, null)
                }
                else -> error("método no simulado: ${call.method.name}")
            }
        }

    // la compuerta de KPI-TER-04: un componente sobre territorio comunal, con los proyectos que pidió anotados
    val componentLand: ComponentLand =
        org.mockito.Mockito.mock(ComponentLand::class.java) { call ->
            if (call.method.name != "rows") return@mock null
            reads += "componentLand ${call.arguments[0]}"
            GateOutcome.Rows(listOf(ComponentLandRow(id("project_component", 0), project, "Texto de ejemplo", "TERRITORIO COMUNAL", id("community", 0))))
        }

    // los conteos en la base (plan 8.2a-bis): la consulta escrita con sus valores enlazados, sin filas
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "<$objectName>"

            override fun column(
                objectName: String,
                field: String
            ) = "\"$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 0
        }

    @Suppress("UNCHECKED_CAST")
    val aggregates: KpiAggregates =
        org.mockito.Mockito.mock(KpiAggregates::class.java) { call ->
            if (call.method.name != "select") return@mock null
            val query = call.arguments[0] as AggregateQuery<Any>
            val bound = mutableListOf<Any?>()
            val sql = query.sql(tables) { value -> "?".also { bound += if (value is Array<*>) value.toList() else value } }
            reads += "aggregate ${query.key} $sql $bound"
            GateOutcome.Rows(emptyList<Any>())
        }

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()
    }
}
