package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.RuleException
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
        val org = line("00000000-0000-0000-0000-000000000001", mapOf("organization" to organization, "influence_group" to "ESTRATEGICO", "stance" to "A FAVOR"))
        val lines = many(6) + org + many(9, group = "POLITICO", stance = "NEUTRAL")
        val sheets = RegisterSheets.aggregates(lines, context(), grid)
        val cells = texts(sheets)
        assertTrue("Grupos de interés" in cells)
        assertFalse(cells.any { "Persona Ejemplo" in it || "Asociación Ejemplo" in it || it.startsWith("GI-") })
        assertEquals(listOf("Agregados", "Por ámbito"), sheets.map { it.name })
        // 4 grupos, SIN EVALUAR y no disponible por 4 posturas, SIN EVALUAR y no disponible: 36 filas, tenga o no gente la combinación
        val rows = sheets[0].rows.toList()
        assertEquals(36, rows.size)
        // lo oculto (los ceros) suma menos de 5: sale también la menor visible (7), y queda solo el 9
        assertEquals(listOf<Any?>(9), counts(sheets[0]).filterIsInstance<Int>())
        assertEquals(35, rows.count { it[2] == SmallCells.HIDDEN })
        // 4 ámbitos, SIN AMBITO y no disponible
        assertEquals(6, sheets[1].rows.count())
    }

    @Test
    fun `PUBLICO sin lectura de la postura no la agrupa`() {
        val sheets = RegisterSheets.aggregates(many(5) + many(6, group = "POLITICO"), context(everything - "stakeholder.stance"), grid)
        assertFalse("A FAVOR" in texts(sheets))
        assertTrue(NOT_AVAILABLE in texts(sheets))
        // la postura es un solo valor: una fila por grupo de influencia (4, SIN EVALUAR y no disponible)
        assertEquals(6, sheets[0].rows.count())
    }

    @Test
    fun `PUBLICO oculta con una sola marca neutra lo menor que 5 y lo que haga falta para que restar no lo despeje`() {
        // 7 ESTRATEGICO a favor, 1 ESTRATEGICO en contra y 9 POLITICO neutral: lo oculto suma 1
        val lines = many(7) + many(1, stance = "EN CONTRA") + many(9, group = "POLITICO", stance = "NEUTRAL", scope = "REGIONAL")
        val sheets = RegisterSheets.aggregates(lines, context(), grid)
        assertEquals(listOf<Any?>(9), counts(sheets[0]).filterIsInstance<Int>())
        assertFalse(texts(sheets).any { "suprimido" in it.lowercase() || "<5" in it })
        // el ámbito también oculta su menor visible (AIDS, 8) porque lo oculto suma cero: solo queda REGIONAL, 9
        assertEquals(listOf(9), counts(sheets[1]).filterIsInstance<Int>())
    }

    @Test
    fun `PUBLICO con un solo grupo de interes no dice cual es, todas las celdas son reservado y la hoja no cambia con la persona`() {
        val one = RegisterSheets.aggregates(many(1, group = "VULNERABLE", stance = "EN CONTRA PERO ABIERTO A ESCUCHAR", scope = "AIIS"), context(), grid)
        val other = RegisterSheets.aggregates(many(1, group = "POLITICO", stance = "A FAVOR", scope = "NACIONAL"), context(), grid)
        for (sheet in one) assertTrue(counts(sheet).all { it == SmallCells.HIDDEN }, sheet.name)
        // la presencia de una fila no dice nada: las dos exportaciones son idénticas
        assertEquals(one.map { it.rows.toList() }, other.map { it.rows.toList() })
        assertEquals(36, one[0].rows.count())
    }

    @Test
    fun `PUBLICO con 1 y 5 y el total de otra hoja no despeja cual es cual`() {
        // un EN CONTRA y cinco A FAVOR, los seis AIDS
        val sheets = RegisterSheets.aggregates(many(1, stance = "EN CONTRA") + many(5), context(), grid)
        // las dos salen con la misma marca, y no hay ningún entero que lo distinga; el ámbito tampoco queda como total
        assertTrue(counts(sheets[0]).all { it == SmallCells.HIDDEN })
        assertTrue(counts(sheets[1]).all { it == SmallCells.HIDDEN })
    }

    @Test
    fun `PUBLICO con cinco o mas personas de una sola postura no la delata ni con el total a la vista`() {
        // seis autoridades de la misma localidad, todas ESTRATEGICO y EN CONTRA: la hoja no puede publicar «6» en esa celda ni el
        // total en el ámbito, o se sabría que las seis están en contra
        val sheets = RegisterSheets.aggregates(many(6, stance = "EN CONTRA"), context(), grid)
        for (sheet in sheets) assertTrue(counts(sheet).all { it == SmallCells.HIDDEN }, sheet.name)
        assertFalse(texts(sheets).any { it == "6" || it == "6.0" })
        // con otra población que sí tiene variedad, esa postura sigue sin leerse en la celda de las seis
        val mixed = RegisterSheets.aggregates(many(6, stance = "EN CONTRA") + many(8, group = "POLITICO", stance = "A FAVOR"), context(), grid)
        assertEquals(listOf<Any?>(8), counts(mixed[0]).filterIsInstance<Int>())
    }

    @Test
    fun `PUBLICO rechaza con 409 CATALOGUE_UNAVAILABLE un catalogo vacio y nombra el campo`() {
        for ((empty, field) in listOf(
            RegisterSheets.Grid(emptyList(), grid.stances, grid.scopes) to "influence_group",
            RegisterSheets.Grid(grid.influenceGroups, emptyList(), grid.scopes) to "stance",
            RegisterSheets.Grid(grid.influenceGroups, grid.stances, emptyList()) to "stakeholder_scope"
        )) {
            val e = assertThrows<RuleException> { RegisterSheets.aggregates(many(6), context(), empty) }
            assertEquals(409, e.status.value())
            assertEquals("CATALOGUE_UNAVAILABLE", e.violations.single().message)
            assertEquals(field, e.violations.single().field)
        }
        // si quien exporta no lee la postura, su catálogo no hace falta
        RegisterSheets.aggregates(many(6), context(everything - "stakeholder.stance"), RegisterSheets.Grid(grid.influenceGroups, emptyList(), grid.scopes))
    }

    @Test
    fun `un valor que no esta en el catalogo se cuenta como no disponible, una fila que siempre existe`() {
        val legacy = many(6, group = "HEREDADO") + many(6, group = "POLITICO", stance = "NEUTRAL") + many(7, group = "VULNERABLE", stance = "EN CONTRA")
        val sheets = RegisterSheets.aggregates(legacy, context(), grid)
        val rows = sheets[0].rows.toList()
        // la grilla no crece con el valor heredado: 36 filas, y su fila «no disponible» existe haya o no valores así
        assertEquals(36, rows.size)
        assertFalse(texts(sheets).any { it == "HEREDADO" })
        val withoutLegacy =
            RegisterSheets.aggregates(
                many(6, group = "POLITICO", stance = "NEUTRAL") + many(7, group = "VULNERABLE", stance = "EN CONTRA"),
                context(),
                grid
            )
        assertEquals(withoutLegacy[0].rows.map { it.take(2) }.toList(), rows.map { it.take(2) })
        // las 6 de HEREDADO cuentan en la fila de «no disponible» del grupo, que existe siempre
        assertTrue(rows.any { it[0] == NOT_AVAILABLE && it[1] == "A FAVOR" })
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
