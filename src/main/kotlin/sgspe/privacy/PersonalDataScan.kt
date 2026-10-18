package sgspe.privacy

// el aviso determinista de datos personales (especificación de F8 §2.2.9, ASM-137): en la narrativa y en los textos de
// registro de cada sección busca nombres de `person` del proyecto, números de 8 dígitos (un DNI), teléfonos y correos, y
// avisa POSSIBLE_PERSONAL_DATA con la clave de la sección, una vez por sección. es una ayuda para la lista de verificación
// de EXTERNO, no una garantía: un nombre que no está registrado no se ve. las menciones [[…:<id>]] no cuentan: se quitan
// antes de buscar, también las escapadas (Mentions.scrub). el aviso nunca lleva lo encontrado
object PersonalDataScan {
    const val CODE = "POSSIBLE_PERSONAL_DATA"

    private val EIGHT_DIGITS = Regex("""(?<![0-9A-Za-z])\d{8}(?![0-9A-Za-z])""")
    private val PHONE = Regex("""(?<![0-9])(\+?51[ -]?)?9\d{2}[ -]?\d{3}[ -]?\d{3}(?![0-9])""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    // `texts`: los textos de cada sección por su clave; `names`: los nombres completos de las personas del proyecto (de dos
    // palabras o más: un nombre de pila suelto daría avisos en cualquier texto)
    fun scan(
        texts: Map<String, List<String>>,
        names: Collection<String>
    ): List<ReportWarning> {
        val wanted = names.map { normalize(it) }.filter { it.split(' ').size >= 2 }.distinct()
        return texts.mapNotNull { (section, values) ->
            val found =
                values.any { raw ->
                    val text = Mentions.TOKEN.replace(Mentions.scrub(raw), " ")
                    val plain = normalize(text)
                    EIGHT_DIGITS.containsMatchIn(text) ||
                        PHONE.containsMatchIn(text) ||
                        EMAIL.containsMatchIn(text) ||
                        wanted.any { " $it " in " $plain " }
                }
            if (found) ReportWarning(CODE, section) else null
        }
    }

    // minúsculas, sin tildes y con un solo espacio entre palabras
    private fun normalize(text: String): String =
        java.text.Normalizer
            .normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-zñ0-9]+"), " ")
            .trim()
}
