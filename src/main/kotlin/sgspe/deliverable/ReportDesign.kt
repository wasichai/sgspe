package sgspe.deliverable

// el diseño tipográfico común de los entregables (ASM-141, deliverables.md §7.5 a §7.7): lo usan la plantilla base, DocxWriter
// y la clase LaTeX. A4 con márgenes de 2,5 cm (3 cm abajo), cuerpo de 10,5 pt, tablas de 9 pt sin filetes verticales, fuente
// de 8,5 pt y el color de acento en los títulos. las medidas de Word van en twips (1/20 pt) y medios puntos
object ReportDesign {
    const val ACCENT = "1F4E79"
    const val RULE = "404040"
    const val HAIRLINE = "D9D9D9"
    const val HEADER_SHADE = "F2F4F7"
    const val MUTED = "595959"

    // fuentes del DOCX de la plantilla base (las de ASM-135): serif para el cuerpo, sans para títulos y tablas
    const val DOCX_SERIF = "Caladea"
    const val DOCX_SANS = "Carlito"

    const val BODY_PT = 10.5
    const val TABLE_PT = 9.0
    const val SOURCE_PT = 8.5
    const val CAPTION_PT = 9.5

    // interlineado del DOCX (múltiplo de 240) y espacio después del párrafo
    const val LINE_240THS = 276
    const val PARAGRAPH_AFTER_PT = 6.0

    // títulos: tamaño, espacio antes y espacio después
    val HEADINGS: List<Triple<Double, Double, Double>> =
        listOf(
            Triple(16.0, 24.0, 10.0),
            Triple(13.0, 18.0, 6.0),
            Triple(11.5, 14.0, 4.0),
            Triple(10.5, 12.0, 3.0)
        )

    const val MARGIN_CM = 2.5
    const val BOTTOM_MARGIN_CM = 3.0
    const val HEADER_CM = 1.25
    const val FOOTER_CM = 1.25

    // relleno de celda: a los lados y arriba y abajo
    const val CELL_SIDE_CM = 0.15
    const val CELL_EDGE_CM = 0.07

    // filetes de las tablas en octavos de punto: arriba y abajo, bajo el encabezado y entre filas
    const val RULE_HEAVY = 8
    const val RULE_LIGHT = 4
    const val RULE_HAIR = 2

    fun twips(cm: Double): Long = Math.round(cm * 1440 / 2.54)

    fun twipsOfPoints(pt: Double): Long = Math.round(pt * 20)

    fun halfPoints(pt: Double): Long = Math.round(pt * 2)
}
