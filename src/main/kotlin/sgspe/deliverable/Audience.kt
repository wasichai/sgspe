package sgspe.deliverable

import sgspe.privacy.Disclosure

// los públicos de las presentaciones (ASM-145, deliverables.md §16.5): cómo se cuenta un entregable emitido, nunca qué se puede
// contar, que lo decide el nivel. cada público tiene su arco (el orden de las partes), su densidad y los nombres de sus partes;
// solo restringe los niveles que admite. agregar un público es agregarlo aquí y en el tono de slides/src/design/tokens.ts
enum class Audience(
    val levels: Set<Disclosure>,
    val arc: List<Beat>,
    // cuántas láminas de contenido entran por parte, y cuántas barras o filas por lámina
    val perBeat: Int,
    val maxItems: Int,
    // separadores entre las partes (los públicos que siguen un relato largo)
    val separators: Boolean,
    private val chapters: Map<Beat, String>
) {
    DIRECCION(
        Disclosure.entries.toSet(),
        listOf(Beat.THESIS, Beat.FINDINGS, Beat.COMMITMENTS, Beat.RISKS, Beat.NEXT, Beat.LIMITS),
        perBeat = 2,
        maxItems = 6,
        separators = false,
        chapters =
            mapOf(
                Beat.THESIS to "Hallazgo principal",
                Beat.FINDINGS to "Relacionamiento",
                Beat.COMMITMENTS to "Compromisos",
                Beat.RISKS to "Riesgos",
                Beat.NEXT to "Decisiones",
                Beat.LIMITS to "Fuentes y límites"
            )
    ),
    CAMPO(
        Disclosure.entries.toSet(),
        listOf(Beat.CONTEXT, Beat.SCOPE, Beat.FINDINGS, Beat.EVIDENCE, Beat.COMMITMENTS, Beat.RISKS, Beat.NEXT, Beat.METHOD, Beat.LIMITS),
        perBeat = 3,
        maxItems = 10,
        separators = true,
        chapters =
            mapOf(
                Beat.CONTEXT to "El territorio",
                Beat.SCOPE to "El trabajo de campo",
                Beat.FINDINGS to "Hallazgos por localidad",
                Beat.EVIDENCE to "Lo que dicen los grupos",
                Beat.COMMITMENTS to "Compromisos",
                Beat.RISKS to "Conflictos y riesgos",
                Beat.NEXT to "Pendientes",
                Beat.METHOD to "Método",
                Beat.LIMITS to "Límites"
            )
    ),
    INVESTIGACION(
        Disclosure.entries.toSet(),
        listOf(Beat.METHOD, Beat.SCOPE, Beat.CONTEXT, Beat.FINDINGS, Beat.EVIDENCE, Beat.RISKS, Beat.LIMITS),
        perBeat = 3,
        maxItems = 10,
        separators = true,
        chapters =
            mapOf(
                Beat.METHOD to "Método",
                Beat.SCOPE to "Cobertura",
                Beat.CONTEXT to "Contexto",
                Beat.FINDINGS to "Indicadores y tendencias",
                Beat.EVIDENCE to "Evidencia cualitativa",
                Beat.RISKS to "Riesgos",
                Beat.LIMITS to "Fuentes y limitaciones"
            )
    ),
    INSTITUCIONES(
        setOf(Disclosure.EXTERNO, Disclosure.PUBLICO),
        listOf(Beat.CONTEXT, Beat.SCOPE, Beat.FINDINGS, Beat.COMMITMENTS, Beat.RISKS, Beat.EVIDENCE, Beat.NEXT, Beat.LIMITS),
        perBeat = 2,
        maxItems = 8,
        separators = true,
        chapters =
            mapOf(
                Beat.CONTEXT to "Alcance territorial",
                Beat.SCOPE to "Cobertura",
                Beat.FINDINGS to "Resultados",
                Beat.COMMITMENTS to "Compromisos y avances",
                Beat.RISKS to "Riesgos",
                Beat.EVIDENCE to "Evidencia",
                Beat.NEXT to "Asuntos de coordinación",
                Beat.LIMITS to "Fuentes"
            )
    ),
    COMUNIDAD(
        setOf(Disclosure.PUBLICO),
        listOf(Beat.INTRO, Beat.SCOPE, Beat.FINDINGS, Beat.COMMITMENTS, Beat.NEXT, Beat.GLOSSARY, Beat.LIMITS),
        perBeat = 2,
        maxItems = 6,
        separators = false,
        chapters =
            mapOf(
                Beat.INTRO to "Qué es este informe",
                Beat.SCOPE to "Qué hicimos",
                Beat.FINDINGS to "Qué encontramos",
                Beat.COMMITMENTS to "Los compromisos",
                Beat.NEXT to "Qué sigue",
                Beat.GLOSSARY to "Palabras del informe",
                Beat.LIMITS to "Qué no dicen los datos"
            )
    );

    fun chapter(beat: Beat): String = chapters[beat] ?: beat.name.lowercase()

    // el nivel es igual o más restrictivo que el de la versión, y el público lo admite
    fun admits(level: Disclosure): Boolean = level in levels

    companion object {
        fun of(name: String?): Audience? = entries.firstOrNull { it.name == name }
    }
}

// las partes de un relato: el planificador llena cada una solo si hay material para ella
enum class Beat {
    INTRO,
    THESIS,
    CONTEXT,
    SCOPE,
    FINDINGS,
    EVIDENCE,
    COMMITMENTS,
    RISKS,
    METHOD,
    NEXT,
    GLOSSARY,
    LIMITS
}
