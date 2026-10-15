package sgspe.deliverable

import sgspe.privacy.Block
import sgspe.privacy.Disclosure
import sgspe.privacy.MentionTarget
import sgspe.privacy.Mentions

// lo que AI-04 manda al modelo de una sección (especificación de F8 §3.9; decisión 16 del plan 8.2c): los bloques que el tipo
// arma para ella como texto con Markdown, sin los bloques ni las celdas marcados (restringidos, titulares, registros, el aviso
// que solo muestra EXTERNO: no van al modelo) y sin fotos, mapas, índices ni saltos. una mención sale por nombre solo si
// `nameable` dice que la seudonimización de la corrida lo reemplaza entero (y vuelve como [PERSONA:<id>]); si no, con la cita
// de EXTERNO (cargo y organización, código GI- o «una persona de <localidad>»): un nombre nunca llega en claro al proveedor
object SectionText {
    fun render(
        blocks: List<Block>,
        people: Map<String, MentionTarget>,
        nameable: (String) -> Boolean
    ): String {
        fun r(text: String): String =
            Mentions.TOKEN.replace(text) { match ->
                val target = people["${match.groupValues[1]}:${match.groupValues[2].lowercase()}"]
                when {
                    target == null -> Mentions.UNKNOWN
                    nameable(target.name) -> target.name
                    else -> Mentions.cite(target, Disclosure.EXTERNO)
                }
            }
        val parts =
            blocks.mapNotNull { block ->
                when (block) {
                    is Block.Heading -> "#".repeat(block.level.coerceIn(2, 3)) + " " + r(block.text)
                    is Block.Paragraph ->
                        if (block.mark !=
                            null
                        ) {
                            null
                        } else {
                            (listOf(r(block.markdown)) + block.footnotes.map { "Nota: ${r(it)}" }).joinToString("\n")
                        }
                    is Block.Table ->
                        if (block.mark != null) {
                            null
                        } else {
                            "**${r(block.title)}**\n| ${block.header.joinToString(" | ") { r(it) }} |\n" +
                                block.rows.joinToString("\n") { row ->
                                    "| " + row.joinToString(" | ") { cell -> if (cell.mark != null) "" else r(cell.text) } +
                                        " |"
                                }
                        }
                    is Block.LineChart ->
                        "${r(block.title)}: " +
                            block.series.joinToString("; ") { series ->
                                "${r(series.name)} " +
                                    block.categories.zip(series.values).joinToString(", ") { (category, value) -> "$category=${value ?: "sin dato"}" }
                            }
                    else -> null
                }
            }
        return parts.joinToString("\n\n")
    }
}
