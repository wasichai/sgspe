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

    // los valores reales del catálogo de model.json
    private val grid =
        RegisterSheets.Grid(
            influenceGroups = listOf("ESTRATEGICO", "POLITICO", "PARA MONITOREO", "VULNERABLE"),
            stances = listOf("A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA"),
            scopes = listOf("AIDS", "AIIS", "REGIONAL", "NACIONAL")
        )

    // n grupos de interés con ese grupo de influencia, postura y ámbito; el id cambia con cada uno
    private fun many(
        n: Int,
        group: String = "ESTRATEGICO",
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

    private fun counts(sheet: XlsxSheet) = sheet.rows.map { it.last() }.toList()

    @Test
    fun `PUBLICO trae solo conteos y ningun nombre, sobre la grilla completa del catalogo con sus ceros`() {
        val lines =
            many(6) +
                line("00000000-0000-0000-0000-000000000001", mapOf("organization" to organization, "influence_group" to "ESTRATEGICO", "stance" to "A FAVOR"))
        val sheets = RegisterSheets.aggregates(lines, context(), grid)
        val cells = texts(sheets)
        assertTrue("Grupos de interés" in cells)
        assertFalse(cells.any { "Persona Ejemplo" in it || "Asociación Ejemplo" in it || it.startsWith("GI-") })
        assertEquals(listOf("Agregados", "Por ámbito"), sheets.map { it.name })
        // 4 grupos + SIN EVALUAR por 4 posturas + SIN EVALUAR: 25 filas, aunque solo una combinación tenga gente
        val rows = sheets[0].rows.toList()
        assertEquals(25, rows.size)
        assertEquals(listOf("ESTRATEGICO", "A FAVOR", 7), rows.first().let { listOf(it[0], it[1], it[2]) })
        assertEquals(24, rows.count { it[2] == SmallCells.BELOW })
        // 4 ámbitos + SIN AMBITO
        assertEquals(5, sheets[1].rows.count())
    }

    @Test
    fun `PUBLICO sin lectura de la postura no la agrupa`() {
        val sheets = RegisterSheets.aggregates(many(5), context(everything - "stakeholder.stance"), grid)
        assertFalse("A FAVOR" in texts(sheets))
        assertTrue(NOT_AVAILABLE in texts(sheets))
        // la postura es un solo valor: una fila por grupo de influencia (4 + SIN EVALUAR)
        assertEquals(5, sheets[0].rows.count())
    }

    @Test
    fun `PUBLICO suprime lo menor que 5 con una sola marca, y la menor visible tambien si lo oculto suma menos de 5`() {
        // 7 ESTRATEGICO a favor, 1 ESTRATEGICO en contra y 9 POLITICO neutral: lo oculto (la de 1 y los ceros) suma 1
        val lines = many(7) + many(1, stance = "EN CONTRA") + many(9, group = "POLITICO", stance = "NEUTRAL")
        val sheets = RegisterSheets.aggregates(lines, context(), grid)
        assertEquals(listOf<Any?>(9), counts(sheets[0]).filterIsInstance<Int>())
        assertEquals(24, counts(sheets[0]).count { it == SmallCells.BELOW })
        assertFalse(texts(sheets).any { "suprimido" in it.lowercase() })
        // los tres son AIDS: 17, que nadie despeja porque la hoja de grupo por postura no suma
        assertEquals(listOf(17), counts(sheets[1]).filterIsInstance<Int>())
    }

    @Test
    fun `PUBLICO con un solo grupo de interes no dice cual es, todas las celdas son menor que 5 y la hoja no cambia con la persona`() {
        val one = RegisterSheets.aggregates(many(1, group = "VULNERABLE", stance = "EN CONTRA PERO ABIERTO A ESCUCHAR", scope = "AIIS"), context(), grid)
        val other = RegisterSheets.aggregates(many(1, group = "POLITICO", stance = "A FAVOR", scope = "NACIONAL"), context(), grid)
        for (sheet in one) assertTrue(counts(sheet).all { it == SmallCells.BELOW }, sheet.name)
        // la presencia de una fila no dice nada: las dos exportaciones son idénticas
        assertEquals(one.map { it.rows.toList() }, other.map { it.rows.toList() })
        assertEquals(25, one[0].rows.count())
    }

    @Test
    fun `PUBLICO con 1 y 5 y el total de otra hoja visible no despeja cual es cual`() {
        // un EN CONTRA y cinco A FAVOR, los seis AIDS: la hoja de ámbito publica 6
        val lines = many(1, stance = "EN CONTRA") + many(5)
        val sheets = RegisterSheets.aggregates(lines, context(), grid)
        assertEquals(listOf(6), counts(sheets[1]).filterIsInstance<Int>())
        // el 5 y el 1 salen con la misma marca, y no hay ningún entero que lo distinga
        assertTrue(counts(sheets[0]).all { it == SmallCells.BELOW })
    }

    @Test
    fun `un nombre o un cargo que empieza con signo de formula sale como texto en el xlsx`() {
        val ctx =
            context(
                people = mapOf(person to "=HYPERLINK(\"http://x\")"),
                positions = mapOf(person to listOf("@cargo", "+1", "-1", "\tx"))
            )
        val out = ByteArrayOutputStream()
        Xlsx.write(RegisterSheets.register(listOf(line()), ctx, Disclosure.INTERNO), out)
        val cells = XSSFWorkbook(out.toByteArray().inputStream()).use { wb -> wb.flatMap { s -> s.flatMap { r -> r.map { it.toString() } } } }
        assertTrue(cells.none { it.startsWith("=") }, cells.toString())
        assertTrue("'=HYPERLINK(\"http://x\")" in cells)
        // el cargo une los vigentes con «; »: el primero empieza con «@» y se neutraliza
        assertTrue(cells.any { it.startsWith("'@cargo") })
        // cada prefijo de fórmula, uno por celda, pasa por safeCell en la capa Xlsx
        val prefixes = listOf("=a", "+a", "-a", "@a", "\ta")
        val direct = ByteArrayOutputStream()
        Xlsx.write(listOf(XlsxSheet("Prueba", prefixes, sequenceOf(prefixes))), direct)
        val written = XSSFWorkbook(direct.toByteArray().inputStream()).use { wb -> wb.getSheetAt(0).flatMap { r -> r.map { it.toString() } } }
        assertEquals(prefixes.map { "'$it" } + prefixes.map { "'$it" }, written)
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
