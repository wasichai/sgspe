package sgspe.privacy

// las celdas pequeñas de PUBLICO (ASM-088, rulings P28, P28b y P28c): una tabla de PUBLICO no revela nada de una sola persona. un
// conteo menor que MIN, el cero incluido, no se publica: la celda sale con la marca única HIDDEN, que es neutra y no dice qué
// valor esconde. como restar un total (el de otra hoja del mismo archivo, o lo que suman las visibles) despejaría una celda
// oculta, y como una población que comparte una postura la delataría celda por celda, la supresión se completa por tabla:
// mientras lo oculto sume menos de MIN, el cero incluido, se oculta también la menor de las visibles con la misma marca. así
// siempre hay al menos MIN personas de categoría desconocida, y las celdas ocultas son indistinguibles entre sí. ninguna hoja
// de PUBLICO lleva totales. quien arma la tabla debe pasar la grilla completa del catálogo, con sus ceros: si no, la presencia
// de una fila ya diría que hay alguien en esa categoría
object SmallCells {
    const val MIN = 5
    const val HIDDEN = "reservado"

    // `cells` en el orden en que se publican; devuelve cada clave con su conteo o con la marca que lo reemplaza
    fun <K> apply(cells: List<Pair<K, Int>>): List<Pair<K, Any>> {
        val hidden = cells.indices.filter { cells[it].second < MIN }.toMutableSet()
        if (hidden.isNotEmpty()) {
            val candidates =
                cells.indices
                    .filter { it !in hidden }
                    .sortedWith(compareBy({ cells[it].second }, { it }))
                    .iterator()
            while (hidden.sumOf { cells[it].second } < MIN && candidates.hasNext()) hidden += candidates.next()
        }
        return cells.mapIndexed { index, (key, count) -> key to if (index in hidden) HIDDEN else count }
    }
}
