package sgspe.ai

import org.springframework.stereotype.Component
import java.util.UUID

// la salida estructurada de AI-06 (prompt ai-06): la respuesta y sus citas, cada una con el número del fragmento y el pedazo
// copiado de él. los valores por defecto dejan a Embabel leer una salida incompleta, que la validación descarta
data class KnowledgeAnswer(
    val answer: String = "",
    val citations: List<KnowledgeQuote> = emptyList()
)

data class KnowledgeQuote(
    val fragment: Int = 0,
    val quote: String = ""
)

// una cita de la respuesta de POST /api/sgspe/ai/knowledge/questions (api.md §4.14): la evidencia, la posición del fragmento y
// el pedazo citado, con marcadores estables
data class KnowledgeCitation(
    val evidenceId: String,
    val chunkIndex: Int,
    val quote: String
)

// la respuesta de AI-06: el texto con marcadores estables ([PERSONA:<id>], [PERSONA], [DOCUMENTO]…), que el portal re-identifica
// al mostrar (especificación de F7 §2.2.11); sus citas; y la ai_interaction de la llamada, null si no se llamó al modelo
data class KnowledgeResponse(
    val answer: String,
    val citations: List<KnowledgeCitation>,
    val interaction: String?
)

// las citas de AI-06, puras (ai-architecture.md §6.1 y §13.3): los fragmentos van numerados entre etiquetas <fragmento> que su
// contenido no puede abrir ni cerrar, y una cita vale solo si su fragmento existe y su pedazo aparece tal cual, salvo espacios,
// en el fragmento seudonimizado que recibió el modelo. así no hay fuente inventada
object KnowledgeCitations {
    const val MAX = 10
    private const val MAX_QUOTE = 1_000
    private val TAG = Regex("""<(\s*/?\s*)(fragmento|pregunta)""", RegexOption.IGNORE_CASE)
    private val FRAGMENT = Regex("""<fragmento n="(\d+)">\n(.*?)\n</fragmento>""", RegexOption.DOT_MATCHES_ALL)
    private val SPACE = Regex("""\s+""")

    // un texto que no abre ni cierra una etiqueta <fragmento> ni <pregunta>: «<» pasa a «(»
    fun neutral(text: String): String = TAG.replace(text) { "(" + it.groupValues[1] + it.groupValues[2] }

    // los fragmentos como los recibe el modelo, numerados desde 1
    fun render(fragments: List<KnowledgeFragment>): String =
        fragments.withIndex().joinToString("\n\n") { (i, fragment) -> "<fragmento n=\"${i + 1}\">\n${neutral(fragment.content)}\n</fragmento>" }

    // los fragmentos seudonimizados, por número, del prompt que salió hacia el modelo
    fun sent(prompt: String): Map<Int, String> = FRAGMENT.findAll(prompt).associate { it.groupValues[1].toInt() to it.groupValues[2] }

    // las citas que valen, sin repetir, hasta MAX
    fun valid(
        quotes: List<KnowledgeQuote>,
        sent: Map<Int, String>
    ): List<KnowledgeQuote> =
        quotes
            .asSequence()
            .map { it.copy(quote = it.quote.trim()) }
            .filter { it.quote.isNotEmpty() && it.quote.length <= MAX_QUOTE }
            .filter { quote -> sent[quote.fragment]?.let { compact(it).contains(compact(quote.quote)) } == true }
            .distinctBy { it.fragment to compact(it.quote) }
            .take(MAX)
            .toList()

    private fun compact(text: String): String = text.replace(SPACE, " ").trim()
}

// la respuesta de AI-06 con su llamada (ai-architecture.md §13.3, pasos 5 a 7): la pregunta y los fragmentos se seudonimizan con
// una tabla nueva y el diccionario de quien pregunta (ModelCalls), el prompt en uso de AI-06 los lleva delimitados, el modelo
// responde con citas y la llamada queda en su ai_interaction. sin una cita que valga, la respuesta dice que no hay fuente (nunca
// responde sin fuente). lo que vuelve lleva marcadores estables: ni nombres ni documentos en claro
@Component
class KnowledgeAnswers(
    private val calls: ModelCalls
) {
    suspend fun answer(
        question: String,
        project: UUID,
        fragments: List<KnowledgeFragment>,
        names: NameDictionary
    ): KnowledgeResponse {
        require(fragments.isNotEmpty()) { "AI-06 no llama al modelo sin fragmentos" }
        val result =
            calls.call(
                ModelCall(
                    useCase = USE_CASE,
                    variables = mapOf(QUESTION to KnowledgeCitations.neutral(question), FRAGMENTS to KnowledgeCitations.render(fragments)),
                    names = names,
                    outputClass = KnowledgeAnswer::class.java,
                    inputReference = "sha256:" + Hashes.sha256(question),
                    project = project,
                    inputField = QUESTION,
                    summary = { output: KnowledgeAnswer -> "${output.citations.size} citas propuestas sobre ${fragments.size} fragmentos" }
                )
            )
        val quotes = KnowledgeCitations.valid(result.output.citations, KnowledgeCitations.sent(result.sent.text))
        val interaction = result.interaction.toString()
        if (quotes.isEmpty() || result.output.answer.isBlank()) return KnowledgeResponse(UNSUPPORTED, emptyList(), interaction)
        return KnowledgeResponse(
            result.table.stable(result.output.answer.trim()),
            quotes.map { quote ->
                val fragment = fragments[quote.fragment - 1]
                KnowledgeCitation(fragment.evidenceId.toString(), fragment.chunkIndex, result.table.stable(quote.quote))
            },
            interaction
        )
    }

    companion object {
        const val USE_CASE = "AI-06"
        const val QUESTION = "question"
        const val FRAGMENTS = "fragments"
        const val NO_EVIDENCE = "No hay evidencia sobre esto en los documentos del proyecto que usted puede leer."
        const val UNSUPPORTED = "No encontré en los documentos una cita que respalde una respuesta."

        // sin fragmento que pase los filtros: la respuesta sin llamar al modelo y sin ai_interaction
        fun noEvidence(): KnowledgeResponse = KnowledgeResponse(NO_EVIDENCE, emptyList(), null)
    }
}
