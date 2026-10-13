package sgspe.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import sgspe.attention.QcsDueDates
import sgspe.common.Criteria
import sgspe.common.Records
import sgspe.common.Values
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

// los límites de AI-07 (decisión 11 del plan 7.2c): los registros de un objeto que una corrida lee como la persona (más es un 400
// TOO_MANY_RECORDS sobre target, nunca una lista recortada) y los pares que llegan al modelo
@ConfigurationProperties("sgspe.ai.duplicates")
data class DuplicateProperties(
    val maxRecords: Int = 2_000,
    val maxPairs: Int = 20
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DuplicateProperties::class)
class DuplicateConfiguration

// sobre qué corre AI-07: un proyecto o un registro de uno de los objetos que compara
data class DuplicateTarget(
    val objectName: String,
    val recordId: UUID
)

// la salida estructurada de AI-07 (prompt ai-07): por par, si es un duplicado, la confianza y las razones
data class DuplicateVerdicts(
    val pairs: List<DuplicateVerdict> = emptyList()
)

data class DuplicateVerdict(
    val pair: Int = 0,
    val duplicate: Boolean = false,
    val confidence: Double = 0.0,
    val reasons: List<String> = emptyList()
)

// una sugerencia DUPLICADO lista para guardar (ai-architecture.md §4.2): el registro destino, el otro, las razones con marcadores
// estables (las de las reglas primero) y la confianza. el destino es el que la aceptación tocaría: en una organización, la que se
// conserva (la más antigua, que recibe el nombre de la otra en alternative_names); en los demás, la más nueva, la repetida (una
// queja recibe duplicate_of hacia la original)
data class DuplicateProposal(
    val objectName: String,
    val targetRecordId: UUID,
    val otherRecordId: UUID,
    val reasons: List<String>,
    val confidence: BigDecimal,
    val project: UUID?
) {
    // suggested_value de la fila DUPLICADO de §4.2
    fun suggestedValue(json: JsonMapper): String = json.writeValueAsString(mapOf("other_record_id" to otherRecordId.toString(), "reasons" to reasons))
}

// lo que dejó una detección: las propuestas, la ai_interaction de su llamada (null si no hubo pares y no se llamó), el modelo y
// cuántos veredictos de duplicado descartó la validación (un par que no existe, una confianza fuera de 0 a 1, un par repetido)
data class DuplicateFindings(
    val proposals: List<DuplicateProposal>,
    val interaction: UUID?,
    val model: String,
    val discarded: Int = 0
)

// los registros de AI-07 desde lo que lee la persona, puros: un objeto de wasichai a DuplicateRecord, y los pares y veredictos
// a propuestas (ai-architecture.md §4.1 y §4.2)
object DuplicateRecords {
    const val PROJECT = "project"
    private val ALTERNATIVES = Regex("""[;,|\n]+""")
    private const val MAX_REASONS = 3
    private const val MAX_REASON = 300

    fun of(
        objectName: String,
        response: RecordResponse,
        labels: Map<UUID, String> = emptyMap()
    ): DuplicateRecord = of(objectName, UUID.fromString(response.id), response.attributes, response.createdAt, labels)

