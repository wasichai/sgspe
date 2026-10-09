package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.File

// las reglas de arquitectura de SGSPE (architecture.md §5.3, security.md §5.10, RQ-SEC-005, RNF-MNT-005), por escaneo de
// fuentes como las pruebas de pureza de caja-backend@dc9e391 (src/test/kotlin/caja/recaudacion/PurezaDeLaRecaudacionTest.kt).
// cada regla tiene su vigía, y una prueba aparte demuestra que el vigía reconoce lo prohibido y deja pasar lo permitido
class ArchitectureTest {
    private val main = File("src/main/kotlin")
    private val test = File("src/test/kotlin")

    private fun sources(root: File): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    // el código de una fuente: se exceptúan solo las líneas que son todo comentario (//, /*, /** y las de continuación de un
    // bloque, que empiezan con *), que pueden nombrar un rol o una api para explicar por qué no se usa. el resto de cada
    // línea se escanea entera: cortar en el primer // dejaría sin ver lo que sigue a una url en un literal ("https://x").
    // las líneas que empiezan con * o /* se exceptúan enteras: el caso patológico es `/* x */ código` en una sola línea
    // un comentario al final de una línea de código que nombre algo prohibido es un hallazgo: se reescribe. lo mismo vale
    // para un nombre de rol en mayúsculas dentro de un KDoc que no empiece la línea con * (poco probable)
    private fun code(lines: List<String>): List<String> =
        lines.map { line ->
            val start = line.trimStart()
            if (start.startsWith("//") || start.startsWith("*") || start.startsWith("/*")) "" else line
        }

    private fun offenders(
        files: List<File>,
        rule: Regex
    ): List<String> =
        files.flatMap { file ->
            code(file.readLines()).mapIndexedNotNull { i, line -> if (rule.containsMatchIn(line)) "${file.path}:${i + 1}: ${line.trim()}" else null }
        }

    // lo que el vigía ve en un texto: verdadero si alguna línea de código lo contiene
    private fun flags(
        rule: Regex,
        text: String
    ): Boolean = code(text.lines()).any { rule.containsMatchIn(it) }

    // esta misma fuente, por su ruta exacta: un ArchitectureTest.kt de otro paquete sí se escanea
    private fun isSelf(file: File): Boolean = file.invariantSeparatorsPath.endsWith("src/test/kotlin/sgspe/ArchitectureTest.kt")

    private fun isCommon(file: File): Boolean = file.invariantSeparatorsPath.contains("/sgspe/common/")

    @Test
    fun `el escaneo ve las fuentes de F1 y de F2`() {
        val names = sources(main).map { it.name }
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
        // una fuente de cada paquete de F2: si el escaneo dejara de verlos, las reglas pasarían en vacío
        val f2 = listOf("ProjectScopeGuard.kt", "Assessments.kt", "AttendanceCounts.kt", "Layers.kt", "Dashboards.kt", "StakeholderRegister.kt")
        assertTrue(names.containsAll(f2), "$names")
        val testNames = sources(test).map { it.name }
        assertTrue("ArchitectureTest.kt" in testNames, "$testNames")
    }

    // un paquete nuevo de F2 debe clasificarse aquí, de dominio o no: si no, la regla de dominio no lo escanearía
    @Test
    fun `cada paquete de sgspe esta clasificado como de dominio o no`() {
        val packages = File(main, "sgspe").listFiles { f -> f.isDirectory }!!.map { it.name }.toSet()
        val unclassified = packages - DOMAIN_PACKAGES.toSet() - NON_DOMAIN_PACKAGES.toSet()
        assertEquals(emptySet<String>(), unclassified, "clasifique el paquete nuevo en DOMAIN_PACKAGES o NON_DOMAIN_PACKAGES")
    }

    @Test
    fun `no hay codigo bajo el paquete wasichai`() {
        assertFalse(File(main, "wasichai").exists())
        assertFalse(File(test, "wasichai").exists())
        assertEquals(emptyList<String>(), offenders(sources(main) + sources(test), WASICHAI_PACKAGE))
    }

