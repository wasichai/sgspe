package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException

// la lectura acotada de una exportación (dashboards.md §6.2, el mismo criterio de los tableros: conjuntos pequeños hasta
// GAP-027): por cursor, página a página, hasta `max` filas. pasarse no recorta la salida en silencio: es un 400
// TOO_MANY_RECORDS que pide acotar los filtros. `already` son las filas ya leídas del mismo conjunto
object BoundedRead {
    suspend fun <T> pages(
        objectName: String,
        max: Int,
        already: Int,
        fetch: suspend (cursor: String?) -> Pair<List<T>, String?>
    ): List<T> {
        val rows = mutableListOf<T>()
        var cursor: String? = null
        do {
            val (content, next) = fetch(cursor)
            rows += content
            if (already + rows.size > max) throw tooMany(objectName, max)
            cursor = next
        } while (cursor != null)
        return rows
    }

    fun tooMany(
        objectName: String,
        limit: Int
    ) = Finding(
        "TOO_MANY_RECORDS",
        "La exportación leería más de $limit registros de $objectName: acote los proyectos o los filtros",
        "filters.project",
        status = HttpStatus.BAD_REQUEST
    ).toCodedException()
}
