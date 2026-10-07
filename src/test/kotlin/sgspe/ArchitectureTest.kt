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

    // el código de una fuente: sus líneas sin los comentarios de línea, que pueden nombrar un rol o una api para explicar
    // por qué no se usa
    private fun code(file: File): List<String> = file.readLines().map { it.substringBefore("//") }

    private fun offenders(
        files: List<File>,
        rule: Regex
    ): List<String> =
        files.flatMap { file ->
            code(file).mapIndexedNotNull { i, line -> if (rule.containsMatchIn(line)) "${file.path}:${i + 1}: ${line.trim()}" else null }
        }

    private fun isCommon(file: File): Boolean = file.invariantSeparatorsPath.contains("/sgspe/common/")

    @Test
    fun `el escaneo ve las fuentes de F1`() {
        val names = sources(main).map { it.name }
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
    }

    @Test
    fun `no hay codigo bajo el paquete wasichai`() {
        assertFalse(File(main, "wasichai").exists())
        assertFalse(File(test, "wasichai").exists())
        assertEquals(emptyList<String>(), offenders(sources(main) + sources(test), WASICHAI_PACKAGE))
    }

    @Test
    fun `no se importan las apis internas de wasichai, tampoco en las pruebas`() {
        assertEquals(emptyList<String>(), offenders(sources(main) + sources(test), INTERNAL_API))
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

    @Test
    fun `observability no depende de otros paquetes de sgspe`() {
        assertEquals(emptyList<String>(), offenders(sources(File(main, "sgspe/observability")), OTHER_SGSPE_IMPORT))
    }

    // solo sgspe.common escribe con la marca SgspeWrite o toca RecordService; el dominio pasa por Records
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
        assertTrue(isCommon(File("src/main/kotlin/sgspe/common/Records.kt")))
        assertFalse(isCommon(File("src/main/kotlin/sgspe/risk/Risks.kt")))
        assertFalse(isCommon(File("src/main/kotlin/sgspe/commonplace/Risks.kt")))
    }

    companion object {
        private val WASICHAI_PACKAGE = Regex("""^\s*package\s+wasichai\b""")
        private val INTERNAL_API = Regex("""^\s*import\s+wasichai\.\S*\.(RecordStore|PhysicalTableRecordStore|AuditService|ObjectSchemaManager)\b""")
        private val REPORTING_IMPORT = Regex("""^\s*import\s+sgspe\.(dashboard|deliverable|ai)\.""")
        private val OTHER_SGSPE_IMPORT = Regex("""^\s*import\s+sgspe\.(?!observability\.)""")
        private val DIRECT_WRITE = Regex("""\bwithContext\s*\([^)]*\bSgspeWrite\b|\bRecordService\b""")
        private val DOMAIN_PACKAGES =
            listOf("stakeholder", "engagement", "commitment", "agreement", "attention", "risk", "warning", "investment", "evidence", "territory")

        // los 15 roles declarados, los 2 de mappedToAdmin y el ADMIN de wasichai, leídos de model/roles.json
        private fun roleLiteral(): Regex {
            val file = JsonMapper.builder().build().readTree(File("model/roles.json"))
            val names = file["roles"].values().map { it["name"].asString() } + file["mappedToAdmin"].values().map { it.asString() } + "ADMIN"
            return Regex("\\b(${names.joinToString("|")})\\b")
        }
    }
}
