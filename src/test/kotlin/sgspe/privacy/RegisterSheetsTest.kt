package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        assertTrue("GI-ABCDEF120000" in cells)
        // el cargo sí: es como se cita a una persona en EXTERNO
        assertTrue("Presidente, Asociación Ejemplo" in cells)
    }

    @Test
    fun `el codigo es estable, de 12 digitos hexadecimales y distinto para otro grupo de interes`() {
        assertEquals("GI-ABCDEF120000", RegisterSheets.code("abcdef12-0000-4000-8000-000000000001"))
        assertEquals(RegisterSheets.code(stakeholderId), RegisterSheets.code(stakeholderId))
        assertTrue(Regex("GI-[0-9A-F]{12}").matches(RegisterSheets.code("0f3a9c21-7b6e-4d10-9a55-1c2d3e4f5a6b")))
        assertFalse(RegisterSheets.code("abcdef12-0000-4000-8000-000000000001") == RegisterSheets.code("abcdef12-0001-4000-8000-000000000001"))
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

    // n grupos de interés con ese grupo de influencia, postura y ámbito; el id cambia con cada uno
    private fun many(
        n: Int,
        group: String = "CLAVE",
        stance: String = "A FAVOR",
        scope: String = "AIDS"
    ) = (1..n).map { i ->
        line(
            "%08x-0000-0000-0000-%012x".format(group.hashCode() xor stance.hashCode(), i),
            mapOf(
                "influence_group" to group,
                "stance" to stance,
                "stakeholder_scope" to scope
            )
        )
    }

    @Test
    fun `PUBLICO trae solo conteos y ningun nombre`() {
        val lines =
            many(6) + line("00000000-0000-0000-0000-000000000001", mapOf("organization" to organization, "influence_group" to "CLAVE", "stance" to "A FAVOR"))
        val sheets = RegisterSheets.aggregates(lines, context())
        val cells = texts(sheets)
        assertTrue("Grupos de interés" in cells)
        assertFalse(cells.any { "Persona Ejemplo" in it || "Asociación Ejemplo" in it || it.startsWith("GI-") })
        assertEquals(listOf("Agregados", "Por ámbito"), sheets.map { it.name })
        val grouped = sheets.first().rows.toList()
        assertEquals(listOf("CLAVE", "A FAVOR", 7), grouped.single().let { listOf(it[0], it[1], it[2]) })
    }

    @Test
    fun `PUBLICO sin lectura de la postura no la agrupa`() {
        val sheets = RegisterSheets.aggregates(many(5), context(everything - "stakeholder.stance"))
        assertFalse("A FAVOR" in texts(sheets))
        assertTrue(NOT_AVAILABLE in texts(sheets))
    }

    @Test
    fun `PUBLICO suprime las celdas de menos de 5 en grupo por postura y en ambito, y su valor no sale`() {
        // una sola persona EN CONTRA en un grupo, y una sola en un ámbito con 7
        val lines = many(7) + many(1, stance = "EN CONTRA") + many(9, group = "LATENTE", stance = "NEUTRAL", scope = "REGIONAL")
        val sheets = RegisterSheets.aggregates(lines, context())
        val grouped = sheets[0].rows.map { it[2] }.toList()
        val scopes = sheets[1].rows.map { it[0] to it[1] }.toList()
        // CLAVE/A FAVOR 7, CLAVE/EN CONTRA 1 (menor que 5), LATENTE/NEUTRAL 9; la de 1 sale «<5» y la menor visible (7) también
        assertTrue(SmallCells.BELOW in grouped)
        assertFalse(1 in grouped)
        assertEquals(1, grouped.count { it == SmallCells.BELOW })
        assertEquals(1, grouped.count { it == SmallCells.COMPLEMENT })
        assertTrue(9 in grouped)
        // AIDS tiene 8 y REGIONAL 9: ninguna es pequeña
        assertEquals(listOf("AIDS" to 8, "REGIONAL" to 9), scopes)
    }

    @Test
    fun `PUBLICO no deja restar el total para despejar una celda oculta y no lleva totales`() {
        // 17 grupos de interés: 1 + 7 + 9. el ámbito suma 17 y la hoja de grupo por postura tiene una celda de 1:
        // con 17 - 7 - 9 se despejaría. la supresión complementaria oculta también el 7
        val lines = many(1, stance = "EN CONTRA") + many(7) + many(9, group = "LATENTE", stance = "NEUTRAL")
        val sheets = RegisterSheets.aggregates(lines, context())
        val shown =
            sheets[0]
                .rows
                .map { it[2] }
                .filterIsInstance<Int>()
                .toList()
        assertEquals(listOf(9), shown)
        // lo único visible en grupo por postura es 9: 17 - 9 = 8 = 1 + 7, que no aísla ninguna de las dos
        assertEquals(17, sheets[1].rows.sumOf { (it[1] as Int) })
        assertFalse(sheets.flatMap { s -> s.rows.map { it.firstOrNull().toString() } }.any { it.lowercase().startsWith("total") })
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
    }
}
