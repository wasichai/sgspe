package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.common.WasichaiException
import java.util.UUID

// un tema que el modelo puede proponer: los activos que la persona lee (topic es del tenant)
data class TopicOption(
    val id: UUID,
    val name: String
)

// lo que la validación de los agentes lee del sistema, como la persona (ai-architecture.md §6.1: «destino existente y legible»,
// «duplicados frente al proyecto»). un puerto para que las pruebas unitarias de los agentes no necesiten base
interface RunLookups {
    // los temas activos que se leen, para AI-01 y AI-08
    suspend fun topics(): List<TopicOption>

    // la única `locality` o `community` (según `field`) que se lee con ese nombre exacto; ninguna o varias, null
    suspend fun place(
        field: String,
        name: String
    ): UUID?

    // la única `organization` que se lee con ese nombre exacto; ninguna o varias, null
    suspend fun organization(name: String): UUID?

    // los textos plegados de los compromisos (título y descripción) y de los candidatos del proyecto: un candidato de la IA que
    // repite uno se descarta
    suspend fun knownTexts(project: UUID): Set<String>
}

// RunLookups con Records, como la persona: lo que no lee no existe para la IA. un error de lectura (sin READ, un tope) es «nada»:
// la propuesta que lo necesitaba se descarta, nunca se guarda a ciegas
@Component
class RecordLookups(
    private val records: Records
) : RunLookups {
    override suspend fun topics(): List<TopicOption> =
        quietly(emptyList()) {
            records
                .all(TOPIC, max = MAX_TOPICS)
                .filter { Values.bool(it.attributes["active"]) == true }
                .mapNotNull { row -> Values.text(row.attributes["name"])?.let { TopicOption(UUID.fromString(row.id), it) } }
        }

    override suspend fun place(
        field: String,
        name: String
    ): UUID? = single(field, name)

    override suspend fun organization(name: String): UUID? = single(ORGANIZATION, name)

    override suspend fun knownTexts(project: UUID): Set<String> {
        val filter = mapOf("project" to project.toString())
        val commitments =
            quietly(emptyList()) {
                records.all(COMMITMENT, filter)
            }.flatMap { listOfNotNull(Values.text(it.attributes["title"]), Values.text(it.attributes["description"])) }
        val candidates = quietly(emptyList()) { records.all(CANDIDATE, filter) }.mapNotNull { Values.text(it.attributes["proposed_text"]) }
        return (commitments + candidates).map { Proposals.folded(it) }.toSet()
    }

    // con max = 1, Records.all lanza TOO_MANY_RECORDS si hay dos: un nombre ambiguo no se resuelve
    private suspend fun single(
        objectName: String,
        name: String
    ): UUID? {
        val wanted = name.trim().ifEmpty { return null }
        return quietly(null) { records.all(objectName, mapOf("name" to wanted), max = 1).singleOrNull()?.let { UUID.fromString(it.id) } }
    }

    private suspend fun <T> quietly(
        fallback: T,
        block: suspend () -> T
    ): T =
        try {
            block()
        } catch (_: WasichaiException) {
            fallback
        }

    companion object {
        const val TOPIC = "topic"
        const val ORGANIZATION = "organization"
        const val COMMITMENT = "commitment"
        const val CANDIDATE = "commitment_candidate"
        const val MAX_TOPICS = 500
    }
}
