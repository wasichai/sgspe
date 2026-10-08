package sgspe.privacy

// las celdas pequeñas de PUBLICO (ASM-088, ruling P28): un conteo menor que MIN no se publica, porque una celda de uno es la
// postura de una persona identificable, p. ej. la única autoridad de una localidad (security.md §11.2). se escribe «<5». no
// basta ocultar solo esa celda: si el total de la tabla se conoce (otra hoja del mismo archivo lo suma, o quien exporta lo
// restó de los visibles), el valor oculto se obtiene restando. por eso, antes de publicar nada, la supresión se completa:
// si queda una sola celda oculta o la suma de las ocultas es menor que MIN, se oculta también la menor de las visibles
// (supresión complementaria) hasta que el valor de cada celda oculta quede en un rango y no se pueda despejar. ninguna hoja
// de PUBLICO lleva totales. la celda complementaria se escribe «suprimido», porque su valor sí es mayor o igual que MIN
object SmallCells {
    const val MIN = 5
    const val BELOW = "<$MIN"
    const val COMPLEMENT = "suprimido"

    // `cells` en el orden en que se publican; devuelve cada clave con su conteo o con el texto que lo reemplaza
    fun <K> apply(cells: List<Pair<K, Int>>): List<Pair<K, Any>> {
        val hidden = cells.indices.filter { cells[it].second < MIN }.toMutableSet()
        val complement = mutableSetOf<Int>()
        if (hidden.isNotEmpty()) {
            val candidates =
                cells.indices
                    .filter { it !in hidden }
                    .sortedWith(compareBy({ cells[it].second }, { it }))
                    .iterator()
            while ((hidden.size + complement.size < 2 || (hidden + complement).sumOf { cells[it].second } < MIN) && candidates.hasNext()) {
                complement += candidates.next()
            }
        }
        return cells.mapIndexed { index, (key, count) ->
            key to
                when (index) {
                    in hidden -> BELOW
                    in complement -> COMPLEMENT
                    else -> count
                }
        }
    }
}
