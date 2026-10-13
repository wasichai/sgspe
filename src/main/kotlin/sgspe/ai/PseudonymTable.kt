package sgspe.ai

import java.util.EnumMap
import java.util.UUID

// las equivalencias de una llamada (ai-architecture.md §12.2): cada dato que el texto nombra con su marcador de la llamada
// ([PERSONA_1], [TELEFONO_1]), el mismo para el mismo dato (una persona por su id, lo demás por su valor plegado), y los valores
// restringidos que ya se enmascararon, reservados para el texto libre que siga. vive solo en memoria: nunca se guarda, se
// registra ni se escribe en un log, y su toString no dice nada. las herramientas del asistente la usan desde los hilos de Embabel,
// por eso sincroniza
class PseudonymTable {
    private class Entry(
        val marker: String,
        val value: String,
        val personId: UUID?
    )

    private val byKey = HashMap<String, Entry>()
    private val byMarker = HashMap<String, Entry>()
    private val counters = EnumMap<Marker, Int>(Marker::class.java)
    private val reservedValues = LinkedHashMap<String, RestrictedValue>()

    @Synchronized
    fun mark(
        kind: Marker,
        value: String,
        personId: UUID? = null
    ): String {
        val key = if (personId != null) "${kind.name}:$personId" else "${kind.name}:${Folding.fold(Folding.nfc(value).trim())}"
        byKey[key]?.let { return it.marker }
        val number = (counters[kind] ?: 0) + 1
        counters[kind] = number
        val entry = Entry("[${kind.name}_$number]", value, personId)
        byKey[key] = entry
        byMarker[entry.marker] = entry
        return entry.marker
    }

    // un valor restringido ya enmascarado: si se distingue, se enmascara también en el texto que siga (decisión 3 del plan 7.2a)
    @Synchronized
    fun reserve(
        kind: Marker,
        value: String
    ) {
        if (Pseudonymizer.distinctive(value)) reservedValues.putIfAbsent(Folding.fold(value.trim()), RestrictedValue(kind, value.trim()))
    }

    @Synchronized
    fun reserved(): List<RestrictedValue> = reservedValues.values.toList()

    // la respuesta de AI-05: cada marcador de la llamada vuelve a su valor; los reservados y los que la tabla no conoce quedan. un
    // valor numerado que después resultó restringido (visto primero en texto libre y luego en su campo) no vuelve: queda su
    // marcador sin número
    @Synchronized
    fun restore(text: String): String =
        MARKER.replace(text) { match ->
            val entry = byMarker[match.value] ?: return@replace match.value
            if (restricted(entry.value)) "[${match.groupValues[1]}]" else entry.value
        }

    // el valor contiene uno reservado, comparados sin tildes, mayúsculas, espacios ni signos (un teléfono con o sin espacios ni +51)
    private fun restricted(value: String): Boolean {
        val key = compact(value)
        return reservedValues.values.any { reserved -> compact(reserved.value).let { it.isNotEmpty() && it in key } }
    }

    private fun compact(value: String): String = Folding.fold(Folding.nfc(value)).filter { it.isLetterOrDigit() || it == '@' }

    // lo que se guarda (7.2b): una persona registrada a [PERSONA:<id>]; las demás, y lo detectado por formato, a su marcador sin
    // número ni valor ([PERSONA], [DOCUMENTO], [TELEFONO], [CORREO])
    @Synchronized
    fun stable(text: String): String =
        MARKER.replace(text) { match ->
            val person = byMarker[match.value]?.personId
            if (person != null) "[PERSONA:$person]" else "[${match.groupValues[1]}]"
        }

    override fun toString(): String = "PseudonymTable(${byMarker.size} marcadores)"

    companion object {
        internal val MARKER = Regex("""\[(PERSONA|DOCUMENTO|TELEFONO|CORREO|CONTACTO|DATO_SENSIBLE|CITA)_\d+]""")
    }
}
