package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

// lo que el registro de grupos de interés escribe en cada nivel de divulgación, sin base (deliverables.md §9 y §10,
// security.md §11.4): quién ve un nombre, qué sale «no disponible», el código de EXTERNO y que ningún texto se tome por
// fórmula. todos los datos son ficticios
class RegisterSheetsTest {
    private val everything = RegisterSheets.REQUIRES.toSet()
    private val person = "11111111-1111-1111-1111-111111111111"
    private val organization = "22222222-2222-2222-2222-222222222222"
    private val stakeholderId = "abcdef12-0000-0000-0000-000000000000"

    private fun line(
        id: String = stakeholderId,
        attributes: Map<String, Any?> = mapOf("person" to person)
    ) = StakeholderLine(id, "identified", mapOf("stance" to "A FAVOR", "influence_group" to "CLAVE", "interest_index" to BigDecimal("3.50")) + attributes)

    private fun context(
        readable: Set<String> = everything,
        people: Map<String, String> = mapOf(person to "Persona Ejemplo Uno"),
        positions: Map<String, List<String>> = mapOf(person to listOf("Presidente, Asociación Ejemplo"))
    ) = RegisterContext(readable, mapOf("p" to "TST-001"), people, mapOf(organization to "Asociación Ejemplo"), emptyMap(), positions)

    private fun texts(sheets: List<XlsxSheet>): List<String> = sheets.flatMap { s -> s.header + s.rows.flatMap { it }.map { it.toString() } }

    @Test
    fun `INTERNO nombra a la persona y EXTERNO la cita por codigo`() {
        val internal = RegisterSheets.register(listOf(line()), context(), Disclosure.INTERNO)
        assertTrue("Persona Ejemplo Uno" in texts(internal))
        val external = RegisterSheets.register(listOf(line()), context(), Disclosure.EXTERNO)
        val cells = texts(external)
        assertFalse(cells.any { "Persona Ejemplo" in it })
        assertTrue("GI-ABCDEF12" in cells)
        // el cargo sí: es como se cita a una persona en EXTERNO
        assertTrue("Presidente, Asociación Ejemplo" in cells)
    }

    @Test
    fun `EXTERNO no trae al responsable y INTERNO si`() {
        val owner = mapOf("owner_user_email" to "responsable@sgspe.test")
        assertTrue(
            "responsable@sgspe.test" in
                texts(RegisterSheets.register(listOf(line(attributes = owner + mapOf("person" to person))), context(), Disclosure.INTERNO))
        )
        assertFalse(
            "responsable@sgspe.test" in
                texts(RegisterSheets.register(listOf(line(attributes = owner + mapOf("person" to person))), context(), Disclosure.EXTERNO))
        )
    }

    @Test
    fun `quien no lee los nombres ni la postura los ve no disponibles y el nombre nunca sale`() {
        val readable = everything - "person.given_names" - "person.surnames" - "stakeholder.stance"
        // aunque el contexto trajera el nombre (un error de quien lo arma), sin permiso de campo no sale
        val cells =
            texts(RegisterSheets.register(listOf(line(attributes = mapOf("person" to person, "stance" to "EN CONTRA"))), context(readable), Disclosure.INTERNO))
        assertFalse(cells.any { "Persona Ejemplo" in it })
        assertFalse("EN CONTRA" in cells)
        assertFalse("A FAVOR" in cells)
        assertTrue(NOT_AVAILABLE in cells)
        val header = RegisterSheets.register(listOf(line()), context(readable), Disclosure.INTERNO).single().header
        val row =
            RegisterSheets
                .register(listOf(line()), context(readable), Disclosure.INTERNO)
                .single()
                .rows
                .single()
        assertEquals(NOT_AVAILABLE, row[header.indexOf("Grupo de interés")])
        assertEquals(NOT_AVAILABLE, row[header.indexOf("Postura")])
        assertEquals("CLAVE", row[header.indexOf("Grupo de influencia")])
    }

    @Test
    fun `una organizacion se nombra si se puede leer y es no disponible si no`() {
        val org = line(attributes = mapOf("organization" to organization))
        assertTrue("Asociación Ejemplo" in texts(RegisterSheets.register(listOf(org), context(), Disclosure.INTERNO)))
        assertTrue("Asociación Ejemplo" in texts(RegisterSheets.register(listOf(org), context(), Disclosure.EXTERNO)))
        val hidden = RegisterContext(everything, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
        assertTrue(NOT_AVAILABLE in texts(RegisterSheets.register(listOf(org), hidden, Disclosure.INTERNO)))
    }

    @Test
    fun `el documento de identidad no es una columna de ningun nivel`() {
        for (level in listOf(Disclosure.INTERNO, Disclosure.EXTERNO)) {
            val header = RegisterSheets.register(listOf(line()), context(), level).single().header
            assertFalse(header.any { "identidad" in it.lowercase() || "documento" in it.lowercase() || "dni" in it.lowercase() })
        }
    }

    @Test
    fun `PUBLICO trae solo conteos y ningun nombre`() {
        val lines =
            listOf(
                line(),
                line("fedcba98-0000-0000-0000-000000000000"),
                line(
                    "00000000-0000-0000-0000-000000000001",
                    mapOf("organization" to organization)
                )
            )
        val sheets = RegisterSheets.aggregates(lines, context())
        val cells = texts(sheets)
        assertTrue("Grupos de interés" in cells)
        assertFalse(cells.any { "Persona Ejemplo" in it || "Asociación Ejemplo" in it || it.startsWith("GI-") })
        assertEquals(listOf("Agregados", "Por ámbito"), sheets.map { it.name })
        val grouped = sheets.first().rows.toList()
        assertEquals(listOf("CLAVE", "A FAVOR", 3), grouped.single().let { listOf(it[0], it[1], it[2]) })
    }

    @Test
    fun `PUBLICO sin lectura de la postura no la agrupa`() {
        val sheets = RegisterSheets.aggregates(listOf(line()), context(everything - "stakeholder.stance"))
        assertFalse("A FAVOR" in texts(sheets))
        assertTrue(NOT_AVAILABLE in texts(sheets))
    }

    @Test
    fun `un nombre o un cargo que empieza con signo de formula sale como texto en el xlsx`() {
        val evil = line(attributes = mapOf("person" to person))
        val ctx = context(people = mapOf(person to "=HYPERLINK(\"http://x\")"), positions = mapOf(person to listOf("@cargo", "+1", "-1", "\tx")))
        val out = ByteArrayOutputStream()
        Xlsx.write(RegisterSheets.register(listOf(evil), ctx, Disclosure.INTERNO), out)
        val cells = XSSFWorkbook(out.toByteArray().inputStream()).use { wb -> wb.flatMap { s -> s.flatMap { r -> r.map { it.toString() } } } }
        assertTrue(cells.none { it.startsWith("=") }, cells.toString())
        assertTrue("'=HYPERLINK(\"http://x\")" in cells)
        assertTrue(cells.any { it.startsWith("'@cargo") })
    }

    @Test
    fun `los numeros salen como numeros`() {
        val out = ByteArrayOutputStream()
        Xlsx.write(RegisterSheets.register(listOf(line()), context(), Disclosure.INTERNO), out)
        XSSFWorkbook(out.toByteArray().inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            val index = sheet.getRow(0).indexOfFirst { it.stringCellValue == "Índice de interés" }
            assertEquals(3.5, sheet.getRow(1).getCell(index).numericCellValue)
        }
        assertNull(null)
    }
}
