package sgspe.privacy

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// el modelo del informe (especificación de F8 §3.2, deliverables.md §7.3): lo que JOB-05 ensambla como la plataforma y
// congela con su SHA-256 antes de generar. es nominal (anterior a la divulgación): ReportDisclosure.apply lo lleva a un nivel
// sin saber de tipos, mirando solo las marcas de cada elemento. es autocontenido: los mapas van en base64 con su SHA-256 y
// las fotos por evidencia, archivo y SHA-256, así que un solo archivo (draft_model) basta para volver a generar. nunca lleva
// documento de identidad ni teléfono (ReportSourcesTest). los decimales y las fechas van como texto ya formateado (es-PE)
data class ReportModel(
    val meta: ReportMeta,
    val sections: List<ReportSection>,
    // las menciones [[person:<id>]], [[position:<id>]] y [[stakeholder:<id>]] de la narrativa y de los textos de registro, por
    // su token sin corchetes ("person:<id>"): lo que cada nivel necesita para resolverlas (Mentions)
    val people: Map<String, MentionTarget> = emptyMap(),
    // los mapas del informe por clave (portada, predio...), con su imagen de cada nivel; sin imagen, el mapa falló
    val maps: Map<String, ReportMapImages> = emptyMap(),
    // las fotos por id de evidencia
    val photos: Map<String, PhotoRef> = emptyMap(),
    val aiAssistedSections: List<String> = emptyList(),
    val warnings: List<ReportWarning> = emptyList()
)

// los metadatos del informe. `level` es null en el modelo congelado y lo fija ReportDisclosure; `draftRevision` es la letra
// del borrador y `issueRevision` la revisión con que se emitiría (ASM-027)
data class ReportMeta(
    val type: String,
    val typeVersion: Int,
    val code: String,
    val title: String,
    val draftRevision: String,
    val issueRevision: String,
    val project: String,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val cutoff: String? = null,
    val scope: String? = null,
    val preparedFor: String? = null,
    val preparedBy: String? = null,
    val authorAddress: String? = null,
    val dateLabel: String,
    val templateId: String? = null,
    val templateSha256: String,
    val coverMap: String? = null,
    val level: String? = null
)

data class ReportSection(
    val key: String,
    val title: String,
    val kind: String,
    val blocks: List<Block>,
    // el nivel de su título (1 a 3) y el capítulo de sus leyendas («Tabla 5.1»); T2 deja 1 y null (leyendas corridas)
    val level: Int = 1,
    val chapter: Int? = null
)

// la marca de un elemento sensible (especificación de F8 §3.2): Restricted lleva el campo restringido de donde sale, y
// HolderName y RegistryNumber quitan un nombre de titular o emisor y un número de registro o de comprobante. EXTERNO quita
// todo lo marcado; INTERNO no quita nada. la foto se marca por sí misma (PhotoRef) y la imagen del comprobante es su `kind`
data class Mark(
    val kind: String,
    val field: String? = null
) {
    companion object {
        const val RESTRICTED = "RESTRICTED"
        const val HOLDER_NAME = "HOLDER_NAME"
        const val REGISTRY_NUMBER = "REGISTRY_NUMBER"
        const val EXTERNAL_NOTE = "EXTERNAL_NOTE"

        fun restricted(field: String) = Mark(RESTRICTED, field)

        val HOLDER = Mark(HOLDER_NAME)
        val REGISTRY = Mark(REGISTRY_NUMBER)

        // un bloque que solo muestra EXTERNO, p. ej. el aviso de que se reservó contenido restringido
        val EXTERNAL_ONLY = Mark(EXTERNAL_NOTE)
    }
}

// un bloque de una sección. el discriminador `block` va en el JSON canónico
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "block")
@JsonSubTypes(
    JsonSubTypes.Type(Block.Heading::class, name = "heading"),
    JsonSubTypes.Type(Block.Paragraph::class, name = "paragraph"),
    JsonSubTypes.Type(Block.Table::class, name = "table"),
    JsonSubTypes.Type(Block.Photos::class, name = "photos"),
    JsonSubTypes.Type(Block.MapFigure::class, name = "map"),
    JsonSubTypes.Type(Block.SectionBreak::class, name = "section-break"),
    JsonSubTypes.Type(Block.Toc::class, name = "toc"),
    JsonSubTypes.Type(Block.CaptionList::class, name = "caption-list"),
    JsonSubTypes.Type(Block.LineChart::class, name = "line-chart")
)
sealed interface Block {
    // un título dentro de la sección: nivel 2 o 3 (el 1 es el de la sección)
    data class Heading(
        val level: Int,
        val text: String
    ) : Block

