package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest
import sgspe.common.Callers
import wasichai.core.common.NotFoundException
import java.util.UUID

// la recuperación de AI-06 con permisos, de seguridad (ai-architecture.md §13.3 y §15, security.md §14 «IA», especificación de
// F7 §5): nunca devuelve fragmentos de otro tenant, de una clasificación que la persona no puede descargar (también si subió
// después de indexar) ni de una evidencia que no puede leer; el proyecto no se mezcla con otro, el tenant sí entra, y una evidencia
// reemplazada no vuelve. los fragmentos se siembran con KnowledgeChunks, como los escribirá JOB-07, con una palabra única por prueba
class KnowledgeSearchApiTest : F7ApiTest() {
    @Autowired
    private lateinit var search: KnowledgeSearch

    @Autowired
    private lateinit var chunks: KnowledgeChunks

    @Autowired
    private lateinit var callers: Callers

    // una palabra que solo esta prueba escribe: los fragmentos de otras pruebas no la tienen
    private val word =
        "reservorio" +
            UUID
                .randomUUID()
                .toString()
                .filter { it.isLetter() }
                .take(8)
    private val seeded = mutableListOf<Pair<UUID, UUID>>()

    @AfterEach
    fun cleanUp() {
        runBlocking { seeded.groupBy({ it.first }, { it.second }).forEach { (organization, evidences) -> chunks.delete(organization, evidences) } }
    }

    private fun index(
        evidence: String,
        project: String?,
        classification: String = "INTERNA",
        organization: UUID = organizationId()
    ) {
        val id = UUID.fromString(evidence)
        runBlocking { chunks.replace(organization, id, project?.let(UUID::fromString), classification, listOf("El acta pide un $word para el anexo norte.")) }
        seeded += organization to id
    }

    private fun found(
        account: Account,
        project: String = testProject()
    ): List<String> = asUser(account) { search.search(callers.require(), UUID.fromString(project), "$word anexo") }.map { it.evidenceId.toString() }

    @Test
    fun `nunca llegan fragmentos de otro tenant, aunque nombren una evidencia que la persona lee`() {
        val evidence = evidence(testProject())
        index(evidence, testProject(), organization = UUID.randomUUID())
        val social = member("SOCIAL_MANAGER")
        assertEquals(emptyList<String>(), found(social))
        index(evidence, testProject())
        assertEquals(listOf(evidence), found(social))
    }

    @Test
    fun `una clasificacion que la persona no puede descargar no llega`() {
        val evidence = evidence(testProject(), classification = "CONFIDENCIAL")
        index(evidence, testProject(), "CONFIDENCIAL")
        assertEquals(emptyList<String>(), found(member("PROJECT_MANAGER")))
        assertEquals(listOf(evidence), found(member("SOCIAL_MANAGER")))
    }

    @Test
    fun `manda la clasificacion de hoy, no la que se copio al indexar`() {
        val evidence = evidence(testProject())
        index(evidence, testProject(), "INTERNA")
        val manager = member("PROJECT_MANAGER")
        assertEquals(listOf(evidence), found(manager))
        val raised =
            patch("/api/sgspe/evidence/$evidence", mapOf("attributes" to mapOf("classification" to "DATOS PERSONALES")), token, ifMatch("evidence", evidence))
        assertEquals(HttpStatus.OK, raised.status, raised.body)
        assertEquals(emptyList<String>(), found(manager))
    }

    @Test
    fun `una evidencia que la persona no puede leer no llega, aunque sus fragmentos digan otro proyecto`() {
        val evidence = evidence(project())
        index(evidence, testProject())
        assertEquals(emptyList<String>(), found(member("SOCIAL_MANAGER")))
    }

    @Test
    fun `los documentos de otro proyecto no se mezclan y los del tenant si`() {
        val other = project()
        val elsewhere = evidence(other)
        index(elsewhere, other)
        val tenantWide = evidence(null)
        index(tenantWide, null)
        val social = member("SOCIAL_MANAGER")
        assertEquals(listOf(tenantWide), found(social))
    }

    @Test
    fun `un proyecto que la persona no lee es 404`() {
        val social = member("SOCIAL_MANAGER")
        assertThrows<NotFoundException> { found(social, project()) }
    }

    @Test
    fun `una evidencia reemplazada no llega`() {
        val first = evidence(testProject())
        val response =
            createEvidence(
                mapOf(
                    "file" to stagedFile("evidence", "file"),
                    "evidence_type" to "ACTA",
                    "classification" to "INTERNA",
                    "project" to testProject(),
                    "replaces" to first
                )
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val second = tree(response.body)["id"].asString()
        index(first, testProject())
        index(second, testProject())
        assertEquals(listOf(second), found(member("SOCIAL_MANAGER")))
    }
}
