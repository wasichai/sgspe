package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ExactlyOneGuard
import sgspe.scope.ProjectScope
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
    fun `el escaneo ve las fuentes de F1, F2, F3 y F4`() {
        val names = sources(main).map { it.name }
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
        // una fuente de cada paquete de F2: si el escaneo dejara de verlos, las reglas pasarían en vacío
        val f2 = listOf("ProjectScopeGuard.kt", "Assessments.kt", "AttendanceCounts.kt", "Layers.kt", "Dashboards.kt", "StakeholderRegister.kt")
        assertTrue(names.containsAll(f2), "$names")
        // y una de cada paquete de F3
        val f3 = listOf("Idempotency.kt", "Evidences.kt", "CommitmentWriteGuard.kt", "AgreementWriteGuard.kt", "DeadlineJob.kt", "Notices.kt")
        assertTrue(names.containsAll(f3), "$names")
        // y las de F4 que cruzan paquetes: attention, el alcance de las confidenciales, el calendario, los avisos, las exportaciones
        // attention-register y risk-register, y los paquetes risk y warning
        val f4 =
            listOf(
                "AttentionWriteGuard.kt",
                "ConfidentialAttentionScope.kt",
                "BusinessDays.kt",
                "ConfidentialReaders.kt",
                "AttentionRegister.kt",
                "RiskAssessments.kt",
                "WarningRulesJob.kt",
                "RiskNotices.kt",
                "RiskRegister.kt"
            )
        assertTrue(names.containsAll(f4), "$names")
        // y la primera de sgspe.ai (F7, plan 7.1): un paquete de salida que ningún paquete de dominio importa
        assertTrue("KnowledgeMigration.kt" in names, "$names")
        // y las de 7.2a: la seudonimización, el asistente y su compuerta
        assertTrue(names.containsAll(listOf("Pseudonymizer.kt", "AssistantQuestions.kt", "AgentHttpGate.kt")), "$names")
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

    // la liberación del alcance de las QCS confidenciales (decisión 4 del plan 4.2a) es de una sola regla del sistema: common la
    // define, solo ConfidentialAttentionScope la honra (on) y solo RequestClosureListener, en su propio archivo, la abre (within)
    @Test
    fun `solo RequestClosureListener libera el alcance de las confidenciales`() {
        val definition = "/sgspe/common/ConfidentialRelease.kt"
        val scope = "/sgspe/scope/ConfidentialAttentionScope.kt"
        val listener = "/sgspe/attention/RequestClosureListener.kt"

        fun except(vararg allowed: String) = sources(main).filterNot { file -> allowed.any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(except(definition, scope, listener), CONFIDENTIAL_RELEASE))
        assertEquals(emptyList<String>(), offenders(except(listener), RELEASE_WITHIN))
        assertEquals(emptyList<String>(), offenders(except(scope), RELEASE_ON))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(listener) })
        assertTrue(flags(CONFIDENTIAL_RELEASE, "ConfidentialRelease.within(\"grievance\", \"owner_user_id\", id) { x() }"))
        assertTrue(flags(RELEASE_WITHIN, "ConfidentialRelease.within(\"grievance\", \"owner_user_id\", id) { x() }"))
        assertFalse(flags(RELEASE_WITHIN, "val release = ConfidentialRelease.on(objectName)"))
        assertTrue(flags(RELEASE_ON, "val release = ConfidentialRelease.on(objectName)"))
        assertFalse(flags(CONFIDENTIAL_RELEASE, "// ConfidentialRelease solo en RequestClosureListener"))
        assertFalse(flags(CONFIDENTIAL_RELEASE, "class ConfidentialAttentionScope"))
    }

    // la reposición de derivados ve el registro que repone aunque quien escribe ya no lo lea (ConfidentialAttentionScope): solo
    // DerivedFieldsKeeper construye un DerivedRestore, que vive en el contexto de la corrutina y un cliente http no puede poner
    @Test
    fun `solo DerivedFieldsKeeper construye un DerivedRestore`() {
        val keeper = "/sgspe/common/DerivedFieldsKeeper.kt"
        val definition = "/sgspe/common/DerivedRestore.kt"
        val others = sources(main).filterNot { file -> listOf(keeper, definition).any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, DERIVED_RESTORE_NEW))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(keeper) })
        assertTrue(flags(DERIVED_RESTORE_NEW, "withContext(DerivedRestore(change.objectName, change.recordId, values)) {"))
        assertTrue(flags(DERIVED_RESTORE_NEW, "val r = DerivedRestore (\"grievance\", id, emptyMap())"))
        assertFalse(flags(DERIVED_RESTORE_NEW, "val restore = DerivedRestore.current()"))
        assertFalse(flags(DERIVED_RESTORE_NEW, "class DerivedRestore"))
        assertFalse(flags(DERIVED_RESTORE_NEW, "// DerivedRestore(objeto, id) solo en el keeper"))
    }

    // la caché del esquema (plan corto del 2026-10-11): las definiciones de objeto se cargan solo por ObjectDefinitions (memo
    // por petición sobre la caché de wasichai 1.1.0). Records conserva la suya en el camino de la plataforma: los trabajos de fondo corren
    // fuera de una petición, donde el memo no aplica. vigila MetadataService.loadDefinition y loadDefinitionById, las cargas
    // sin permisos; definitionOf y listDefinitions miran los permisos de quien llama y quedan fuera
    @Test
    fun `solo ObjectDefinitions carga definiciones de objeto`() {
        val allowed = listOf("/sgspe/common/ObjectDefinitions.kt", "/sgspe/common/Records.kt")
        val others = sources(main).filterNot { file -> allowed.any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, LOAD_DEFINITION))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(allowed.first()) })
        assertTrue(flags(LOAD_DEFINITION, "metadata.loadDefinition(organizationId, name)"))
        assertTrue(flags(LOAD_DEFINITION, "metadata.loadDefinitionById(organizationId, id)"))
        assertTrue(flags(LOAD_DEFINITION, "val load = metadata::loadDefinition"))
        assertFalse(flags(LOAD_DEFINITION, "definitions.current(organizationId, name)"))
        assertFalse(flags(LOAD_DEFINITION, "// MetadataService.loadDefinition no tiene caché"))
    }

    // la compuerta de SQL propio (especificación de F6 §3.1, ASM-122): dentro de sgspe.territory solo SpatialGate y los
    // escritores que ya existían tocan DatabaseClient; fuera de territory, solo los que ya lo usaban. y solo SpatialGate pide
    // los alcances de lectura de wasichai (RecordReadScopes): nadie más arma SQL que cruce tablas del tenant
    @Test
    fun `solo SpatialGate y los escritores de territorio usan DatabaseClient`() {
        val territory = listOf("SpatialGate.kt", "GeoSql.kt", "TerritoryImports.kt", "Areas.kt", "ProjectComponents.kt").map { "/sgspe/territory/$it" }
        val elsewhere =
            listOf(
                "/sgspe/common/DependentsDeleteGuard.kt",
                "/sgspe/common/DerivedVersions.kt",
                "/sgspe/common/Idempotency.kt",
                "/sgspe/common/UserReferenceGuard.kt",
                "/sgspe/evidence/EvidenceWriteGuard.kt",
                "/sgspe/ai/KnowledgeChunks.kt"
            )
        val others = sources(main).filterNot { file -> (territory + elsewhere).any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, DATABASE_CLIENT))
        val gate = "/sgspe/territory/SpatialGate.kt"
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(gate) })
        assertEquals(emptyList<String>(), offenders(sources(main).filterNot { it.invariantSeparatorsPath.endsWith(gate) }, READ_SCOPES))
    }

    // knowledge_chunk es de sgspe.ai (ai-architecture.md §13.2, decisión 2 del plan 7.2c): solo su migración la crea y solo
    // KnowledgeChunks la lee y la escribe, con su SQL constante; nadie más nombra la tabla
    @Test
    fun `solo KnowledgeChunks toca knowledge_chunk`() {
        val allowed = listOf("/sgspe/ai/KnowledgeMigration.kt", "/sgspe/ai/KnowledgeChunks.kt")
        val others = sources(main).filterNot { file -> allowed.any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, KNOWLEDGE_TABLE))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(allowed.last()) })
        assertTrue(flags(KNOWLEDGE_TABLE, "db.sql(\"SELECT * FROM sgspe.knowledge_chunk\")"))
        assertTrue(flags(KNOWLEDGE_TABLE, "private const val TABLE = KnowledgeMigration.TABLE"))
        assertFalse(flags(KNOWLEDGE_TABLE, "// los fragmentos de knowledge_chunk"))
        assertFalse(flags(KNOWLEDGE_TABLE, "class KnowledgeChunks("))
    }

    // los bytes de un archivo sin pasar por FileService (que exige una persona) son solo de los trabajos de fondo, como la
    // plataforma (decisión 5 del plan 7.2c; desde el plan 8.2b también JOB-05 y DeliverableWriteGuard): solo StoredFiles usa
    // StoredFileRepository y FileStore de wasichai-files
    @Test
    fun `solo StoredFiles lee archivos sin FileService`() {
        val reader = "/sgspe/common/StoredFiles.kt"
        val others = sources(main).filterNot { it.invariantSeparatorsPath.endsWith(reader) }
        assertEquals(emptyList<String>(), offenders(others, STORED_FILES))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(reader) })
        assertTrue(flags(STORED_FILES, "import wasichai.files.StoredFileRepository"))
        assertTrue(flags(STORED_FILES, "    private val store: FileStore"))
        assertFalse(flags(STORED_FILES, "import wasichai.files.FileService"))
        assertFalse(flags(STORED_FILES, "// FileStore solo en StoredFiles"))
    }

    // el alcance de lectura por proyecto (ASM-106, ASM-114, security.md §9.7). regla 1: cada objeto de model.json está en un
    // solo grupo de ProjectScope y ProjectScope no nombra objetos ausentes; regla 2: los objetos con la relación `project`
    // son exactamente las raíces y project_assignment. así una fase nueva no agrega un objeto sin clasificarlo:
    // ProjectReadScope no admitiría ningún registro suyo
    @Test
    fun `cada objeto del modelo esta clasificado en ProjectScope`() {
        val model = JsonMapper.builder().build().readTree(File("model/model.json"))
        val objects = model["objects"].values().map { it["name"].asString() }
        val groups =
            mapOf(
                "project" to setOf(ProjectScope.PROJECT),
                "project_assignment" to setOf(ProjectScope.ASSIGNMENT),
                "ROOTS" to ProjectScope.ROOTS,
                "CHILDREN" to ProjectScope.CHILDREN.keys,
                "TENANT" to ProjectScope.TENANT
            )
        for (name in objects) {
            val holders = groups.filterValues { name in it }.keys
            assertEquals(1, holders.size, "$name está en $holders: clasifíquelo en un solo grupo de ProjectScope")
        }
        val named = ProjectScope.ROOTS + ProjectScope.CHILDREN.keys + ProjectScope.TENANT
        assertEquals(emptySet<String>(), named - objects.toSet(), "ProjectScope nombra objetos que model.json no tiene")
        val withProject = modelRelations().filter { it.field == "project" && it.target == "project" }.map { it.source }.toSet()
        assertEquals(ProjectScope.ROOTS + ProjectScope.ASSIGNMENT, withProject)
    }

    // regla 3: cada padre posible (campo, objeto) de un hijo es una relación (hijo, campo, objeto) del modelo, y el padre es una
    // raíz o un hijo
    @Test
    fun `cada padre posible de un hijo es una relacion del modelo hacia una raiz o un hijo`() {
        val relations = modelRelations().map { Triple(it.source, it.field, it.target) }.toSet()
        for ((child, parents) in ProjectScope.CHILDREN) {
            assertTrue(parents.isNotEmpty(), "$child no tiene padres")
            for ((field, parent) in parents) {
                assertTrue(Triple(child, field, parent) in relations, "$child.$field no apunta a $parent")
                assertTrue(parent in ProjectScope.ROOTS || parent in ProjectScope.CHILDREN, "$child: su padre $parent no es una raíz ni un hijo")
            }
        }
    }

    // regla 4: desde cada hijo, toda cadena llega a una raíz en a lo sumo MAX_HOPS saltos y ningún objeto es su propio ancestro:
    // ProjectReadCondition anida una subconsulta por salto
    @Test
    fun `cada cadena llega a una raiz en a lo sumo dos saltos y sin ciclos`() {
        for ((child, parents) in ProjectScope.CHILDREN) {
            for ((field, _) in parents) {
                val chain = ProjectScope.chain(child, field)
                assertTrue(chain.isNotEmpty() && chain.size <= ProjectScope.MAX_HOPS, "$child.$field: $chain")
                assertTrue(chain.last().second in ProjectScope.ROOTS, "$child.$field no llega a una raíz: $chain")
                val visited = listOf(child) + chain.map { it.second }
                assertEquals(visited.size, visited.toSet().size, "$child.$field pasa dos veces por un objeto: $visited")
            }
        }
        assertEquals(
            listOf("program_indicator" to "program_indicator", "social_program" to "social_program"),
            ProjectScope.chain("program_measurement", "program_indicator")
        )
    }

    // regla 5: un hijo que es padre de otro tiene un solo padre posible, con relación obligatoria: la cadena de su hijo es una
    // sola y nunca pasa por un registro sin padre
    @Test
    fun `un hijo que es padre de otro tiene un solo padre obligatorio`() {
        val required = modelRelations().filter { it.required }.map { it.source to it.field }.toSet()
        val intermediate =
            ProjectScope.CHILDREN.values
                .flatten()
                .map { it.second }
                .filter { it in ProjectScope.CHILDREN }
                .toSet()
        assertEquals(setOf("program_indicator"), intermediate)
        for (name in intermediate) {
            val parents = ProjectScope.CHILDREN.getValue(name)
            assertEquals(1, parents.size, "$name tiene varios padres y es padre de otro hijo")
            assertTrue(name to parents.single().first in required, "$name.${parents.single().first} no es obligatoria")
        }
    }

    // regla 6: un hijo con varios padres los tiene todos opcionales, forman exactamente un grupo de ExactlyOneGuard y no tiene
    // otra relación hacia un objeto de proyecto: un registro sin ninguno es huérfano y nadie lo lee salvo ADMIN y
    // READ_ALL_PROJECTS (nunca «sin padre, todos», security.md §9.7)
    @Test
    fun `un hijo con varios padres los tiene opcionales, en un grupo de exactamente uno y sin otro vinculo de proyecto`() {
        val relations = modelRelations()
        val several = ProjectScope.CHILDREN.filterValues { it.size > 1 }
        assertEquals(setOf("delivery"), several.keys)
        for ((child, parents) in several) {
            val fields = parents.map { it.first }
            val own = relations.filter { it.source == child && it.field != null }
            assertTrue(own.filter { it.field in fields }.none { it.required }, "$child: uno de sus padres es obligatorio")
            assertTrue(
                ExactlyOneGuard.GROUPS[child].orEmpty().any { it.toSet() == fields.toSet() },
                "$child: sus padres no forman un grupo de ExactlyOneGuard"
            )
            val others = own.filter { it.field !in fields && it.target in scoped() }.map { "$child.${it.field}" }
            assertEquals(emptyList<String>(), others, "$child tiene otro vínculo hacia un objeto de proyecto: acótelo como padre")
        }
    }

    // regla 7: un hijo con un único padre opcional se lee en todo el tenant cuando no lo tiene ("<padre> IS NULL OR …" de
    // ProjectReadCondition). la lista es fija: un hijo nuevo con padre opcional, o uno de estos con otra relación hacia un
    // objeto de proyecto, obliga a revisar ProjectScope.CHILDREN, ProjectReadCondition y security.md §9.7
    @Test
    fun `los hijos con un unico padre opcional son exactamente los revisados`() {
        val relations = modelRelations()
        val optional =
            ProjectScope.CHILDREN
                .filter { (child, parents) -> parents.size == 1 && relations.none { it.source == child && it.field == parents.single().first && it.required } }
                .keys
        assertEquals(
            setOf("locality_profile"),
            optional,
            "los hijos con un único padre opcional se leen en todo el tenant cuando no lo tienen: revise ProjectScope.CHILDREN, " +
                "ProjectReadCondition y security.md §9.7 antes de cambiar esta lista"
        )
        val others =
            relations
                .filter {
                    it.source in optional && it.field != null && it.field !=
                        ProjectScope.CHILDREN
                            .getValue(it.source)
                            .single()
                            .first
                }.filter { it.target in scoped() }
                .map { "${it.source}.${it.field}" }
        assertEquals(
            emptyList<String>(),
            others,
            "un hijo con un único padre opcional ganó otra relación hacia un objeto de proyecto: hágala otro padre posible " +
                "(nunca «sin padre, todos») y actualice esta prueba"
        )
    }

    // security.md §10.2 (reglas 3 y 8) y ASM-121: GeoServer lee la base con sus credenciales, sin permisos ni alcance, así que
    // model/layers.json solo nombra maestros de ProjectScope.TENANT sin campos restringidos en model/roles.json.
    // publish_layers.py lo valida con la partición del modelo y test_model.py contra security.md §9.7; esta regla, contra el
    // código que aplica el alcance
    @Test
    fun `las capas que publica GeoServer son maestros del tenant sin campos restringidos`() {
        val json = JsonMapper.builder().build()
        val layers =
            json
                .readTree(File("model/layers.json"))
                .values()
                .map { it["object"].asString() }
                .toSet()
        assertTrue(layers.isNotEmpty(), "model/layers.json no nombra ninguna capa")
        assertEquals(emptySet<String>(), layers - ProjectScope.TENANT, "capas de objetos de proyecto: GeoServer no aplica el alcance")
        val restricted =
            json
                .readTree(File("model/roles.json"))["restrictedFields"]
                .values()
                .map { it["object"].asString() }
                .toSet()
        assertEquals(emptySet<String>(), layers.intersect(restricted), "capas con campos restringidos: GeoServer no aplica permisos de campo")
    }

    // las relaciones de model.json: (objeto, campo, destino, obligatoria); una MANY_TO_MANY no tiene campo
    private data class ModelRelation(
        val source: String,
        val field: String?,
        val target: String,
        val required: Boolean
    )

    private fun modelRelations(): List<ModelRelation> =
        JsonMapper
            .builder()
            .build()
            .readTree(File("model/model.json"))["relationships"]
            .values()
            .map { rel ->
                ModelRelation(
                    rel["source"].asString(),
                    rel["fieldName"]?.takeUnless { it.isNull }?.asString(),
                    rel["target"].asString(),
                    rel["required"]?.asBoolean() == true
                )
            }

    // los objetos acotados por proyecto
    private fun scoped(): Set<String> = ProjectScope.ROOTS + ProjectScope.CHILDREN.keys + ProjectScope.PROJECT

    // la condición del alcance por proyecto la arma solo ProjectReadScope, y «lee todos los proyectos» lo preguntan solo common y
    // scope: los demás paquetes usan mine, assigned o reaches de ProjectReach (elección 6 de la especificación 4.4)
    @Test
    fun `solo ProjectReadScope arma la condicion y solo common y scope preguntan si se lee todo`() {
        val scope = "/sgspe/scope/ProjectReadScope.kt"
        val definition = "/sgspe/scope/ProjectReadCondition.kt"
        val others = sources(main).filterNot { file -> listOf(scope, definition).any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, READ_CONDITION))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(scope) })
        val outside = sources(main).filterNot { isCommon(it) || it.invariantSeparatorsPath.contains("/sgspe/scope/") }
        assertEquals(emptyList<String>(), offenders(outside, READS_ALL))
        assertTrue(flags(READ_CONDITION, "ProjectReadCondition.condition(group, columns, organization, user, today, bind)"))
        assertFalse(flags(READ_CONDITION, "// ProjectReadCondition solo en ProjectReadScope"))
        assertTrue(flags(READS_ALL, "if (reach.readsAll(user)) return null"))
        assertFalse(flags(READS_ALL, "val projects = reach.mine(user)"))
    }

    // el asistente de wasichai y Embabel son de sgspe.ai (especificación de F7 §3.3, ai-architecture.md §6.2): ningún otro
    // paquete importa wasichai.agent ni com.embabel
    @Test
    fun `solo sgspe ai usa el modulo de agente y Embabel`() {
        val outside = sources(main).filterNot { it.invariantSeparatorsPath.contains("/sgspe/ai/") }
        assertTrue(outside.isNotEmpty())
        assertEquals(emptyList<String>(), offenders(outside, AGENT_IMPORT))
        assertTrue(flags(AGENT_IMPORT, "import wasichai.agent.AgentService"))
        assertTrue(flags(AGENT_IMPORT, "import com.embabel.agent.core.AgentPlatform"))
        assertFalse(flags(AGENT_IMPORT, "import wasichai.core.data.RecordService"))
        assertFalse(flags(AGENT_IMPORT, "// wasichai.agent no se importa fuera de sgspe.ai"))
    }

    // especificación de F8 §2.2.16 (Review Focus 1 del plan 8.2a): en sgspe.dashboard solo KpiReaders lee como la plataforma,
    // y lo hace solo fuera de una petición (KpiReadersTest). un tablero o un endpoint nuevo que llame a asPlatform le daría a
    // una persona la postura, las QCS confidenciales o los proyectos que no tiene
    @Test
    fun `solo KpiReaders lee como la plataforma en sgspe dashboard`() {
        val reader = "/sgspe/dashboard/KpiReader.kt"
        val others = sources(File(main, "sgspe/dashboard")).filterNot { it.invariantSeparatorsPath.endsWith(reader) }
        assertTrue(others.isNotEmpty())
        assertEquals(emptyList<String>(), offenders(others, AS_PLATFORM))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(reader) })
        assertTrue(flags(AS_PLATFORM, "records.asPlatform(organization) { block() }"))
        assertFalse(flags(AS_PLATFORM, "// nunca asPlatform dentro de una petición"))
    }

    // WAS-ADR-0039: las M2M sin usuario solo las leen DashboardData (con el lector de la plataforma de KpiReaders, JOB-05),
    // StakeholderFollowUpJob (JOB-03, fuera de una petición) y, desde el plan 8.2b, ReportSources (el ensamblado de T2 en
    // JOB-05); Records.kt la define. otro llamador le daría a una persona
    // vínculos que no lee
    @Test
    fun `solo DashboardData, StakeholderFollowUpJob y ReportSources leen M2M como la plataforma`() {
        val allowed =
            listOf(
                "/sgspe/dashboard/DashboardData.kt",
                "/sgspe/stakeholder/StakeholderFollowUpJob.kt",
                "/sgspe/common/Records.kt",
                // plan 8.2b: el ensamblado de T2 (grupos de interés de un contacto, evidencias de una entrega)
                "/sgspe/deliverable/ReportSources.kt"
            )
        val others = sources(main).filterNot { f -> allowed.any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, RELATED_AS_PLATFORM))
        assertTrue(allowed.all { path -> sources(main).any { it.invariantSeparatorsPath.endsWith(path) } })
        assertTrue(flags(RELATED_AS_PLATFORM, "records.relatedAsPlatform(organization, \"engagement\", id, \"engagement_topic\")"))
        assertFalse(flags(RELATED_AS_PLATFORM, "// las M2M por Records.relatedAsPlatform"))
        assertFalse(flags(RELATED_AS_PLATFORM, "records.related(\"engagement\", id, \"engagement_topic\")"))
    }

    // decisión 12: la vía de plataforma de la compuerta solo la llaman ComponentLand, KpiAggregates (plan 8.2a-bis),
    // ProjectProximityQueries (plan 8.2b, una sola llamada: concessionTerritoriesAsPlatform) y ReportMapFeatures (plan 8.2c,
    // los mapas de informe de JOB-05), y la organización de la plataforma solo se la pasa DashboardData (la que arma
    // KpiReaders): ningún endpoint ni otro dominio lee SQL propio como la plataforma
    @Test
    fun `solo ComponentLand, KpiAggregates, ProjectProximityQueries y ReportMapFeatures usan selectAsPlatform y solo DashboardData les pasa la plataforma`() {
        val gate = "/sgspe/territory/SpatialGate.kt"
        val land = "/sgspe/territory/ComponentLand.kt"
        val aggregates = "/sgspe/dashboard/KpiAggregates.kt"
        // plan 8.2b (decisión 3): concesiones sobre territorios como la plataforma, para el predio de T2
        val proximity = "/sgspe/territory/ProjectProximityQueries.kt"
        val data = "/sgspe/dashboard/DashboardData.kt"
        // plan 8.2c (decisión 7): las geometrías de los mapas de informe, solo desde JOB-05
        val mapFeatures = "/sgspe/territory/ReportMapFeatures.kt"
        val notGateOrLand =
            sources(main).filterNot { f -> listOf(gate, land, aggregates, proximity, mapFeatures).any { f.invariantSeparatorsPath.endsWith(it) } }
        val notLandOrData = sources(main).filterNot { f -> listOf(land, data).any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(notGateOrLand, SELECT_AS_PLATFORM))
        val proximityFile = sources(main).single { it.invariantSeparatorsPath.endsWith(proximity) }
        assertEquals(1, offenders(listOf(proximityFile), SELECT_AS_PLATFORM).size)
        assertEquals(emptyList<String>(), offenders(notLandOrData, ROWS_WITH_PLATFORM))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(data) })
        assertTrue(flags(SELECT_AS_PLATFORM, "gate.selectAsPlatform(platform, READS, query, map)"))
        assertTrue(flags(ROWS_WITH_PLATFORM, "source.rows(filters.projects, platform)"))
        assertFalse(flags(ROWS_WITH_PLATFORM, "source.rows(filters.projects)"))
        // KpiAggregates solo lo usan DashboardData (con KpiContext.platform) y Dashboards (que lo inyecta)
        val notAggregatesUsers =
            sources(main).filterNot { f -> listOf(aggregates, data, "/sgspe/dashboard/Dashboards.kt").any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(notAggregatesUsers, KPI_AGGREGATES))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(aggregates) })
        assertTrue(flags(KPI_AGGREGATES, "private val aggregates: KpiAggregates"))
        assertFalse(flags(KPI_AGGREGATES, "// los conteos de KpiAggregates"))
        // la compuerta no se inyecta fuera de territory y KpiAggregates (SpatialGate.ID y SpatialGate.STATE son constantes)
        val gateUsers =
            sources(main).filterNot { f ->
                f.invariantSeparatorsPath.contains("/sgspe/territory/") || f.invariantSeparatorsPath.endsWith(aggregates)
            }
        assertEquals(emptyList<String>(), offenders(gateUsers, GATE_USE))
        assertTrue(flags(GATE_USE, "private val gate: SpatialGate"))
        assertFalse(flags(GATE_USE, "source.system(this, SpatialGate.STATE)"))
        assertFalse(flags(GATE_USE, "import sgspe.territory.SpatialGate"))
        assertFalse(flags(GATE_USE, "// la compuerta SpatialGate"))
        // y solo DashboardData le pasa la organización de la plataforma (KpiContext.platform) a KpiAggregates.select
        val notData = sources(main).filterNot { it.invariantSeparatorsPath.endsWith(data) }
        assertEquals(emptyList<String>(), offenders(notData, SELECT_WITH_PLATFORM))
        assertTrue(flags(SELECT_WITH_PLATFORM, "{ source.select(query, platform) }"))
        assertFalse(flags(SELECT_WITH_PLATFORM, "gate.select(query.reads, query.sql, query.map)"))
        // límite conocido: el vigía mira línea por línea y no ve una llamada partida por ktlint (`.select(` y `platform` en
        // líneas distintas); hoy ningún archivo lo hace
        assertFalse(flags(SELECT_WITH_PLATFORM, "source.select(\n query,\n platform\n)"))
    }

    // plan 8.2b (Global Constraints): en sgspe.deliverable solo JOB-05 y su puerto de archivos leen o escriben como la
    // plataforma; los endpoints (borradores, descargas, emisión, plantillas) corren como la persona
    @Test
    fun `en sgspe deliverable solo JOB-05 y DraftFiles usan asPlatform`() {
        val allowed = listOf("/sgspe/deliverable/DeliverableGenerationJob.kt", "/sgspe/deliverable/DraftFiles.kt")
        val others = sources(File(main, "sgspe/deliverable")).filterNot { f -> allowed.any { f.invariantSeparatorsPath.endsWith(it) } }
        assertTrue(others.isNotEmpty())
        assertEquals(emptyList<String>(), offenders(others, AS_PLATFORM))
    }

    // plan 8.2c (decisión 7): ReportMapFeatures lee como la plataforma, así que solo lo inyecta TerritoryMapData (MapData.kt),
    // el puerto de GeoServerReportMaps, que solo llama JOB-05; ningún endpoint ni otro dominio lo usa
    @Test
    fun `solo MapData usa ReportMapFeatures`() {
        val allowed = listOf("/sgspe/territory/ReportMapFeatures.kt", "/sgspe/deliverable/MapData.kt")
        val others = sources(main).filterNot { f -> allowed.any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, REPORT_MAP_FEATURES))
        assertTrue(allowed.all { path -> sources(main).any { it.invariantSeparatorsPath.endsWith(path) } })
        assertTrue(flags(REPORT_MAP_FEATURES, "private val reader: ReportMapFeatures"))
        assertTrue(flags(REPORT_MAP_FEATURES, "import sgspe.territory.ReportMapFeatures"))
        assertFalse(flags(REPORT_MAP_FEATURES, "// sus elementos los lee ReportMapFeatures"))
    }

    // plan 8.2c (Global Constraints): ninguna petición llama a GeoServer por un entregable ni lee sus geometrías como la
    // plataforma. ReportMaps, GeoServerReportMaps, MapData y TerritoryMapData solo los nombran sus archivos y JOB-05 (el único
    // que inyecta ReportMaps); los dobles de src/test no cuentan
    @Test
    fun `solo JOB-05 y sus archivos usan ReportMaps y MapData`() {
        val allowed =
            listOf(
                "/sgspe/deliverable/ReportMaps.kt",
                "/sgspe/deliverable/GeoServerReportMaps.kt",
                "/sgspe/deliverable/MapData.kt",
                "/sgspe/deliverable/DeliverableGenerationJob.kt"
            )
        val others = sources(main).filterNot { f -> allowed.any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, MAP_PORTS))
        assertTrue(allowed.all { path -> sources(main).any { it.invariantSeparatorsPath.endsWith(path) } })
        assertTrue(flags(MAP_PORTS, "private val maps: ReportMaps,"))
        assertTrue(flags(MAP_PORTS, "private val data: MapData"))
        assertTrue(flags(MAP_PORTS, "private val geoserver: GeoServerReportMaps"))
        assertTrue(flags(MAP_PORTS, "val reader = TerritoryMapData(features, definitions)"))
        assertFalse(flags(MAP_PORTS, "): ReportMapImages = ReportMapImages()"))
        assertFalse(flags(MAP_PORTS, "val fake = FakeReportMaps()"))
        assertFalse(flags(MAP_PORTS, "// los pide JOB-05 a ReportMaps"))
    }

    // especificación de F8 §5 («Modelo y arquitectura», plan 8.2c): el WebClient hacia Gotenberg y GeoServer solo en
    // sgspe.deliverable (GotenbergConverter y GeoServerReportMaps), más el proxy WMS de F6
    @Test
    fun `solo GotenbergConverter, GeoServerReportMaps y WmsProxy usan WebClient`() {
        val allowed = listOf("/sgspe/deliverable/GotenbergConverter.kt", "/sgspe/deliverable/GeoServerReportMaps.kt", "/sgspe/territory/WmsProxy.kt")
        val others = sources(main).filterNot { f -> allowed.any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, WEB_CLIENT))
        assertTrue(flags(WEB_CLIENT, "import org.springframework.web.reactive.function.client.WebClient"))
        assertFalse(flags(WEB_CLIENT, "// el WebClient de GeoServer"))
    }

    // la autenticación de una pregunta al asistente lleva su tabla de equivalencias (decisión 1 del plan 7.2a): solo
    // AssistantRun.kt pone otra autenticación en el contexto de Reactor
    @Test
    fun `solo AssistantRun cambia la autenticacion del contexto`() {
        val run = "/sgspe/ai/AssistantRun.kt"
        val others = sources(main).filterNot { it.invariantSeparatorsPath.endsWith(run) }
        assertEquals(emptyList<String>(), offenders(others, WITH_AUTHENTICATION))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(run) })
        assertTrue(flags(WITH_AUTHENTICATION, "ReactiveSecurityContextHolder.withAuthentication(tagged)"))
        assertFalse(flags(WITH_AUTHENTICATION, "// withAuthentication solo en AssistantRun"))
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
        assertTrue(DATABASE_CLIENT.containsMatchIn("    private val db: DatabaseClient,"))
        assertTrue(DATABASE_CLIENT.containsMatchIn("import org.springframework.r2dbc.core.DatabaseClient"))
        assertFalse(DATABASE_CLIENT.containsMatchIn("class DatabaseGateRunnerFake"))
        assertFalse(flags(DATABASE_CLIENT, "// sin DatabaseClient: por SpatialGate"))
        assertTrue(READ_SCOPES.containsMatchIn("        readScopes: RecordReadScopes,"))
        assertFalse(READ_SCOPES.containsMatchIn("class ProjectReadScope : RecordReadScope"))
    }

    // el nombre de un bean de estereotipo es el nombre simple de su clase con la inicial en minúscula: dos clases con el mismo
    // nombre simple en paquetes distintos, o una con el nombre de un bean de wasichai (`deliveries` de notificaciones), tumban
    // el arranque con BeanDefinitionOverrideException
    @Test
    fun `no hay dos beans de estereotipo con el mismo nombre simple`() {
        val stereotype = Regex("""^@(Component|Service|RestController|Controller|Configuration|Repository)\b""")
        val named = Regex("""^(?:(?:internal|open|abstract|private)\s+)*class\s+(\w+)""")
        val beans =
            sources(main).flatMap { file ->
                val lines = file.readLines().map { it.trim() }
                lines.mapIndexedNotNull { i, line ->
                    val m = named.find(line) ?: return@mapIndexedNotNull null
                    val annotated = (i - 1 downTo maxOf(0, i - 4)).map { lines[it] }.takeWhile { it.startsWith("@") }.any { stereotype.containsMatchIn(it) }
                    if (annotated) m.groupValues[1] to file.path else null
                }
            }
        assertTrue(beans.size > 20, "el escaneo no ve los beans: $beans")
        val duplicated = beans.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        assertTrue(duplicated.isEmpty(), "nombres de bean repetidos: $duplicated")
        // `Deliveries` chocaba con el bean `deliveries` de wasichai-notifications
        assertFalse(beans.any { it.first == "Deliveries" }, "Deliveries choca con el bean deliveries de wasichai-notifications")
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
        private val CONFIDENTIAL_RELEASE = Regex("""\bConfidentialRelease\b""")
        private val RELEASE_WITHIN = Regex("""\bConfidentialRelease\s*\.\s*within\b""")
        private val RELEASE_ON = Regex("""\bConfidentialRelease\s*\.\s*on\b""")
        private val DERIVED_RESTORE_NEW = Regex("""\bDerivedRestore\s*\(""")
        private val READ_CONDITION = Regex("""\bProjectReadCondition\b""")
        private val READS_ALL = Regex("""\.\s*readsAll\s*\(""")
        private val LOAD_DEFINITION = Regex("""\bloadDefinition(ById)?\b""")
        private val DATABASE_CLIENT = Regex("""\bDatabaseClient\b""")
        private val READ_SCOPES = Regex("""\bRecordReadScopes\b""")
        private val AS_PLATFORM = Regex("""\basPlatform\s*[({]""")
        private val RELATED_AS_PLATFORM = Regex("""\brelatedAsPlatform\s*\(""")
        private val SELECT_AS_PLATFORM = Regex("""\bselectAsPlatform\s*\(""")
        private val ROWS_WITH_PLATFORM = Regex("""\.rows\([^)]*\bplatform\b""")
        private val KPI_AGGREGATES = Regex("""\bKpiAggregates\b""")
        private val GATE_USE = Regex("""^(?!\s*import\b).*\bSpatialGate\b(?!\.(ID|STATE)\b)""")
        private val SELECT_WITH_PLATFORM = Regex("""\.select\([^)]*\bplatform\b""")
        private val AGENT_IMPORT = Regex("""^\s*import\s+(wasichai\.agent|com\.embabel)\.""")
        private val WITH_AUTHENTICATION = Regex("""\bwithAuthentication\b""")
        private val STORED_FILES = Regex("""\b(StoredFileRepository|FileStore)\b""")
        private val WEB_CLIENT = Regex("""\bWebClient\b""")
        private val REPORT_MAP_FEATURES = Regex("""\bReportMapFeatures\b""")
        private val MAP_PORTS = Regex("""\b(ReportMaps|GeoServerReportMaps|MapData|TerritoryMapData)\b""")
        private val KNOWLEDGE_TABLE = Regex("""\bknowledge_chunk\b|\bKnowledgeMigration\.TABLE\b""")

        // SgspeWrite y RecordService son nombres propios del proyecto: cualquier mención fuera de common es un hallazgo
        private val DIRECT_WRITE = Regex("""\b(SgspeWrite|RecordService)\b""")
        private val NON_DOMAIN_PACKAGES = listOf("common", "observability", "prod", "scope", "privacy", "dashboard", "deliverable", "ai")

        // deadline (BC-13, JOB-01) y notification (BC-14) son de dominio desde F3: no dependen de tableros, entregables ni IA
        private val DOMAIN_PACKAGES =
            listOf(
                "stakeholder",
                "engagement",
                "commitment",
                "agreement",
                "attention",
                "risk",
                "warning",
                "investment",
                "evidence",
                "territory",
                "deadline",
                "notification"
            )

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
