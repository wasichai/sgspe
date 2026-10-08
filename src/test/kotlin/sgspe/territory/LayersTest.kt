package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.RuleException

// las decisiones de Layers que no necesitan base: el bbox tolerante (P26) y el fallo cerrado de un filtro sin columna
class LayersTest {
    @Test
    fun `el bbox de un mapa alejado se recorta al globo y conserva la franja de latitud`() {
        assertEquals("-180.0,-20.0,180.0,10.0", Layers.normalizeBbox("-425,-20,275,10"))
        assertEquals("-180.0,-90.0,-170.0,90.0", Layers.normalizeBbox("-200,-95,-170,120"))
    }

    @Test
    fun `un bbox que cubre todo el globo no filtra`() {
        assertNull(Layers.normalizeBbox("-425,-100,275,100"))
    }

    @Test
    fun `los espacios se quitan y un bbox normal pasa igual`() {
        assertEquals("-78.3,-8.0,-78.1,-7.8", Layers.normalizeBbox(" -78.3, -8 ,-78.1 , -7.8 "))
    }

    @Test
    fun `un bbox con el minimo mayor que el maximo o que no son cuatro numeros es INVALID_BBOX`() {
        for (raw in listOf("5,0,1,1", "0,5,1,1", "1,2", "a,b,c,d", "1,2,3,NaN", "1,2,3,Infinity", "1,2,3,4,5", "")) {
            val e = assertThrows<RuleException>(raw) { Layers.normalizeBbox(raw) }
            assertEquals(400, e.status.value(), raw)
            assertEquals("INVALID_BBOX", e.violations.single().message, raw)
            assertEquals("bbox", e.violations.single().field, raw)
        }
    }

    @Test
    fun `un bbox fuera del globo por completo se recorta a un borde y no es un error`() {
        assertEquals("180.0,0.0,180.0,1.0", Layers.normalizeBbox("200,0,300,1"))
    }

    @Test
    fun `un filtro sin columna legible no deja pasar nada y con columna se arma`() {
        assertEquals("FALSE", Layers.condition(null) { "$it IS NOT NULL" })
        assertEquals("\"component_type\" = ANY(:1)", Layers.condition("\"component_type\"") { "$it = ANY(:1)" })
    }
}
