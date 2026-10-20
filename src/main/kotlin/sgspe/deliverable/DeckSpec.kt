package sgspe.deliverable

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import sgspe.privacy.CanonicalJson

// la especificación de una presentación (ADR-0031, deliverables.md §16.3): el espejo en Kotlin del esquema de
// slides/src/spec/schema.ts, que la valida del otro lado. se escribe en JSON canónico (claves ordenadas, sin nulos, decimales
// como texto) y su SHA-256 es la identidad de la presentación junto con el del modelo y las versiones (RQ-REP-037). las cifras
// ya van formateadas (`display`) y, para dibujar, como texto decimal (`value`); lo ausente es null, nunca cero
data class DeckSpec(
    val meta: DeckMeta,
    val theme: DeckTheme,
    val assets: Map<String, DeckAsset>,
    val slides: List<DeckSlide>,
    val schema: Int = SCHEMA
) {
    fun bytes(): ByteArray = CanonicalJson.bytesOf(this)

    companion object {
        const val SCHEMA = 1
    }
}

data class DeckMeta(
    val code: String,
    val title: String,
    val project: String,
    val revision: String,
    val level: String,
    val audience: String,
    val dateLabel: String,
    val derivation: String,
    val period: String? = null,
    val organization: String? = null
)

data class DeckTheme(
    val accent: String,
    val onAccent: String,
    val logo: String? = null
)

data class DeckAsset(
    val mime: String,
    val width: Int,
    val height: Int,
    val alt: String,
    val data: String
)

data class DeckSlide(
    val id: String,
    val purpose: String,
    val title: String,
    val data: SlideData,
    val chapter: String? = null,
    val source: String? = null,
    val note: String? = null,
    val speakerNotes: String? = null
)

// una cifra: su texto, su valor para dibujar (null si no hay) y si PUBLICO la reservó
data class DeckFigure(
    val display: String,
    val value: String?,
    val reserved: Boolean = false
)

data class LabeledFigure(
    val figure: DeckFigure,
    val label: String,
    val detail: String? = null
)

data class ComparisonItem(
    val label: String,
    val figure: DeckFigure,
    val focus: Boolean = false
)

data class TrendSeries(
    val name: String,
    val values: List<String?>,
    val labels: List<String?>,
    val n: List<Int?>? = null
)

data class MatrixCell(
    val figure: DeckFigure,
    val tone: String
)

data class MatrixLegend(
    val tone: String,
    val label: String
)

data class TimelineEvent(
    val date: String,
    val label: String,
    val detail: String? = null
)

data class MethodStep(
    val label: String,
    val detail: String
)

data class ScenarioColumn(
    val title: String,
    val points: List<String>
)

data class GlossaryTerm(
    val term: String,
    val meaning: String
)

// los datos de una lámina, por layout: el discriminador `layout` va en el JSON, como en el esquema del paquete
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "layout")
@JsonSubTypes(
    JsonSubTypes.Type(SlideData.Cover::class, name = "cover"),
    JsonSubTypes.Type(SlideData.Section::class, name = "section"),
    JsonSubTypes.Type(SlideData.Thesis::class, name = "thesis"),
    JsonSubTypes.Type(SlideData.Summary::class, name = "summary"),
    JsonSubTypes.Type(SlideData.Figures::class, name = "figures"),
    JsonSubTypes.Type(SlideData.Trend::class, name = "trend"),
    JsonSubTypes.Type(SlideData.Comparison::class, name = "comparison"),
    JsonSubTypes.Type(SlideData.MapView::class, name = "map"),
    JsonSubTypes.Type(SlideData.Quote::class, name = "quote"),
    JsonSubTypes.Type(SlideData.Timeline::class, name = "timeline"),
    JsonSubTypes.Type(SlideData.Matrix::class, name = "matrix"),
    JsonSubTypes.Type(SlideData.Table::class, name = "table"),
    JsonSubTypes.Type(SlideData.Method::class, name = "method"),
    JsonSubTypes.Type(SlideData.Scenarios::class, name = "scenarios"),
    JsonSubTypes.Type(SlideData.Conclusions::class, name = "conclusions"),
    JsonSubTypes.Type(SlideData.Sources::class, name = "sources"),
    JsonSubTypes.Type(SlideData.Glossary::class, name = "glossary"),
    JsonSubTypes.Type(SlideData.Photos::class, name = "photos"),
    JsonSubTypes.Type(SlideData.Closing::class, name = "closing")
)
sealed interface SlideData {
    data class Cover(
        val subtitle: String? = null,
        val place: String? = null,
        val preparedFor: String? = null,
        val preparedBy: String? = null,
        val image: String? = null
    ) : SlideData

    data class Section(
        val number: String? = null,
        val lead: String? = null
    ) : SlideData

    data class Thesis(
        val support: String? = null,
        val figures: List<LabeledFigure> = emptyList()
    ) : SlideData

    data class Summary(
        val points: List<String>
    ) : SlideData

    data class Figures(
        val items: List<LabeledFigure>
    ) : SlideData

    data class Trend(
        val unit: String,
        val categories: List<String>,
        val series: List<TrendSeries>,
        val min: String,
        val max: String,
        val reading: String? = null
    ) : SlideData

    data class Comparison(
        val unit: String,
        val items: List<ComparisonItem>,
        val max: String,
        val reading: String? = null
    ) : SlideData

    data class MapView(
        val image: String,
        val reading: String? = null
    ) : SlideData

    data class Quote(
        val quote: String,
        val attribution: String,
        val kind: String,
        val scope: String
    ) : SlideData

    data class Timeline(
        val events: List<TimelineEvent>
    ) : SlideData

    data class Matrix(
        val rowAxis: String,
        val columnAxis: String,
        val rows: List<String>,
        val columns: List<String>,
        val cells: List<List<MatrixCell>>,
        val legend: List<MatrixLegend> = emptyList(),
        val reading: String? = null
    ) : SlideData

    data class Table(
        val header: List<String>,
        val align: List<String>,
        val rows: List<List<String>>
    ) : SlideData

    data class Method(
        val steps: List<MethodStep>,
        val facts: List<LabeledFigure> = emptyList()
    ) : SlideData

    data class Scenarios(
        val columns: List<ScenarioColumn>
    ) : SlideData

    data class Conclusions(
        val conclusions: List<String>,
        val nextSteps: List<String> = emptyList()
    ) : SlideData

    data class Sources(
        val sources: List<String>,
        val limitations: List<String> = emptyList()
    ) : SlideData

    data class Glossary(
        val terms: List<GlossaryTerm>
    ) : SlideData

    data class Photos(
        val images: List<String>,
        val caption: String
    ) : SlideData

    data class Closing(
        val message: String,
        val contact: String? = null
    ) : SlideData
}
