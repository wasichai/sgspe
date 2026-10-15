package sgspe.deliverable

// la numeración literal de las leyendas (especificación de F8 §2.2.12, ADR-0013): «Tabla 5.1, 5.2…» por capítulo en T1 y
// «Tabla 1, 2…» corridas en T2, que no tiene capítulos; las figuras aparte de las tablas. cada leyenda lleva su marcador
// (`_Tabla_5_1`), que las listas de tablas y de figuras de T1 citan con PAGEREF (8.2c). LibreOffice no numera por capítulo
// con SEQ y STYLEREF: el número lo escribe SGSPE
class CaptionNumbering {
    private val counters = mutableMapOf<Pair<String, Int?>, Int>()

    data class Caption(
        val label: String,
        val bookmark: String
    )

    fun next(
        kind: String,
        chapter: Int? = null
    ): Caption {
        val n = counters.merge(kind to chapter, 1, Int::plus)!!
        val number = if (chapter == null) "$n" else "$chapter.$n"
        return Caption("$kind $number", "_${kind}_${number.replace('.', '_')}")
    }

    companion object {
        const val TABLE = "Tabla"
        const val FIGURE = "Figura"
    }
}
