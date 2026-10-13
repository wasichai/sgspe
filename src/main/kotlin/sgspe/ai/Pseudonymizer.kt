package sgspe.ai

import java.util.UUID

// los tipos de dato personal o restringido que la IA nunca ve en claro (ai-architecture.md §12.2, security.md §7.8 y §7.9).
// dentro de una llamada, lo que el texto nombra va con número ([PERSONA_1]) y PseudonymTable lo restaura o lo pasa a estable;
// el valor de un campo restringido va siempre sin número ([PERSONA], [DOCUMENTO]…): nadie lo restaura (decisión 3 del plan 7.2a)
enum class Marker {
    PERSONA,
    DOCUMENTO,
    TELEFONO,
    CORREO,
    CONTACTO,
    DATO_SENSIBLE,
    CITA;

    // el marcador tipado sin número ni id
    val reserved: String get() = "[$name]"
}

// una persona del diccionario de nombres: una person con sus nombres, como la lee quien pide (o todas, en JOB-07)
data class KnownPerson(
    val id: UUID?,
    val givenNames: String?,
    val surnames: String?
) {
    // cómo vuelve al restaurar la respuesta de AI-05
    val displayName: String
        get() = listOfNotNull(givenNames?.trim()?.ifEmpty { null }, surnames?.trim()?.ifEmpty { null }).joinToString(" ")
}

// un valor restringido que la llamada conoce (los nombres de los participantes de una interacción, el documento de una persona):
// sale con el marcador tipado de su campo, sin número
data class RestrictedValue(
    val kind: Marker,
    val value: String
)

// minúsculas y sin tildes, letra por letra: el texto plegado mide lo mismo que el original, así que una posición en uno es la misma
// en el otro. «ñ» pliega a «n» y «ü» a «u»
internal object Folding {
    private const val FROM = "áàäâãéèëêíìïîóòöôõúùüûñç"
    private const val TO = "aaaaaeeeeiiiiooooouuuunc"
    private val WORD = Regex("""[\p{L}\p{N}]+""")

    fun fold(text: String): String {
        val out = CharArray(text.length)
        for (i in text.indices) {
            val c = text[i].lowercaseChar()
            val at = FROM.indexOf(c)
            out[i] = if (at >= 0) TO[at] else c
        }
        return String(out)
    }

    // las palabras (letras y dígitos) con su posición
    fun words(text: String): List<MatchResult> = WORD.findAll(text).toList()
}

// el diccionario de nombres de una llamada, por palabras plegadas: «Ana Ejemplo», «ANA EJEMPLO», «Ejemplo, Ana» y «Ána Éjemplo»
// son la misma persona. una persona sin nombres o sin apellidos no entra: un nombre de una sola palabra confundiría palabras
// comunes con personas
class NameDictionary(
    people: Collection<KnownPerson>
) {
    private class Entry(
        val words: List<String>,
        val person: KnownPerson
    )

    private val byFirst: Map<String, List<Entry>> =
        people
            .flatMap { person -> variants(person).map { Entry(it, person) } }
            .groupBy { it.words.first() }
            .mapValues { (_, entries) -> entries.sortedByDescending { it.words.size } }

    // las personas nombradas en el texto, sin solaparse: en cada posición, el nombre más largo
    fun matches(text: String): List<Pair<IntRange, KnownPerson>> {
        if (byFirst.isEmpty()) return emptyList()
        val words = Folding.words(Folding.fold(text))
        val found = mutableListOf<Pair<IntRange, KnownPerson>>()
        var i = 0
        while (i < words.size) {
            val entry = byFirst[words[i].value]?.firstOrNull { fits(text, words, i, it.words) }
            if (entry == null) {
                i++
                continue
            }
            val last = words[i + entry.words.size - 1]
            found += (words[i].range.first..last.range.last) to entry.person
            i += entry.words.size
        }
        return found
    }

    private fun fits(
        text: String,
        words: List<MatchResult>,
        start: Int,
        name: List<String>
    ): Boolean {
        if (start + name.size > words.size) return false
        for (k in name.indices) {
            if (words[start + k].value != name[k]) return false
            if (k > 0 && !separator(text.substring(words[start + k - 1].range.last + 1, words[start + k].range.first))) return false
        }
        return true
    }

    companion object {
        val EMPTY = NameDictionary(emptyList())

        // entre dos palabras de un nombre: espacios y a lo sumo una coma («Ejemplo, Ana»)
        private fun separator(gap: String): Boolean = gap.isNotEmpty() && gap.all { it.isWhitespace() || it == ',' } && gap.count { it == ',' } <= 1

        private fun variants(person: KnownPerson): List<List<String>> {
            val given = Folding.words(Folding.fold(person.givenNames.orEmpty())).map { it.value }
            val surnames = Folding.words(Folding.fold(person.surnames.orEmpty())).map { it.value }
            if (given.isEmpty() || surnames.isEmpty()) return emptyList()
            return listOf(given + surnames, surnames + given).distinct()
        }
    }
}