    // Markdown (CommonMark con tablas GFM, sin HTML) con menciones; `footnotes`, las notas al pie del párrafo
    data class Paragraph(
        val markdown: String,
        val mark: Mark? = null,
        val footnotes: List<String> = emptyList()
    ) : Block

    // una tabla con título arriba y fuente abajo (RQ-REP-006); `wide` la pone en una sección horizontal; `figure` la numera
    // como figura (la matriz 5 × 5 y el cuadrante 2 × 2 de T1 son gráficos nativos hechos de una tabla, deliverables.md §7.9)
    data class Table(
        val title: String,
        val header: List<String>,
        val rows: List<List<Cell>>,
        val source: String? = null,
        val wide: Boolean = false,
        val mark: Mark? = null,
        val figure: Boolean = false
    ) : Block

    // fotos de a dos por fila (RQ-REP-010): ids de evidencia de ReportModel.photos
    data class Photos(
        val title: String,
        val evidence: List<String>,
        val source: String? = null
    ) : Block

    // un mapa de ReportModel.maps por su clave
    data class MapFigure(
        val key: String,
        val title: String,
        val source: String? = null
    ) : Block

    // un salto de sección: lo que sigue va en horizontal o vuelve a vertical
    data class SectionBreak(
        val landscape: Boolean
    ) : Block

    // el índice (T1, ASM-135): el campo TOC de los títulos de nivel 1 a `levels`; LibreOffice lo llena al convertir
    // (updateIndexes) y Word al abrir
    data class Toc(
        val title: String,
        val levels: Int = 5
    ) : Block

    // la lista de tablas o de figuras (`kind` «Tabla» o «Figura», CaptionNumbering): una línea por leyenda del documento con
    // su página por PAGEREF a su marcador
    data class CaptionList(
        val title: String,
        val kind: String
    ) : Block

    // un gráfico de líneas nativo de Word (XDDF): categorías y una serie por nombre, los valores como texto decimal o null
    // (un hueco). la tendencia de 9.3.1.1 de T1 (deliverables.md §7.9)
    data class LineChart(
        val title: String,
        val categories: List<String>,
        val series: List<ChartSeries>,
        val source: String? = null
    ) : Block
}

// una serie de un gráfico de líneas
data class ChartSeries(
    val name: String,
    val values: List<String?>
)

// una celda: `colSpan` combina hacia la derecha y `rowSpan` hacia abajo (las celdas cubiertas no se repiten en `rows`);
// `shade`, su color de fondo RRGGBB (la matriz 5 × 5)
data class Cell(
    val text: String,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val mark: Mark? = null,
    val shade: String? = null
)

// lo que hace falta para citar a una persona en cada nivel: el nombre (INTERNO), el cargo y la organización o el código GI-
// (EXTERNO) y, sin cargo, la localidad («una persona de <localidad>»)
data class MentionTarget(
    val name: String,
    val position: String? = null,
    val organization: String? = null,
    val stakeholderCode: String? = null,
    val locality: String? = null
)

// la imagen PNG de un mapa en base64 con su SHA-256 y si se omitieron capas por el nivel
data class ReportImage(
    val pngBase64: String,
    val sha256: String,
    val omittedLayers: Boolean = false
)

// las imágenes de un mapa por nivel: INTERNO y EXTERNO (con la divulgación por capa de F6). sin imágenes, el mapa falló
data class ReportMapImages(
    val internal: ReportImage? = null,
    val external: ReportImage? = null
) {
    // calculado: no va al JSON canónico
    @get:JsonIgnore
    val failed: Boolean get() = internal == null || external == null
}

// una foto o la imagen de un comprobante (`kind` COMPROBANTE): la evidencia, su archivo y su SHA-256, el texto alternativo y
// sus datos de consentimiento (un vacío cuenta como identificable sin consentimiento, ASM-037)
data class PhotoRef(
    val evidence: String,
    val file: String,
    val sha256: String,
    val alt: String,
    val kind: String,
    val identifiable: Boolean? = null,
    val consent: Boolean? = null
) {
    companion object {
        const val PHOTO = "FOTO"
        const val RECEIPT = "COMPROBANTE"
    }
}

// un aviso del ensamblado: un código (MAP_FAILED, POSSIBLE_PERSONAL_DATA...) y, si es de una sección, su clave. nunca datos
// personales (RQ-SEC-010)
data class ReportWarning(
    val code: String,
    val section: String? = null
)
