package sgspe.deliverable

// los topes de texto del esquema de las láminas (slides/src/spec/schema.ts) del lado de SGSPE: el planificador no entrega
// una especificación que sgspe-slides rechazaría (SPEC_INVALID). una lámina que no los cumple se descarta, con un aviso en el
// log, y la presentación sigue; nunca se recorta un texto, que cambiaría lo que dice. una tabla deja fuera las columnas de
// texto largo, que siguen en el informe
object DeckLimits {
    const val TITLE = 160
    const val CHAPTER = 60
    const val SOURCE = 300
    const val NOTE = 400
    const val CELL = 120
    const val HEADER = 60

    // las columnas cuyo encabezado y celdas caben en los topes de la tabla, en su orden
    fun shortColumns(
        header: List<String>,
        rows: List<List<String>>
    ): Pair<List<String>, List<List<String>>> {
        val keep = header.indices.filter { i -> header[i].length <= HEADER && rows.all { (it.getOrNull(i)?.length ?: 0) <= CELL } }
        return keep.map { header[it] } to rows.map { row -> keep.map { row.getOrElse(it) { "" } } }
    }

    // la lámina cumple los topes del esquema: los textos comunes y los de sus datos
    fun fits(slide: DeckSlide): Boolean {
        if (slide.title.length > TITLE || (slide.chapter?.length ?: 0) > CHAPTER) return false
        if ((slide.source?.length ?: 0) > SOURCE || (slide.note?.length ?: 0) > NOTE) return false
        return when (val d = slide.data) {
            is SlideData.Table ->
                d.header.size in 2..6 && d.header.all { it.length <= HEADER } && d.rows.size in 1..8 &&
                    d.rows.all { r -> r.all { it.length <= CELL } }
            is SlideData.Summary -> d.points.size in 1..5 && d.points.all { it.length <= 300 }
            is SlideData.Figures ->
                d.items.size in 1..4 &&
                    d.items.all { it.figure.display.length <= 40 && it.label.length <= 400 && (it.detail?.length ?: 0) <= 400 }
            is SlideData.Thesis -> d.figures.size <= 3 && (d.support?.length ?: 0) <= 600 && d.figures.all { it.figure.display.length <= 40 }
            is SlideData.Comparison -> d.items.size in 2..10 && d.items.all { it.label.length <= 80 && it.figure.display.length <= 40 }
            is SlideData.Trend ->
                d.categories.size in 2..24 && d.categories.all { it.length <= 40 } && d.series.size in 1..3 &&
                    d.series.all { s -> s.name.length <= 400 && s.labels.all { (it?.length ?: 0) <= 40 } }
            is SlideData.Quote -> d.quote.length <= 500 && d.attribution.length <= 400 && d.scope.length <= 400
            is SlideData.Timeline -> d.events.size in 2..8 && d.events.all { it.date.length <= 40 && it.label.length <= 120 && (it.detail?.length ?: 0) <= 400 }
            is SlideData.Matrix -> d.rows.size in 1..6 && d.columns.size in 1..6 && d.rows.all { it.length <= 40 } && d.columns.all { it.length <= 40 }
            is SlideData.Method -> d.steps.size in 1..4 && d.steps.all { it.label.length <= 80 && it.detail.length <= 240 } && d.facts.size <= 4
            is SlideData.Conclusions ->
                d.conclusions.size in 1..4 && d.conclusions.all { it.length <= 260 } && d.nextSteps.size <= 4 &&
                    d.nextSteps.all { it.length <= 200 }
            is SlideData.Sources ->
                d.sources.size in 1..6 && d.sources.all { it.length <= 240 } && d.limitations.size <= 5 &&
                    d.limitations.all { it.length <= 260 }
            is SlideData.Glossary -> d.terms.size in 1..5 && d.terms.all { it.term.length <= 60 && it.meaning.length <= 220 }
            is SlideData.Photos -> d.images.size in 1..2 && d.caption.length <= 400
            else -> true
        }
    }
}
