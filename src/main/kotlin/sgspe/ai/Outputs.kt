package sgspe.ai

// las salidas estructuradas de los agentes de 7.2b (ai-architecture.md §6.1): clases de Kotlin de las que Embabel deriva el esquema
// que pide al modelo, con valores por defecto para que una salida incompleta se lea igual y la validación la descarte. las claves
// son los nombres técnicos en inglés; los valores de catálogo, los de §10 del análisis. nada de esto se guarda tal cual: lo valida
// cada agente y lo guarda SuggestionWriter

// AI-01: los hallazgos de un documento. `item` elige la fila de la lista permitida (actor, topic, place, date, clause)
data class ExtractionOutput(
    val items: List<ExtractedItem> = emptyList()
)

data class ExtractedItem(
    val item: String = "",
    // actor: organization o person
    val kind: String? = null,
    // actor y place: el nombre tal como aparece en el texto (con sus marcadores)
    val name: String? = null,
    val organizationType: String? = null,
    val stakeholderType: String? = null,
    // topic: el id de un tema de la lista que recibió el modelo
    val topic: String? = null,
    // place: locality o community; date: signed_date, effective_date o expiry_date
    val field: String? = null,
    // date: la fecha en ISO (2027-03-31)
    val value: String? = null,
    val clauseNumber: String? = null,
    val clauseText: String? = null,
    val createsCommitment: Boolean? = null,
    val quote: String = "",
    val confidence: Double? = null
)

// AI-03: los posibles compromisos de un acta («possible commitment», nunca un compromiso creado)
data class CommitmentOutput(
    val commitments: List<ProposedCommitment> = emptyList()
)

data class ProposedCommitment(
    val text: String = "",
    val quote: String = "",
    val beneficiaries: String? = null,
    val dueDate: String? = null,
    val confidence: Double? = null
)

// AI-02 y AI-04: un texto Markdown sin HTML
data class DraftText(
    val markdown: String = "",
    val confidence: Double? = null
)

// AI-08: un valor de catálogo (o un tema) por campo de la lista permitida
data class ClassificationOutput(
    val proposals: List<ProposedClassification> = emptyList()
)

data class ProposedClassification(
    val field: String = "",
    val value: String = "",
    val quote: String = "",
    val confidence: Double? = null
)
