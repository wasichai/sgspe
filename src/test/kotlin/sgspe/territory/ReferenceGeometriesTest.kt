package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReferenceGeometriesTest {
    private val point = mapOf("type" to "Point", "coordinates" to listOf(-78.5, -7.9))

    @Test
    fun `sin formas no hay referencia`() {
        assertNull(ReferenceGeometries.collection(emptyList()))
    }

    @Test
    fun `las formas vacias o mal formadas se descartan y si no queda ninguna es null`() {
        val empty = mapOf("type" to "Polygon", "coordinates" to emptyList<Any>())
        val untyped = mapOf("coordinates" to listOf(1.0, 2.0))
        assertNull(ReferenceGeometries.collection(listOf(empty, untyped)))
        assertEquals(
            mapOf("type" to "GeometryCollection", "geometries" to listOf(point)),
            ReferenceGeometries.collection(listOf(empty, point, untyped))
        )
    }
}
