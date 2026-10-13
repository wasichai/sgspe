package sgspe.ai

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// un registro que AI-07 compara, como lo lee quien pide la corrida (ai-architecture.md §4.1): los nombres de una persona (sus
// nombres y apellidos), de una organización (su nombre y sus alternative_names) o de un grupo de interés (los de su persona u
// organización); el texto de una QCS (description) o de un compromiso (title); el proyecto, la fecha, la localidad y el alta
data class DuplicateRecord(
    val objectName: String,
    val id: UUID,
    val names: List<String> = emptyList(),
    val text: String? = null,
    val project: UUID? = null,
    val date: LocalDate? = null,
    val locality: UUID? = null,
    val createdAt: Instant? = null,
    // el vínculo de duplicado que ya tiene (grievance.duplicate_of): un par ya vinculado no se vuelve a proponer
    val duplicateOf: UUID? = null
)

// por qué las reglas juntan dos registros; el texto es el que queda en reasons de la sugerencia
enum class DuplicateReason(
    val text: String
) {
    SAME_NAME("Mismo nombre normalizado"),
    ALTERNATIVE_NAME("El nombre de uno es un nombre alternativo del otro"),
    SIMILAR_NAME("Nombres parecidos"),
    SIMILAR_TEXT("Textos parecidos"),
    SAME_PROJECT("Mismo proyecto"),
    CLOSE_DATE("Fechas cercanas"),
    SAME_LOCALITY("Misma localidad")
}

// un par candidato con sus razones y su puntaje (la similitud más 0,1 por razón de apoyo): first es el registro más antiguo
data class DuplicatePair(
    val first: DuplicateRecord,
    val second: DuplicateRecord,
    val reasons: List<DuplicateReason>,
    val score: Double
)

// las reglas deterministas de AI-07 (especificación de F7 §2.2.10, ai-architecture.md §4.1): solo registros del mismo objeto; los
// de persona, organización y grupo de interés se juntan por un nombre normalizado igual (en cualquier orden de palabras, sin
// tildes ni mayúsculas), por un nombre alternativo o por nombres parecidos; los de QCS y compromisos, dentro del mismo proyecto,
// por textos parecidos, o algo parecidos con fecha cercana y la misma localidad. un par ya vinculado (duplicate_of) o ya
// propuesto antes (`known`, en cualquier orden) no vuelve. los pares van por puntaje, hasta `max`, y el modelo compara solo esos.
// Kotlin puro
object DuplicateCandidates {
    // los objetos que AI-07 compara; los de nombre y los de texto
    val NAMED = setOf("person", "organization", "stakeholder")
    val TEXTUAL = setOf("grievance", "inquiry", "stakeholder_request", "commitment")
    val OBJECTS = NAMED + TEXTUAL

    const val CLOSE_DAYS = 7L
    private const val SIMILAR_NAME = 0.6
    private const val SIMILAR_TEXT = 0.5
    private const val RELATED_TEXT = 0.3
    private const val MIN_SHARED_NAME_WORDS = 2
    private const val MIN_SHARED_TEXT_WORDS = 3
    private const val MIN_TEXT_WORD = 4
    private const val SUPPORT = 0.1

    // las palabras que no distinguen un pedido de otro
    private val STOPWORDS =
        (
            "para como pero este esta esto estos estas entre sobre desde hasta porque cuando donde segun tambien ellos " +
                "ellas nuestra nuestro nuestros nuestras sus unos unas solicita solicitan pide piden pidio pidieron senor senora sres comunidad"
        ).split(" ").toSet()

    // las palabras plegadas de un texto (minúsculas, sin tildes)
    fun words(text: String): List<String> = Folding.words(Folding.fold(Folding.nfc(text))).map { it.value }

    // un nombre normalizado: sus palabras plegadas, ordenadas («Ejemplo, Ana» = «ANA EJEMPLO»)
    fun normalizedName(name: String): String = words(name).sorted().joinToString(" ")

    // las palabras de un texto que cuentan para compararlo: de cuatro letras o más y que no son vacías
    fun significant(text: String): Set<String> = words(text).filter { it.length >= MIN_TEXT_WORD && it !in STOPWORDS }.toSet()

    fun jaccard(
        a: Set<String>,
        b: Set<String>
    ): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else (a intersect b).size.toDouble() / (a union b).size

