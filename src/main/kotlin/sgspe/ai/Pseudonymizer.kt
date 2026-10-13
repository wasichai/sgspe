package sgspe.ai

import java.text.Normalizer
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

// una persona del diccionario de nombres: una persona con sus nombres, como la lee quien pide (o todas, en JOB-07)
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
            val c = text[i].lowercaseChar().let { if (it == '’') '\'' else it }
            val at = FROM.indexOf(c)
            out[i] = if (at >= 0) TO[at] else c
        }
        return String(out)
    }

    // las palabras (letras y dígitos) con su posición
    fun words(text: String): List<MatchResult> = WORD.findAll(text).toList()

    // la forma compuesta (NFC): «Á» escrita como «A» más el acento suelto es la misma letra que «Á»
    fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    // `name` (palabras plegadas) empieza en la palabra `start` de `words` (las de `folded`), separadas como en un nombre
    fun fits(
        folded: String,
        words: List<MatchResult>,
        start: Int,
        name: List<String>
    ): Boolean {
        if (start + name.size > words.size) return false
        for (k in name.indices) {
            if (words[start + k].value != name[k]) return false
            if (k > 0 && !separator(folded.substring(words[start + k - 1].range.last + 1, words[start + k].range.first))) return false
        }
        return true
    }

    // entre dos palabras de un nombre: espacios (también saltos de línea y espacios duros) y a lo sumo una coma («Ejemplo, Ana»),
    // o un solo apóstrofo o guion pegado a las dos palabras («Q'orianka», «Mamani-Ejemplo»)
    private fun separator(gap: String): Boolean =
        gap == "'" || gap == "-" || (gap.isNotEmpty() && gap.all { it.isWhitespace() || it == ',' } && gap.count { it == ',' } <= 1)
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
        val folded = Folding.fold(text)
        val words = Folding.words(folded)
        val found = mutableListOf<Pair<IntRange, KnownPerson>>()
        var i = 0
        while (i < words.size) {
            val entry = byFirst[words[i].value]?.firstOrNull { Folding.fits(folded, words, i, it.words) }
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

    companion object {
        val EMPTY = NameDictionary(emptyList())

        // las formas en que se nombra a una persona, siempre de dos palabras o más: nombres y apellidos completos o con solo el primero
        // de cada uno, en los dos órdenes, y los dos apellidos solos. un apellido compuesto («Mamani-Ejemplo») cuenta como dos palabras
        private val PARTICLES = setOf("de", "del", "la", "las", "los", "y", "e")

        // el primer apellido con sus partículas («de la Cruz», «del Carmen»): «Juan de» o «Luz del» no nombran a nadie
        private fun firstSurname(surnames: List<String>): List<String> {
            val core = surnames.indexOfFirst { it !in PARTICLES }
            return if (core < 0) surnames else surnames.take(core + 1)
        }

        private fun variants(person: KnownPerson): List<List<String>> {
            val given = Folding.words(Folding.fold(Folding.nfc(person.givenNames.orEmpty()))).map { it.value }
            val surnames = Folding.words(Folding.fold(Folding.nfc(person.surnames.orEmpty()))).map { it.value }
            if (given.isEmpty() || surnames.isEmpty()) return emptyList()
            val names = listOf(given, given.take(1)).distinct()
            val last = listOf(surnames, firstSurname(surnames)).distinct()
            val forms = mutableListOf<List<String>>()
            for (a in names) {
                for (b in last) {
                    forms += a + b
                    forms += b + a
                }
            }
            if (surnames.size >= 2) forms += surnames
            return forms.filter { it.size >= 2 }.distinct()
        }
    }
}

// la seudonimización antes de cada llamada a un modelo (ai-architecture.md §12.2, RQ-AI-009), en Kotlin puro. en este orden de
// preferencia: los valores restringidos conocidos (los de la llamada y los que la tabla ya reservó) con su marcador tipado sin
// número; los correos, los teléfonos y los documentos de identidad por su formato, y las personas del diccionario, con un marcador
// de la llamada que guarda `table`, numerado en el orden del texto. los UUID quedan intactos: son los ids con que el modelo pide un
// registro. nunca lanza con el texto en el mensaje. solo para texto libre: nunca se le pasa un JSON entero (sus números,
// como un monto o un conteo, se tomarían por documentos o teléfonos); quien lo use sobre resultados de herramientas lo hace por
// cada valor de texto
object Pseudonymizer {
    private val UUID_SPAN = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
    private val DOMAIN = Regex("""[\p{L}\p{N}-]+(?:\.[\p{L}\p{N}-]+)*\.\p{L}{2,}""")

    private fun localChar(c: Char): Boolean = c.isLetterOrDigit() || c == '.' || c == '_' || c == '%' || c == '+' || c == '-'