    fun of(
        objectName: String,
        id: UUID,
        attributes: Map<String, Any?>,
        createdAt: java.time.Instant?,
        labels: Map<UUID, String> = emptyMap()
    ): DuplicateRecord {
        fun text(field: String) = Values.text(attributes[field])

        fun uuid(field: String) = Records.uuid(attributes[field])

        fun date(field: String) = text(field)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return when (objectName) {
            "person" ->
                DuplicateRecord(
                    objectName,
                    id,
                    names = listOfNotNull(listOfNotNull(text("given_names"), text("surnames")).joinToString(" ").ifEmpty { null }),
                    locality = uuid("residence_locality"),
                    createdAt = createdAt
                )
            "organization" ->
                DuplicateRecord(
                    objectName,
                    id,
                    names =
                        listOfNotNull(text("name")) +
                            text("alternative_names")
                                .orEmpty()
                                .split(ALTERNATIVES)
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                    locality = uuid("locality"),
                    createdAt = createdAt
                )
            "stakeholder" ->
                DuplicateRecord(
                    objectName,
                    id,
                    names = listOfNotNull((uuid("person") ?: uuid("organization"))?.let { labels[it] }),
                    project = uuid(PROJECT),
                    locality = uuid("locality"),
                    createdAt = createdAt
                )
            "commitment" ->
                DuplicateRecord(
                    objectName,
                    id,
                    text = listOfNotNull(text("title"), text("description")).joinToString(". "),
                    project = uuid(PROJECT),
                    date = date("agreed_date") ?: date("due_date"),
                    locality = uuid("locality"),
                    createdAt = createdAt
                )
            else ->
                DuplicateRecord(
                    objectName,
                    id,
                    text = text("description"),
                    project = uuid(PROJECT),
                    date = QcsDueDates.receivedDate(attributes["received_at"]),
                    locality = uuid("locality"),
                    createdAt = createdAt,
                    duplicateOf = uuid("duplicate_of")
                )
        }
    }

    // los pares como los recibe el modelo, numerados desde 1: lo que compara de cada registro (sin ids) y las razones de las
    // reglas, entre etiquetas <par> que el contenido no puede abrir ni cerrar
    fun render(pairs: List<DuplicatePair>): String =
        pairs.withIndex().joinToString("\n\n") { (i, pair) ->
            listOf(
                "<par n=\"${i + 1}\" objeto=\"${pair.first.objectName}\">",
                "A: ${describe(pair.first)}",
                "B: ${describe(pair.second)}",
                "Razones de las reglas: ${pair.reasons.joinToString("; ") { it.text }}",
                "</par>"
            ).joinToString("\n")
        }