    @Test
    fun `no se importan las apis internas de wasichai, tampoco en las pruebas`() {
        // esta clase queda fuera del escaneo por nombre, porque sus vigías nombran las clases prohibidas en textos de prueba;
        // a ella solo se le vigilan los imports de wasichai, así que un import prohibido real sigue saltando
        val scanned = sources(main) + sources(test).filterNot { isSelf(it) }
        assertEquals(emptyList<String>(), offenders(scanned, INTERNAL_API))
        assertEquals(emptyList<String>(), offenders(sources(test).filter { isSelf(it) }, WASICHAI_IMPORT))
    }

    @Test
    fun `ningun nombre de rol aparece en el codigo principal`() {
        assertEquals(emptyList<String>(), offenders(sources(main), roleLiteral()))
    }

    @Test
    fun `los paquetes de dominio no dependen de tableros, entregables ni IA`() {
        val domain = DOMAIN_PACKAGES.flatMap { sources(File(main, "sgspe/$it")) }
        assertEquals(emptyList<String>(), offenders(domain, REPORTING_IMPORT))
    }

    // sgspe.scope y sgspe.privacy dependen solo de sgspe.common (architecture.md §5.3): el alcance no conoce el dominio y
    // las exportaciones leen con Records
    @Test
    fun `scope y privacy solo dependen de common`() {
        for (name in listOf("scope", "privacy")) {
            assertEquals(emptyList<String>(), offenders(sources(File(main, "sgspe/$name")), NOT_COMMON_IMPORT), name)
        }
    }

    // UPS-17: findAllOrganizations (obsoleto desde wasichai 0.5.0) no se usa en ningún archivo; los trabajos listan los
    // tenants con TenantDirectory, y solo sgspe/common/Organizations.kt lo toca (nunca un controlador ni un dominio)
    @Test
    fun `nadie usa findAllOrganizations`() {
        assertEquals(emptyList<String>(), offenders(sources(main), ORGANIZATIONS))
    }

    @Test
    fun `solo Organizations toca TenantDirectory`() {
        val outside = sources(main).filterNot { it.invariantSeparatorsPath.endsWith("/sgspe/common/Organizations.kt") }
        assertEquals(emptyList<String>(), offenders(outside, TENANT_DIRECTORY))
    }

    @Test
    fun `observability no depende de otros paquetes de sgspe`() {
        assertEquals(emptyList<String>(), offenders(sources(File(main, "sgspe/observability")), OTHER_SGSPE_IMPORT))
    }

    // solo sgspe.common escribe con la marca SgspeWrite o toca RecordService; el dominio pasa por Records. la regla es solo
    // para src/main: las pruebas (RecordsApiTest, SgspeWriteTest) usan RecordService y SgspeWrite legítimamente
    @Test
    fun `fuera de sgspe common nadie escribe con SgspeWrite ni usa RecordService`() {
        val outside = sources(main).filterNot { isCommon(it) }
        assertTrue(outside.isNotEmpty())
        assertEquals(emptyList<String>(), offenders(outside, DIRECT_WRITE))
    }

