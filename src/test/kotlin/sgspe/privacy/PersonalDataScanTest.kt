package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// especificación de F8 §2.2.9: un aviso por sección, sin lo encontrado; las menciones no cuentan
class PersonalDataScanTest {
    private val names = listOf("Persona Ejemplo 1", "Ejemplina")

    private fun scan(vararg texts: String) = PersonalDataScan.scan(mapOf("relaciones" to texts.toList()), names)

    @Test
    fun `avisa nombres del proyecto, numeros de 8 digitos, telefonos y correos`() {
        val expected = listOf(ReportWarning(PersonalDataScan.CODE, "relaciones"))
        assertEquals(expected, scan("Conversamos con persona ejemplo 1 en la faena."))
        assertEquals(expected, scan("Su documento es 12345678."))
        assertEquals(expected, scan("Llamar al 987 654 321."))
        assertEquals(expected, scan("Escribir a contacto@sgspe.test."))
        assertEquals(expected, scan("uno", "dos 987654321"))
    }

    @Test
    fun `una mencion, un nombre de pila suelto o un codigo GI- no avisan`() {
        assertEquals(emptyList<ReportWarning>(), scan("Conversamos con [[person:11111111-1111-1111-1111-111111111111]]."))
        assertEquals(emptyList<ReportWarning>(), scan("Conversamos con \\[\\[person:11111111-1111-1111-1111-111111111111\\]\\]."))
        assertEquals(emptyList<ReportWarning>(), scan("Ejemplina es un nombre suelto; GI-12345678ABCD es un código."))
    }
}