// la seudonimización antes de cada llamada a un modelo (ai-architecture.md §12.2, RQ-AI-009), en Kotlin puro. en este orden de
// preferencia: los valores restringidos conocidos (los de la llamada y los que la tabla ya reservó) con su marcador tipado sin
// número; los correos, los teléfonos y los documentos de identidad por su formato, y las personas del diccionario, con un marcador
// de la llamada que guarda `table`, numerado en el orden del texto. los UUID quedan intactos: son los ids con que el modelo pide un
// registro. nunca lanza con el texto en el mensaje
object Pseudonymizer {
    private val UUID_SPAN = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
    private val EMAIL = Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}-]+(?:\.[\p{L}\p{N}-]+)*\.\p{L}{2,}""")

    // un celular del Perú (9 dígitos que empiezan con 9, con o sin +51) o un fijo con su código de ciudad
    private val PHONE =
        Regex(
            """(?<![\p{L}\p{N}+])(?:\+?51[\s.-]?)?9\d{2}[\s.-]?\d{3}[\s.-]?\d{3}(?!\d)|(?<![\p{L}\p{N}+])\(?0\d{1,2}\)?[\s.-]?\d{3}[\s.-]?\d{3,4}(?!\d)"""
        )

    // un RUC de persona natural (10…, 15… o 17…, 11 dígitos) o un DNI (8 dígitos), nunca dentro de otra cifra ni de un monto
    private val DOCUMENT = Regex("""(?<![\p{L}\p{N}.,])(?:(?:10|15|17)\d{9}|\d{8})(?![\p{L}\p{N}]|[.,]\d)""")

    private class Candidate(
        val range: IntRange,
        val priority: Int,
        val replace: (PseudonymTable) -> String
    )

    fun pseudonymize(
        text: String,
        names: NameDictionary,
        table: PseudonymTable,
        restricted: Collection<RestrictedValue> = emptyList()
    ): String {
        if (text.isEmpty()) return text
        val protected = UUID_SPAN.findAll(text).map { it.range }.toList()
        val folded = Folding.fold(text)
        val candidates = mutableListOf<Candidate>()
        for (value in (restricted + table.reserved()).filter { distinctive(it.value) }) {
            for (range in occurrences(folded, Folding.fold(value.value.trim()))) candidates += Candidate(range, 0) { value.kind.reserved }
        }
        EMAIL.findAll(text).forEach { m -> candidates += Candidate(m.range, 1) { it.mark(Marker.CORREO, m.value) } }
        PHONE.findAll(text).forEach { m -> candidates += Candidate(m.range, 2) { it.mark(Marker.TELEFONO, m.value) } }
        DOCUMENT.findAll(text).forEach { m -> candidates += Candidate(m.range, 3) { it.mark(Marker.DOCUMENTO, m.value) } }
        names.matches(text).forEach { (range, person) ->
            candidates += Candidate(range, 4) { it.mark(Marker.PERSONA, person.displayName, person.id) }
        }
        val accepted = mutableListOf<Candidate>()
        val order = compareBy<Candidate>({ it.priority }, { it.range.first }, { -(it.range.last - it.range.first) })
        for (candidate in candidates.sortedWith(order)) {
            if (protected.any { overlaps(it, candidate.range) } || accepted.any { overlaps(it.range, candidate.range) }) continue
            accepted += candidate
        }
        if (accepted.isEmpty()) return text
        val out = StringBuilder(text.length)
        var at = 0
        for (candidate in accepted.sortedBy { it.range.first }) {
            out.append(text, at, candidate.range.first).append(candidate.replace(table))
            at = candidate.range.last + 1
        }
        return out.append(text, at, text.length).toString()
    }

    // un valor restringido se busca en el texto libre solo si se distingue: dos palabras o más, seis dígitos o más, o un correo.
    // una palabra sola («Flores») confundiría el texto con un dato, y un UUID es un id, no un dato
    fun distinctive(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty() || UUID_SPAN.matches(text)) return false
        return text.count { it.isDigit() } >= 6 || '@' in text || Folding.words(text).size >= 2
    }

    // las apariciones de `needle` (plegado) en `folded` que no empiezan ni terminan dentro de una palabra
    private fun occurrences(
        folded: String,
        needle: String
    ): List<IntRange> {
        if (needle.isEmpty()) return emptyList()
        val found = mutableListOf<IntRange>()
        var from = folded.indexOf(needle)
        while (from >= 0) {
            val end = from + needle.length
            val before = from == 0 || !folded[from - 1].isLetterOrDigit()
            val after = end == folded.length || !folded[end].isLetterOrDigit()
            if (before && after) found += from until end
            from = folded.indexOf(needle, from + 1)
        }
        return found
    }

    private fun overlaps(
        a: IntRange,
        b: IntRange
    ): Boolean = a.first <= b.last && b.first <= a.last
}