    @Test
    fun `los vigias reconocen lo prohibido y dejan pasar lo permitido`() {
        assertTrue(WASICHAI_PACKAGE.containsMatchIn("package wasichai.core.data"))
        assertFalse(WASICHAI_PACKAGE.containsMatchIn("import wasichai.core.data.RecordService"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.data.PhysicalTableRecordStore"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.data.RecordStore"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.audit.AuditService"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.metadata.ObjectSchemaManager"))
        assertFalse(INTERNAL_API.containsMatchIn("import wasichai.core.data.RecordService"))
        assertFalse(INTERNAL_API.containsMatchIn("import wasichai.core.data.RecordWriteGuard"))
        val roles = roleLiteral()
        assertTrue(roles.containsMatchIn("if (user.roles.contains(\"AUDITOR\")) return"))
        assertTrue(roles.containsMatchIn("val admin = \"ADMIN\""))
        assertTrue(roles.containsMatchIn("listOf(\"GIS_ANALYST\")"))
        assertFalse(roles.containsMatchIn("val isAdmin = user.isAdmin"))
        assertFalse(roles.containsMatchIn("requirePermission(user, \"EDIT_ANY\", objectId)"))
        assertTrue(REPORTING_IMPORT.containsMatchIn("import sgspe.dashboard.Indicators"))
        assertTrue(REPORTING_IMPORT.containsMatchIn("import sgspe.ai.SuggestionWriter"))
        assertFalse(REPORTING_IMPORT.containsMatchIn("import sgspe.common.Records"))
        assertTrue(OTHER_SGSPE_IMPORT.containsMatchIn("import sgspe.common.Records"))
        assertFalse(OTHER_SGSPE_IMPORT.containsMatchIn("import sgspe.observability.CorrelationId"))
        assertTrue(DIRECT_WRITE.containsMatchIn("    suspend fun f() = withContext(SgspeWrite) { records.create() }"))
        assertTrue(DIRECT_WRITE.containsMatchIn("withContext(Dispatchers.IO + SgspeWrite) { x() }"))
        assertTrue(DIRECT_WRITE.containsMatchIn("import wasichai.core.data.RecordService"))
        assertTrue(DIRECT_WRITE.containsMatchIn("class Risks(private val records: RecordService)"))
        assertFalse(DIRECT_WRITE.containsMatchIn("import sgspe.common.Records"))
        assertFalse(DIRECT_WRITE.containsMatchIn("class Risks(private val records: Records)"))
        assertFalse(DIRECT_WRITE.containsMatchIn("withContext(Dispatchers.IO) { x() }"))
        assertFalse(DIRECT_WRITE.containsMatchIn("import wasichai.core.data.RecordRequest"))
        // F1 ronda 1: formas que los vigías originales dejaban pasar
        val aliased = "import sgspe.common.SgspeWrite as W\nsuspend fun f() = withContext(W) { x() }"
        assertTrue(flags(DIRECT_WRITE, aliased))
        assertTrue(flags(DIRECT_WRITE, "withContext(Dispatchers.IO.limitedParallelism(1) + SgspeWrite) { x() }"))
        assertTrue(flags(DIRECT_WRITE, "scope.launch(SgspeWrite) { x() }"))
        assertTrue(flags(DIRECT_WRITE, "val ctx = SgspeWrite + Dispatchers.IO"))
        assertTrue(flags(DIRECT_WRITE, "suspend fun f() =\n    withContext(\n        Dispatchers.IO +\n        SgspeWrite\n    ) { x() }"))
        assertTrue(flags(DIRECT_WRITE, "import wasichai.core.data.*\nclass A(r: RecordService)"))
        assertFalse(flags(DIRECT_WRITE, "val ctx = SgspeWriter()"))
        assertFalse(flags(DIRECT_WRITE, "// withContext(SgspeWrite) está prohibido fuera de common"))
        assertTrue(flags(INTERNAL_API, "import wasichai.core.data.*"))
        assertTrue(flags(INTERNAL_API, "import wasichai.*"))
        assertTrue(flags(INTERNAL_API, "val s: wasichai.core.data.RecordStore"))
        assertTrue(flags(INTERNAL_API, "import wasichai.RecordStore"))
        assertTrue(flags(INTERNAL_API, "import wasichai.core.audit.AuditService as Audit"))
        assertFalse(flags(INTERNAL_API, "import wasichai.core.data.RecordService"))
        assertFalse(flags(INTERNAL_API, "class MyRecordStoreFake"))
        assertFalse(flags(INTERNAL_API, "// no se usa RecordStore: va por Records"))
        assertTrue(flags(roles, "hasAuthority(\"ROLE_AUDITOR\")"))
        assertTrue(flags(roles, "hasAuthority(\"ROLE_SUPER_ADMIN\")"))
        assertTrue(flags(roles, "val r = \"AUDITOR_X\""))
        assertFalse(flags(roles, "val r = \"AUDITORIA\""))
        assertTrue(flags(roles, "listOf(\"https://x\", \"AUDITOR\")"))
        assertTrue(flags(roles, "val u = \"http://x\"; val r = \"AUDITOR\""))
        assertFalse(flags(roles, "// el rol AUDITOR no se nombra aquí"))
        assertFalse(flags(roles, "    * el rol AUDITOR, en un KDoc"))
        assertFalse(flags(roles, "/** el rol AUDITOR */"))
        assertFalse(flags(roles, "/* el rol AUDITOR"))
        assertTrue(isSelf(File("src/test/kotlin/sgspe/ArchitectureTest.kt")))
        assertFalse(isSelf(File("src/test/kotlin/sgspe/otro/ArchitectureTest.kt")))
        assertFalse(isSelf(File("src/main/kotlin/sgspe/ArchitectureTest.kt")))
        assertTrue(WASICHAI_IMPORT.containsMatchIn("import wasichai.core.data.RecordStore"))
        assertFalse(WASICHAI_IMPORT.containsMatchIn("assertTrue(flags(INTERNAL_API, \"import wasichai.*\"))"))
        assertTrue(NOT_COMMON_IMPORT.containsMatchIn("import sgspe.stakeholder.Assessments"))
        assertFalse(NOT_COMMON_IMPORT.containsMatchIn("import sgspe.common.Records"))
        assertTrue(ORGANIZATIONS.containsMatchIn("objects.findAllOrganizations()"))
        assertFalse(ORGANIZATIONS.containsMatchIn("organizations.having(\"stakeholder\")"))
        assertTrue(TENANT_DIRECTORY.containsMatchIn("private val tenants: TenantDirectory"))
        assertFalse(TENANT_DIRECTORY.containsMatchIn("organizations.having(\"stakeholder\")"))
        assertTrue(isCommon(File("src/main/kotlin/sgspe/common/Records.kt")))
        assertFalse(isCommon(File("src/main/kotlin/sgspe/risk/Risks.kt")))
        assertFalse(isCommon(File("src/main/kotlin/sgspe/commonplace/Risks.kt")))
    }

