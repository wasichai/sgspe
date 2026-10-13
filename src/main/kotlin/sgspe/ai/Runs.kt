package sgspe.ai

import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

// lo que pide una corrida de POST /api/sgspe/ai/runs: el registro fuente
data class RunTarget(
    val objectName: String,
    val recordId: UUID
)

// la fuente de una corrida leída como la persona (especificación de F7 §3.4): el texto que el modelo lee y del que sale cada cita,
// las demás variables del prompt, los valores restringidos que la llamada conoce (los asistentes de una interacción), la
// referencia de ai_interaction.input_reference (`<objeto>:<id>` y su huella o su versión, nunca el contenido) y los registros con
// que se relaciona: su proyecto, la interacción y el acuerdo a los que apuntan las extracciones y, si es una evidencia, ella
data class RunSource(
    val objectName: String,
    val id: UUID,
    val project: UUID?,
    val text: String,
    val inputReference: String,
    val restricted: List<RestrictedValue> = emptyList(),
    val context: Map<String, String> = emptyMap(),
    val engagement: UUID? = null,
    val agreement: UUID? = null,
    val evidence: UUID? = null,
    // los valores vigentes del registro (AI-08: no se propone lo que ya tiene)
    val current: Map<String, Any?> = emptyMap()
)

// las llamadas al modelo de una corrida (sgspe.ai.limits.actions, ASM-131): un agente gasta una antes de cada llamada; sin margen,
// 502 AI_RUN_TRUNCATED antes de llamar, así que esa llamada no deja interacción y la corrida no guarda nada
class RunBudget(
    private val actions: Int
) {
    var spent: Int = 0
        private set

    fun spend() {
        if (spent >= actions) throw AiErrors.truncated()
        spent++
    }
}

// una ai_suggestion lista para guardar (ai-architecture.md §9.1): suggested_value y evidence_quote ya con marcadores estables
data class SuggestionDraft(
    val kind: String,
    val targetObject: String,
    val targetRecordId: UUID?,
    val suggestedValue: String,
    val evidenceQuote: String?,
    val confidence: BigDecimal?,
    val model: String,
    val project: UUID?,
    val interaction: UUID
)

// un commitment_candidate de la IA listo para guardar: sin responsable sugerido (el modelo solo ve [CORREO]; lo elige quien acepta)
data class CandidateDraft(
    val project: UUID,
    val proposedText: String,
    val evidenceQuote: String,
    val beneficiariesText: String?,
    val suggestedDueDate: LocalDate?,
    val confidence: BigDecimal,
    val interaction: UUID,
    val sourceEvidence: UUID?,
    val engagement: UUID?
)

// lo que dejó una corrida: las propuestas válidas, las llamadas hechas y cuántas propuestas descartó la validación
data class RunProposals(
    val suggestions: List<SuggestionDraft> = emptyList(),
    val candidates: List<CandidateDraft> = emptyList(),
    val interactions: List<UUID> = emptyList(),
    val discarded: Int = 0
) {
    operator fun plus(other: RunProposals) =
        RunProposals(suggestions + other.suggestions, candidates + other.candidates, interactions + other.interactions, discarded + other.discarded)
}

// las piezas de validación que comparten los agentes (ai-architecture.md §6.1, P11): Kotlin puro
object Proposals {
    private val JSON: JsonMapper = JsonMapper.builder().build()
    private val HTML_TAG = Regex("""</?[A-Za-z][^<>]*>""")
    private val PERSON_REFERENCE = Regex("""^\[PERSONA:([0-9a-fA-F-]{36})]$""")

    // suggested_value de EXTRACCION, CLASIFICACION y DUPLICADO: un texto JSON con la forma de su fila de la lista permitida
    // (ai-architecture.md §4.2), en el orden en que se arma y sin las claves vacías
    fun json(value: Map<String, Any?>): String = JSON.writeValueAsString(value.filterValues { it != null })

    // un suggested_value JSON leído como objeto, o null si no lo es
    @Suppress("UNCHECKED_CAST")
    fun parse(value: String?): Map<String, Any?>? = value?.let { runCatching { JSON.readValue(it, Map::class.java) as Map<String, Any?> }.getOrNull() }

    // la confianza que informó el modelo, de 0 a 1 con dos decimales; fuera de rango o ausente, null (la propuesta se descarta)
    fun confidence(value: Double?): BigDecimal? =
        value?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) }

    // un texto Markdown sin HTML (ai-architecture.md §4.2): las etiquetas se quitan hasta que no cambie nada, porque quitar una
    // puede juntar otra (`<<b>script>` queda `<script>`); en blanco, null
    fun markdown(text: String?): String? {
        var current = text ?: return null
        while (true) {
            val next = HTML_TAG.replace(current, "")
            if (next == current) break
            current = next
        }
        return current.trim().ifEmpty { null }
    }

    // la fecha ISO que propuso el modelo, o null si no es una fecha
    fun date(value: String?): LocalDate? = value?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // el id de una persona registrada en un marcador estable `[PERSONA:<id>]`, o null
    fun personReference(stable: String): UUID? =
        PERSON_REFERENCE
            .matchEntire(stable.trim())
            ?.groupValues
            ?.get(1)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    // el texto plegado para comparar duplicados: sin tildes ni mayúsculas, con los espacios colapsados
    fun folded(text: String): String = Folding.fold(Folding.nfc(text)).replace(Regex("""\s+"""), " ").trim()

    // el contenido de la fuente no puede cerrar el bloque que lo delimita en el prompt (ai-architecture.md §12.3): una etiqueta
    // <fuente> o </fuente> escrita en el texto, en cualquier forma, queda «(fuente)» o «(/fuente)»; un comienzo suelto, «(fuente»
    private val SOURCE_TAG = Regex("""<(\s*/?\s*)fuente(\b[^<>]*>)?""", RegexOption.IGNORE_CASE)

    fun neutral(text: String): String =
        SOURCE_TAG.replace(text) { tag ->
            if (tag.groupValues[2].isNotEmpty()) "(${if ('/' in tag.groupValues[1]) "/" else ""}fuente)" else "(" + tag.value.substring(1)
        }
}