    fun pairs(
        records: List<DuplicateRecord>,
        max: Int,
        known: Set<Set<UUID>> = emptySet()
    ): List<DuplicatePair> {
        val out = mutableListOf<DuplicatePair>()
        for ((objectName, group) in records.filter { it.objectName in OBJECTS }.distinctBy { it.objectName to it.id }.groupBy { it.objectName }) {
            val sorted = group.sortedWith(compareBy<DuplicateRecord>({ it.createdAt ?: Instant.MAX }, { it.id.toString() }))
            val prepared = sorted.map { Prepared(it) }
            for (i in prepared.indices) {
                for (j in i + 1 until prepared.size) {
                    val (a, b) = prepared[i].record to prepared[j].record
                    if (a.duplicateOf == b.id || b.duplicateOf == a.id || setOf(a.id, b.id) in known) continue
                    val pair = if (objectName in NAMED) named(prepared[i], prepared[j]) else textual(prepared[i], prepared[j])
                    pair?.let { out += it }
                }
            }
        }
        return out
            .sortedWith(compareByDescending<DuplicatePair> { it.score }.thenBy { it.first.id.toString() }.thenBy { it.second.id.toString() })
            .take(max)
    }

    // las formas comparables de un registro, calculadas una vez
    private class Prepared(
        val record: DuplicateRecord
    ) {
        val names: List<String> = record.names.map { normalizedName(it) }.filter { it.isNotEmpty() }
        val nameWords: List<Set<String>> = names.map { it.split(" ").toSet() }
        val text: Set<String> = significant(record.text.orEmpty())
    }

    private fun named(
        a: Prepared,
        b: Prepared
    ): DuplicatePair? {
        if (a.names.isEmpty() || b.names.isEmpty()) return null
        if (a.record.objectName == "stakeholder" && a.record.project != b.record.project) return null
        val reasons = mutableListOf<DuplicateReason>()
        var similarity = 0.0
        val equal =
            a.names.indices
                .flatMap { i -> b.names.indices.map { j -> i to j } }
                .filter { (i, j) -> a.names[i] == b.names[j] }
        if (equal.isNotEmpty()) {
            similarity = 1.0
            reasons += if (equal.any { (i, j) -> i == 0 && j == 0 }) DuplicateReason.SAME_NAME else DuplicateReason.ALTERNATIVE_NAME
        } else {
            val best =
                a.nameWords
                    .flatMap { x ->
                        b.nameWords.map { y -> if ((x intersect y).size >= MIN_SHARED_NAME_WORDS) jaccard(x, y) else 0.0 }
                    }.maxOrNull() ?: 0.0
            if (best < SIMILAR_NAME) return null
            similarity = best
            reasons += DuplicateReason.SIMILAR_NAME
        }
        support(a.record, b.record, reasons)
        return DuplicatePair(a.record, b.record, reasons, similarity + SUPPORT * (reasons.size - 1))
    }

    private fun textual(
        a: Prepared,
        b: Prepared
    ): DuplicatePair? {
        if (a.record.project == null || a.record.project != b.record.project) return null
        val shared = (a.text intersect b.text).size
        val similarity = if (shared >= MIN_SHARED_TEXT_WORDS) jaccard(a.text, b.text) else 0.0
        val close = close(a.record.date, b.record.date)
        val sameLocality = a.record.locality != null && a.record.locality == b.record.locality
        if (similarity < SIMILAR_TEXT && !(similarity >= RELATED_TEXT && close && sameLocality)) return null
        val reasons = mutableListOf(DuplicateReason.SIMILAR_TEXT)
        support(a.record, b.record, reasons)
        return DuplicatePair(a.record, b.record, reasons, similarity + SUPPORT * (reasons.size - 1))
    }

    private fun support(
        a: DuplicateRecord,
        b: DuplicateRecord,
        reasons: MutableList<DuplicateReason>
    ) {
        if (a.project != null && a.project == b.project) reasons += DuplicateReason.SAME_PROJECT
        if (close(a.date, b.date)) reasons += DuplicateReason.CLOSE_DATE
        if (a.locality != null && a.locality == b.locality) reasons += DuplicateReason.SAME_LOCALITY
    }

    private fun close(
        a: LocalDate?,
        b: LocalDate?
    ): Boolean = a != null && b != null && kotlin.math.abs(ChronoUnit.DAYS.between(a, b)) <= CLOSE_DAYS
}