    companion object {
        private val WASICHAI_PACKAGE = Regex("""^\s*package\s+wasichai\b""")

        // el nombre de la clase en cualquier forma (import, alias, nombre completo, uso) y cualquier import con comodín de wasichai
        private val INTERNAL_API =
            Regex("""\b(RecordStore|PhysicalTableRecordStore|AuditService|ObjectSchemaManager)\b|^\s*import\s+wasichai\.\S*\*""")
        private val WASICHAI_IMPORT = Regex("""^\s*import\s+wasichai\.""")
        private val REPORTING_IMPORT = Regex("""^\s*import\s+sgspe\.(dashboard|deliverable|ai)\.""")
        private val OTHER_SGSPE_IMPORT = Regex("""^\s*import\s+sgspe\.(?!observability\.)""")
        private val NOT_COMMON_IMPORT = Regex("""^\s*import\s+sgspe\.(?!common\.)""")
        private val ORGANIZATIONS = Regex("""\bfindAllOrganizations\b""")
        private val TENANT_DIRECTORY = Regex("""\bTenantDirectory\b""")

        // SgspeWrite y RecordService son nombres propios del proyecto: cualquier mención fuera de common es un hallazgo
        private val DIRECT_WRITE = Regex("""\b(SgspeWrite|RecordService)\b""")
        private val NON_DOMAIN_PACKAGES = listOf("common", "observability", "prod", "scope", "privacy", "dashboard", "deliverable", "ai")
        private val DOMAIN_PACKAGES =
            listOf("stakeholder", "engagement", "commitment", "agreement", "attention", "risk", "warning", "investment", "evidence", "territory")

        // los 15 roles declarados, los 2 de mappedToAdmin y el ADMIN de wasichai, leídos de model/roles.json. el límite no es
        // \b: el guion bajo cuenta como letra en \b y dejaría pasar "ROLE_AUDITOR" (Spring Security). distingue mayúsculas a
        // propósito: en minúsculas las mismas palabras ("admin", "auditor") aparecen en rutas, identificadores y prosa, y
        // darían falsos positivos; los roles se escriben siempre en mayúsculas
        private fun roleLiteral(): Regex {
            val file = JsonMapper.builder().build().readTree(File("model/roles.json"))
            val names = file["roles"].values().map { it["name"].asString() } + file["mappedToAdmin"].values().map { it.asString() } + "ADMIN"
            return Regex("(?<![A-Za-z0-9])(${names.joinToString("|")})(?![A-Za-z0-9])")
        }
    }
}
