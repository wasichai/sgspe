package sgspe.ai

import sgspe.common.ModelCatalog
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// los dobles de las corridas de 7.2b (ai-architecture.md §15): lo que la validación leería del sistema, fijo, y los agentes armados
// sobre ModelCalls con un modelo de prueba, el registro de interacciones en memoria y los prompts publicados. todo ficticio
class FakeLookups(
    val topics: List<TopicOption> = emptyList(),
    val places: Map<Pair<String, String>, UUID> = emptyMap(),
    val organizations: Map<String, UUID> = emptyMap(),
    val texts: Set<String> = emptySet()
) : RunLookups {
    override suspend fun topics(): List<TopicOption> = topics

    override suspend fun place(
        field: String,
        name: String
    ): UUID? = places[field to name]

    override suspend fun organization(name: String): UUID? = organizations[name]

    override suspend fun knownTexts(project: UUID): Set<String> = texts
}

object RunFixtures {
    val CATALOG: ModelCatalog = ModelCatalog.fromClasspath()
    val ANA: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    val PROJECT: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    val ENGAGEMENT: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
    val AGREEMENT: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000d1")
    val EVIDENCE: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000e1")
    val NAMES = NameDictionary(listOf(KnownPerson(ANA, "Ana", "Ejemplo")))

    // los prompts publicados y los modelos de application.yml, con límites de prueba
    fun properties(
        actions: Int = 8,
        proposals: Int = 50
    ) = AiProperties(
        models = listOf("ai-01", "ai-02", "ai-03", "ai-04", "ai-08").associateWith { "modelo-de-prueba" },
        prompts = listOf("ai-01", "ai-02", "ai-03", "ai-04", "ai-08").associateWith { "1" },
        limits = AiLimits(actions = actions, proposals = proposals)
    )

    fun calls(
        model: StructuredModel,
        log: InteractionLog = RecordedInteractions(),
        properties: AiProperties = properties()
    ) = ModelCalls(model, Prompts(properties), log, properties, Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC))

    // un acta ficticia: Ana Ejemplo, registrada, con su DNI y su celular, y una participante no registrada
    const val MINUTES =
        "Acta de la asamblea de la Comunidad Ejemplo del 6 de octubre de 2026.\n" +
            "Ana Ejemplo (DNI 40123456, celular 987654321) pidió reparar el canal de riego del sector bajo.\n" +
            "La empresa se compromete a reparar el canal de riego antes del 30 de noviembre de 2026.\n" +
            "Rosa Ccori Ejemplo agradeció la reunión."

    fun minutes(
        project: UUID? = PROJECT,
        engagement: UUID? = ENGAGEMENT,
        agreement: UUID? = null,
        text: String = MINUTES
    ) = RunSource(
        objectName = "engagement",
        id = ENGAGEMENT,
        project = project,
        text = text,
        inputReference = "engagement:$ENGAGEMENT updated_at:2026-10-07T12:00:00Z",
        restricted = listOf(RestrictedValue(Marker.PERSONA, "Rosa Ccori Ejemplo")),
        engagement = engagement,
        agreement = agreement
    )
}
