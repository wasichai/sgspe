package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// model/layers.json (plan 6.1) en el jar: las capas públicas que el proxy puede servir (especificación de F6 §2.2.1)
class WmsLayersCatalogTest {
    @Test
    fun `lee un arreglo o un objeto con layers`() {
        val entry = """{"layer":"LYR-01","object":"administrative_unit","geometry":"boundary","style":"sgspe-lyr-01"}"""
        val expected = listOf(PublicLayer("LYR-01", "administrative_unit", "boundary", "sgspe-lyr-01"))
        assertEquals(expected, WmsLayersCatalog.parse("[$entry]"))
        assertEquals(expected, WmsLayersCatalog.parse("""{"layers":[$entry]}"""))
    }

    // solo maestros del tenant sin campos restringidos (§2.2.1); test_model.py lo prueba contra roles.json, aquí basta con
    // que el jar traiga las siete capas públicas y ningún objeto de proyecto
    @Test
    fun `el jar trae las capas publicas y ninguna de proyecto`() {
        val all = WmsLayersCatalog.ALL
        assertEquals(setOf("LYR-01", "LYR-02", "LYR-03", "LYR-04", "LYR-05", "LYR-07", "LYR-16"), all.map { it.layer }.toSet())
        val tenant = setOf("administrative_unit", "locality", "road_access", "community", "mining_concession", "school", "health_facility")
        assertTrue(all.all { it.objectName in tenant }, all.toString())
        assertTrue(all.all { it.style.isNotBlank() && it.geometry.isNotBlank() }, all.toString())
    }
}
