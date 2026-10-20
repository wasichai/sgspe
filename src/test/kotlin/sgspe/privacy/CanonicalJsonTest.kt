package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// especificación de F8 §3.2: claves ordenadas, UTF-8, sin espacios, sin nulos y sin coma flotante; volver a leer da el mismo
// modelo y el mismo SHA-256 (un solo archivo basta para volver a generar, RQ-REP-026)
class CanonicalJsonTest {
    @Test
    fun `ordena las claves, no deja espacios ni nulos y escapa lo necesario`() {
        val text = CanonicalJson.write(mapOf("b" to 1, "a" to listOf("x\"y", null, true), "c" to null, "ñ" to "línea\nnueva"))
        assertEquals("""{"a":["x\"y",null,true],"b":1,"ñ":"línea\nnueva"}""", text)
    }

    @Test
    fun `un decimal no es canonico y se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { CanonicalJson.write(mapOf("monto" to BigDecimal("1.50"))) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalJson.write(mapOf("monto" to 1.5)) }
    }

    @Test
    fun `el modelo va y vuelve con los mismos bytes y el mismo SHA-256`() {
        val model = ReportFixtures.model()
        val bytes = CanonicalJson.bytes(model)
        val back = CanonicalJson.read(bytes)
        assertEquals(model, back)
        assertEquals(CanonicalJson.sha256(bytes), CanonicalJson.sha256(CanonicalJson.bytes(back)))
        assertEquals(64, CanonicalJson.sha256(bytes).length)
    }

    // un modelo congelado antes de los hechos tipados (ADR-0031) conserva sus bytes: los campos nuevos son opcionales y nulos,
    // y CanonicalJson omite los nulos (RQ-REP-026)
    @Test
    fun `un modelo sin hechos tipados conserva sus bytes`() {
        assertEquals(GOLDEN_V1, CanonicalJson.sha256(CanonicalJson.bytes(ReportFixtures.model())))
    }

    private companion object {
        const val GOLDEN_V1 = "8f2a6ce1c1663f4a015ba0728545a5c4c1d2c403d8fda4ea204b875d4c71e541"
    }
}
