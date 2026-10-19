package sgspe.deliverable

import sgspe.privacy.Block
import sgspe.privacy.ReportModel

// las leyendas del documento en su orden, numeradas por capítulo (o corridas sin capítulo), con su título: las mismas para el
// DOCX y el PDF (ADR-0030). van por la posición del bloque (sección, bloque) y no por el bloque: un mismo bloque repetido
// lleva una leyenda por aparición (#83). llevan leyenda una tabla (o una tabla figura), un bloque de fotos con alguna foto del
// nivel, un mapa y un gráfico
class ReportCaptions private constructor() {
    private val byPosition = HashMap<Pair<Int, Int>, CaptionNumbering.Caption>()
    val ordered = mutableListOf<Pair<CaptionNumbering.Caption, String>>()

    fun find(at: Pair<Int, Int>): CaptionNumbering.Caption? = byPosition[at]

    fun of(at: Pair<Int, Int>): CaptionNumbering.Caption = byPosition[at] ?: error("un bloque con leyenda quedó sin numerar")

    companion object {
        fun of(
            model: ReportModel,
            chapterOf: (String) -> Int? = { null }
        ): ReportCaptions {
            val out = ReportCaptions()
            val numbering = CaptionNumbering()
            for ((sectionIndex, section) in model.sections.withIndex()) {
                val chapter = chapterOf(section.key) ?: section.chapter
                for ((blockIndex, block) in section.blocks.withIndex()) {
                    val (kind, title) =
                        when (block) {
                            is Block.Table -> (if (block.figure) CaptionNumbering.FIGURE else CaptionNumbering.TABLE) to block.title
                            is Block.Photos -> if (block.evidence.any { it in model.photos }) CaptionNumbering.FIGURE to block.title else continue
                            is Block.MapFigure -> CaptionNumbering.FIGURE to block.title
                            is Block.LineChart -> CaptionNumbering.FIGURE to block.title
                            is Block.ScatterChart -> CaptionNumbering.FIGURE to block.title
                            else -> continue
                        }
                    val caption = numbering.next(kind, chapter)
                    out.byPosition[sectionIndex to blockIndex] = caption
                    out.ordered += caption to title
                }
            }
            return out
        }
    }
}
