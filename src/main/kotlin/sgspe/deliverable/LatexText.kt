package sgspe.deliverable

// la única puerta del texto del modelo hacia TeX (ASM-139, ADR-0030): nada que venga de un registro, de la narrativa o de los
// metadatos entra a main.tex sin pasar por aquí. escapa los diez caracteres especiales de TeX (`^` siempre, así no hay notación
// `^^`), encierra en llaves `*`, `[` y `]` (no se leen como argumento opcional ni como la variante con asterisco de `\\`, de
// `\item` o de `\newline`) y quita los caracteres de control. un salto de línea es un espacio en `text` y un `\newline` en
// `lines`. puro
object LatexText {
    private val REPLACEMENTS: Map<Char, String> =
        mapOf(
            '\\' to "\\textbackslash{}",
            '{' to "\\{",
            '}' to "\\}",
            '$' to "\\$",
            '&' to "\\&",
            '#' to "\\#",
            '^' to "\\textasciicircum{}",
            '_' to "\\_",
            '%' to "\\%",
            '~' to "\\textasciitilde{}",
            '*' to "{*}",
            '[' to "{[}",
            ']' to "{]}"
        )

    fun text(raw: String): String = escape(raw.replace('\n', ' '))

    // cada línea escapada, unidas por \newline; las líneas vacías del principio y del final no dejan un \newline suelto
    fun lines(raw: String): String = raw.trim('\n').split('\n').joinToString("\\newline ") { escape(it) }

    private fun escape(raw: String): String {
        val out = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when {
                ch in REPLACEMENTS -> out.append(REPLACEMENTS.getValue(ch))
                ch == '\t' || ch == '\r' -> out.append(' ')
                Character.isISOControl(ch) -> Unit
                // los separadores de línea y de párrafo de Unicode, y el BOM
                ch == ' ' || ch == ' ' || ch == '﻿' -> out.append(' ')
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    // un color RRGGBB de la marca o de una celda; cualquier otra cosa no se escribe
    fun color(raw: String?): String? = raw?.uppercase()?.takeIf { COLOR.matches(it) }

    // un número decimal del modelo («12.5», «-3») para pgfplots; lo demás es un hueco
    fun number(raw: String?): String = raw?.trim()?.takeIf { NUMBER.matches(it) } ?: "nan"

    private val COLOR = Regex("^[0-9A-F]{6}$")
    private val NUMBER = Regex("^-?\\d{1,15}(\\.\\d{1,15})?$")
}