    // los correos, en tiempo lineal: se parte de cada «@» y se ensancha hacia los lados (una expresión que empieza por el usuario
    // reintenta desde cada posición de un texto largo sin «@»)
    private fun emails(text: String): List<IntRange> {
        val found = mutableListOf<IntRange>()
        var at = text.indexOf('@')
        var floor = 0
        while (at >= 0) {
            var start = at
            while (start > floor && localChar(text[start - 1])) start--
            var end = at + 1
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '-')) end++
            val domain = if (start < at && end > at + 1) DOMAIN.matchAt(text.substring(at + 1, end), 0) else null
            if (domain != null) {
                val last = at + domain.value.length
                found += start..last
                floor = last + 1
            }
            at = text.indexOf('@', at + 1)
        }
        return found
    }

    // un celular del Perú (9 dígitos que empiezan con 9, con o sin +51, agrupados 3-3-3, 3-2-2-2 o 4-5) o un fijo con su código de
    // ciudad (con 0 o con +51)
    private val PHONE =
        Regex(
            """(?<![\p{L}\p{N}+])(?:\+?51[\s.-]?)?9(?:\d{2}[\s.-]?\d{3}[\s.-]?\d{3}|\d{2}[\s.-]?\d{2}[\s.-]?\d{2}[\s.-]?\d{2}|\d{3}[\s.-]?\d{5})(?!\d)""" +
                """|(?<![\p{L}\p{N}+])\(?0\d{1,2}\)?[\s.-]?\d{3}[\s.-]?\d{3,4}(?!\d)""" +
                """|(?<![\p{L}\p{N}+])\+51[\s.-]?\(?\d{1,2}\)?[\s.-]?\d{3}[\s.-]?\d{3,4}(?!\d)"""
        )

    // un RUC de persona natural (10…, 15… o 17…, 11 dígitos) o un DNI (8 dígitos), nunca dentro de otra cifra ni de un monto
    // lo que lo precede o sigue no es otra cifra ni la parte decimal de un monto («40123456.50»); una lista («40123456,40123457») y
    // un prefijo pegado («DNI40123456») sí se enmascaran
    private val DOCUMENT =
        Regex("""(?<!\p{N})(?<!(?<!\p{N})\d{1,3}[.,])(?:(?:10|15|17)\d{9}|\d{8})(?!\p{N})(?![.,]\d{1,2}(?!\d))""")

    private class Candidate(
        val range: IntRange,
        val priority: Int,
        val replace: (PseudonymTable) -> String
    )

    fun pseudonymize(
        input: String,
        names: NameDictionary,
        table: PseudonymTable,
        restricted: Collection<RestrictedValue> = emptyList()
    ): String {
        if (input.isEmpty()) return input
        // un marcador escrito en el texto («[PERSONA_1]») dejaría de distinguirse de los que se generan: restaurar o guardar le
        // atribuiría el texto a una persona registrada. se vuelve inofensivo antes de cualquier reemplazo
        val text = PseudonymTable.MARKER.replace(Folding.nfc(input)) { "(${it.value.substring(1, it.value.length - 1)})" }
        val taken = java.util.TreeMap<Int, Int>()
        UUID_SPAN.findAll(text).forEach { taken[it.range.first] = it.range.last }
        val folded = Folding.fold(text)
        val foldedWords = Folding.words(folded)
        val candidates = mutableListOf<Candidate>()
        val known = (restricted + table.reserved()).associate { key(it.value) to it.kind }
        for (value in (restricted + table.reserved()).filter { distinctive(it.value) }) {
            val needle = Folding.fold(Folding.nfc(value.value).trim())
            val ranges = occurrences(folded, needle) + wordSequences(folded, foldedWords, needle)
            for (range in ranges) candidates += Candidate(range, 0) { value.kind.reserved }
        }
        emails(text).forEach { r -> candidates += Candidate(r, 1) { marked(it, known, Marker.CORREO, text.substring(r)) } }
        PHONE.findAll(text).forEach { m -> candidates += Candidate(m.range, 2) { marked(it, known, Marker.TELEFONO, m.value) } }
        DOCUMENT.findAll(text).forEach { m -> candidates += Candidate(m.range, 3) { marked(it, known, Marker.DOCUMENTO, m.value) } }
        names.matches(text).forEach { (range, person) ->
            candidates += Candidate(range, 4) { it.mark(Marker.PERSONA, person.displayName, person.id) }
        }
        val accepted = mutableListOf<Candidate>()
        val order = compareBy<Candidate>({ it.priority }, { it.range.first }, { -(it.range.last - it.range.first) })
        for (candidate in candidates.sortedWith(order)) {
            // los tramos tomados (ids y reemplazos) no se solapan: basta mirar el último que empieza antes de que acabe este
            val before = taken.floorEntry(candidate.range.last)
            if (before != null && before.value >= candidate.range.first) continue
            taken[candidate.range.first] = candidate.range.last
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

    // la clave de un valor para compararlo con lo hallado por formato: sin tildes ni mayúsculas, espacios ni signos
    private fun key(value: String): String = Folding.fold(Folding.nfc(value)).filter { it.isLetterOrDigit() || it == '@' }

    // lo hallado por formato que es un valor restringido conocido sale con el marcador tipado sin número, que nadie restaura
    // («DNI40123456» pegado, que la búsqueda por palabras no ve)
    private fun marked(
        table: PseudonymTable,
        known: Map<String, Marker>,
        kind: Marker,
        value: String
    ): String = known[key(value)]?.reserved ?: table.mark(kind, value)

    // un valor restringido se busca en el texto libre solo si se distingue: dos palabras o más, seis dígitos o más, o un correo.
    // una palabra sola («Flores») confundiría el texto con un dato, y un UUID es un id, no un dato
    fun distinctive(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty() || UUID_SPAN.matches(text)) return false
        return text.count { it.isDigit() } >= 6 || '@' in text || Folding.words(text).size >= 2
    }

    // las apariciones de las palabras de `needle` (plegado) como un nombre: con otros espacios, saltos de línea o una coma, y con el
    // primer nombre al final («Ccori Ejemplo, Rosa»). un valor con cifras se busca solo tal cual
    private fun wordSequences(
        folded: String,
        words: List<MatchResult>,
        needle: String
    ): List<IntRange> {
        val parts = Folding.words(needle).map { it.value }
        if (parts.size < 2) return emptyList()
        val forms = if (parts.any { part -> part.any { it.isDigit() } }) listOf(parts) else parts.indices.map { parts.drop(it) + parts.take(it) }
        val found = mutableListOf<IntRange>()
        for (form in forms) {
            for (i in words.indices) {
                if (Folding.fits(folded, words, i, form)) found += words[i].range.first..words[i + form.size - 1].range.last
            }
        }
        return found
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
}