    private val TAG = Regex("""<(\s*/?\s*)par\b""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    // lo que se describe de cada registro (sus nombres, su texto) llega en una sola línea y a lo más con estos caracteres: un salto
    // del contenido no abre otra línea «A:» o «B:» y un texto largo no agranda el prompt
    const val MAX_DESCRIBED = 500

    private fun describe(record: DuplicateRecord): String =
        listOfNotNull(
            record.names.takeIf { it.isNotEmpty() }?.let { "nombres: ${clip(it.joinToString(" / "))}" },
            record.text?.let { "texto: ${clip(it)}" },
            record.date?.let { "fecha: $it" }
        ).joinToString("; ").ifEmpty { "sin datos" }.let { text -> TAG.replace(text) { "(" + it.groupValues[1] + "par" } }

    private fun clip(text: String): String {
        val line = SPACES.replace(text, " ").trim()
        return if (line.length <= MAX_DESCRIBED) line else line.take(MAX_DESCRIBED).trimEnd() + "…"
    }

    // los veredictos que valen (un par que existe, marcado duplicado, con confianza entre 0 y 1; el primero de cada par) como
    // propuestas: las razones de las reglas y hasta tres del modelo, cada una pasada a marcadores estables con `stable`
    fun proposals(
        pairs: List<DuplicatePair>,
        verdicts: List<DuplicateVerdict>,
        project: UUID?,
        stable: (String) -> String
    ): List<DuplicateProposal> =
        verdicts
            .filter { it.duplicate && it.pair in 1..pairs.size && it.confidence in 0.0..1.0 }
            .distinctBy { it.pair }
            .sortedBy { it.pair }
            .map { verdict ->
                val pair = pairs[verdict.pair - 1]
                val keep = pair.first.objectName == "organization"
                val (target, other) = if (keep) pair.first to pair.second else pair.second to pair.first
                val reasons =
                    pair.reasons.map { it.text } +
                        verdict.reasons
                            .map { stable(it.trim()) }
                            .filter { it.isNotEmpty() && it.length <= MAX_REASON }
                            .take(MAX_REASONS)
                DuplicateProposal(
                    target.objectName,
                    target.id,
                    other.id,
                    reasons.distinct(),
                    BigDecimal.valueOf(verdict.confidence).setScale(2, RoundingMode.HALF_UP),
                    target.project ?: project
                )
            }
}

// AI-07 sin escribir nada (ai-architecture.md §4.1, especificación de F7 §3.5): lee como la persona los registros del destino,
// arma los pares con DuplicateCandidates (sin los ya vinculados ni los ya propuestos), y solo si hay pares llama al modelo, que
// los compara, seudonimizados con el diccionario de quien pide; la salida se valida y vuelve como propuestas. la escritura de
// las sugerencias es de DuplicateRun (Tarea 10), con SuggestionWriter.write de 7.2b
@Component
class DuplicateDetection(
    private val records: Records,
    private val calls: ModelCalls,
    private val properties: DuplicateProperties,
    private val ai: AiProperties
) {
    // `names`, el diccionario de quien pide; `budget`, el tope de llamadas de la corrida (gasta una antes de llamar)
    suspend fun detect(
        target: DuplicateTarget,
        names: NameDictionary,
        budget: RunBudget
    ): DuplicateFindings {
        val (project, read) = read(target)
        val known = known(read)
        val all = DuplicateCandidates.pairs(read, Int.MAX_VALUE, known)
        val pairs =
            (
                if (target.objectName ==
                    DuplicateRecords.PROJECT
                ) {
                    all
                } else {
                    all.filter { target.recordId in setOf(it.first.id, it.second.id) }
                }
            ).take(properties.maxPairs)
        val model = ai.modelFor(USE_CASE)
        if (pairs.isEmpty()) return DuplicateFindings(emptyList(), null, model)
        budget.spend()
        val result =
            calls.call(
                ModelCall(
                    useCase = USE_CASE,
                    variables = mapOf(PAIRS to DuplicateRecords.render(pairs)),
                    names = names,
                    outputClass = DuplicateVerdicts::class.java,
                    inputReference = "${target.objectName}:${target.recordId}",
                    project = project,
                    summary = { output: DuplicateVerdicts -> "${pairs.size} pares comparados; ${output.pairs.count { it.duplicate }} posibles duplicados" }
                )
            )
        val proposals = DuplicateRecords.proposals(pairs, result.output.pairs, project) { result.table.stable(it) }
        val discarded = (result.output.pairs.count { it.duplicate } - proposals.size).coerceAtLeast(0)
        return DuplicateFindings(proposals, result.interaction, model, discarded)
    }

    // el proyecto de la corrida y los registros que compara, como la persona. un proyecto: sus QCS, compromisos y grupos de interés
    // y las personas y organizaciones de esos grupos; un registro de proyecto: los de su objeto en su proyecto; una persona u
    // organización: las de su objeto en el tenant
    private suspend fun read(target: DuplicateTarget): Pair<UUID?, List<DuplicateRecord>> {
        if (target.objectName != DuplicateRecords.PROJECT && target.objectName !in DuplicateCandidates.OBJECTS) {
            throw ValidationException(
                "Destino inválido",
                "target.object",
                "AI-07 compara un proyecto o ${DuplicateCandidates.OBJECTS.sorted().joinToString(", ")}"
            )
        }
        val record =
            records.find(target.objectName, target.recordId) ?: throw NotFoundException("No existe ${target.objectName} ${target.recordId} o no puede leerlo")
        if (target.objectName == DuplicateRecords.PROJECT) {
            val filter = mapOf(DuplicateRecords.PROJECT to target.recordId.toString())
            val stakeholders = readAll("stakeholder", filter)
            val people = readAll("person", ids = stakeholders.mapNotNull { Records.uuid(it.attributes["person"]) }.distinct())
            val organizations = readAll("organization", ids = stakeholders.mapNotNull { Records.uuid(it.attributes["organization"]) }.distinct())
            val parties = people.map { DuplicateRecords.of("person", it) } + organizations.map { DuplicateRecords.of("organization", it) }
            val labels = parties.mapNotNull { party -> party.names.firstOrNull()?.let { party.id to it } }.toMap()
            val textual = DuplicateCandidates.TEXTUAL.flatMap { name -> readAll(name, filter).map { DuplicateRecords.of(name, it) } }
            return target.recordId to (parties + stakeholders.map { DuplicateRecords.of("stakeholder", it, labels) } + textual)
        }
        val project = Records.uuid(record.attributes[DuplicateRecords.PROJECT])
        if (target.objectName in DuplicateCandidates.TEXTUAL) {
            val rows = project?.let { readAll(target.objectName, mapOf(DuplicateRecords.PROJECT to it.toString())) } ?: listOf(record)
            return project to rows.map { DuplicateRecords.of(target.objectName, it) }
        }
        if (target.objectName == "stakeholder") {
            val rows = project?.let { readAll("stakeholder", mapOf(DuplicateRecords.PROJECT to it.toString())) } ?: listOf(record)
            val people = readAll("person", ids = rows.mapNotNull { Records.uuid(it.attributes["person"]) }.distinct())
            val organizations = readAll("organization", ids = rows.mapNotNull { Records.uuid(it.attributes["organization"]) }.distinct())
            val labels =
                (people.map { DuplicateRecords.of("person", it) } + organizations.map { DuplicateRecords.of("organization", it) })
                    .mapNotNull { party -> party.names.firstOrNull()?.let { party.id to it } }
                    .toMap()
            return project to rows.map { DuplicateRecords.of("stakeholder", it, labels) }
        }
        return null to readAll(target.objectName).map { DuplicateRecords.of(target.objectName, it) }
    }

    // todos los de un objeto que la persona lee, acotados (400 TOO_MANY_RECORDS sobre target); sin READ, ninguno
    private suspend fun readAll(
        objectName: String,
        filters: Map<String, String> = emptyMap(),
        ids: List<UUID>? = null
    ): List<RecordResponse> =
        try {
            records.all(objectName, filters = filters, ids = ids, max = properties.maxRecords, field = TARGET)
        } catch (_: ForbiddenException) {
            emptyList()
        }

    // los pares ya propuestos como DUPLICADO, en cualquier estado, sobre los registros de la corrida: no se vuelven a proponer. el
    // destino de una sugerencia es siempre uno de los dos del par, así que basta leer las que apuntan a los registros leídos; sin
    // el tope de max-records, que es de los registros que se comparan (las sugerencias acumuladas no dan 400 TOO_MANY_RECORDS)
    private suspend fun known(read: List<DuplicateRecord>): Set<Set<UUID>> =
        read
            .groupBy({ it.objectName }, { it.id })
            .flatMap { (name, ids) ->
                suggestions(name, ids).mapNotNull { suggestion ->
                    val target = Records.uuid(suggestion.attributes["target_record_id"])
                    val other = otherOf(Values.text(suggestion.attributes["suggested_value"]))
                    if (target != null && other != null) setOf(target, other) else null
                }
            }.toSet()

    private suspend fun suggestions(
        objectName: String,
        ids: List<UUID>
    ): List<RecordResponse> =
        try {
            records.all(
                SUGGESTION,
                filters = mapOf("suggestion_kind" to KIND, "target_object" to objectName),
                criteria = listOf(Criteria.anyOf("target_record_id", ids.distinct()))
            )
        } catch (_: ForbiddenException) {
            emptyList()
        }

    companion object {
        const val USE_CASE = "AI-07"
        const val PAIRS = "pairs"
        const val TARGET = "target"
        const val SUGGESTION = "ai_suggestion"
        const val KIND = "DUPLICADO"
        private val JSON: JsonMapper = JsonMapper.builder().build()

        // other_record_id de un suggested_value DUPLICADO; null si no se puede leer
        fun otherOf(value: String?): UUID? =
            value?.let { text -> runCatching { JSON.readTree(text)["other_record_id"]?.asString() }.getOrNull() }?.let { Records.uuid(it) }
    }
}
