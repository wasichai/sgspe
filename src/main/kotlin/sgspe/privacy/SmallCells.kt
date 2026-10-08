package sgspe.privacy

// las celdas pequeñas de PUBLICO (ASM-088, rulings P28 y P28b): una tabla de PUBLICO no revela nada de una sola persona. un
// conteo menor que MIN, el cero incluido, no se publica: la celda sale con la marca única BELOW, y la marca no dice qué valor
// esconde. como restar el total (el de otra hoja del mismo archivo, o lo que suman las visibles) despejaría una celda
// oculta, antes de publicar nada la supresión se completa: si hay alguien oculto y la suma de lo oculto es menor que MIN, se oculta también la
// menor de las visibles, con la misma marca, hasta que no se pueda saber cuál de las celdas marcadas tiene cada valor.
// ninguna hoja de PUBLICO lleva totales. quien arma la tabla debe pasar la grilla completa del catálogo, con sus ceros: si no,
// la presencia de una fila ya diría que hay alguien en esa categoría
object SmallCells {
    const val MIN = 5
    const val BELOW = "<$MIN"

    // `cells` en el orden en que se publican; devuelve cada clave con su conteo o con la marca que lo reemplaza
    fun <K> apply(cells: List<Pair<K, Int>>): List<Pair<K, Any>> {
        val hidden = cells.indices.filter { cells[it].second < MIN }.toMutableSet()
        if (hidden.isNotEmpty()) {
            val candidates =
                cells.indices
                    .filter { it !in hidden }
                    .sortedWith(compareBy({ cells[it].second }, { it }))
                    .iterator()
            // lo oculto suma cero: no hay nadie que proteger y no se arrastra a ninguna visible
            while (hidden.sumOf { cells[it].second }.let { it in 1 until MIN } && candidates.hasNext()) hidden += candidates.next()
        }
        return cells.mapIndexed { index, (key, count) -> key to if (index in hidden) BELOW else count }
    }
}
