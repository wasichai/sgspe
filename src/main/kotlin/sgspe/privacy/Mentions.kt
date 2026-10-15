package sgspe.privacy

// las menciones de la narrativa (especificación de F8 §2.2.9, ASM-137): tokens Markdown [[person:<id>]],
// [[position:<id>]] o [[stakeholder:<id>]] que apuntan a un registro, nunca el nombre escrito. INTERNO los resuelve al
// nombre; EXTERNO al cargo y la organización, o al código GI-, y sin cargo a «una persona de <localidad>». una mención que
// el modelo no conoce sale como «una persona» en todo nivel: nunca se imprime el id
object Mentions {
    val TOKEN = Regex("""\[\[(person|position|stakeholder):([0-9a-fA-F-]{36})]]""")

    const val UNKNOWN = "una persona"

    // las claves («person:<id>») que cita un texto, en su orden y sin repetir
    fun keys(text: String): List<String> =
        TOKEN
            .findAll(text)
            .map { "${it.groupValues[1]}:${it.groupValues[2].lowercase()}" }
            .distinct()
            .toList()

    fun resolve(
        text: String,
        people: Map<String, MentionTarget>,
        level: Disclosure
    ): String =
        TOKEN.replace(text) { match ->
            val target = people["${match.groupValues[1]}:${match.groupValues[2].lowercase()}"]
            if (target == null) UNKNOWN else cite(target, level)
        }

    fun cite(
        target: MentionTarget,
        level: Disclosure
    ): String =
        when (level) {
            Disclosure.INTERNO -> target.name
            else -> {
                val role = listOfNotNull(target.position, target.organization).joinToString(", ").ifEmpty { null }
                role
                    ?: target.stakeholderCode
                    ?: target.locality?.let { "una persona de $it" }
                    ?: UNKNOWN
            }
        }
}
