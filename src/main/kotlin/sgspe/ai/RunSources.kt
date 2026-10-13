package sgspe.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.Records
import sgspe.common.Values
import sgspe.dashboard.Dashboards
import sgspe.dashboard.IndicatorValue
import sgspe.dashboard.Kpis
import sgspe.evidence.EvidenceContent
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.util.UUID

// las fuentes de las corridas, leídas como la persona que pide (especificación de F7 §3.4, ai-architecture.md §6.1 «Document»):
// lo que no lee no llega al modelo. un registro que no existe o que no lee es el 404 de wasichai; una evidencia CONFIDENCIAL o DATOS
// PERSONALES sin DOWNLOAD_RESTRICTED, el 403 ACTION_REQUIRED de su descarga (RF-DOC-003); una fuente sin texto, 422 AI_NO_TEXT; un
// archivo que pasa sgspe.ai.limits.file-bytes, 422 AI_INPUT_TOO_LARGE. todo antes de llamar al modelo
@Component
class RunSources(
    private val records: Records,
    private val evidence: EvidenceContent,
    private val dashboards: Dashboards,
    private val extraction: TextExtraction,
    private val properties: AiProperties
) {
    // AI-01 y AI-03 sobre una evidencia: el texto de su archivo (PDF o DOCX), su proyecto y la única interacción y el único acuerdo
    // vinculados que se leen (los destinos de las extracciones de tema, lugar, fecha y cláusula)
    suspend fun evidenceText(id: UUID): RunSource {
        val record = records.get(EVIDENCE, id)
        // un 404 de la descarga cuando el registro sí se lee es una evidencia sin archivo: no tiene texto que leer
        val download =
            try {
                evidence.open(id)
            } catch (_: NotFoundException) {
                throw AiErrors.noText()
            }
        val limit = properties.limits.fileBytes
        if (download.file.sizeBytes > limit) throw AiErrors.fileTooLarge(limit)
        val bytes =
            try {
                val buffer = DataBufferUtils.join(download.content, limit).awaitSingle()
                try {
                    ByteArray(buffer.readableByteCount()).also { buffer.read(it) }
                } finally {
                    DataBufferUtils.release(buffer)
                }
            } catch (_: DataBufferLimitException) {
                throw AiErrors.fileTooLarge(limit)
            }
        val text =
            withContext(Dispatchers.IO) {
                extraction.extract(bytes, download.file.contentType, download.file.fileName, maxChars = properties.limits.inputChars)
            }
        return RunSource(
            objectName = EVIDENCE,
            id = id,
            project = Records.uuid(record.attributes[PROJECT]),
            text = text,
            inputReference = "$EVIDENCE:$id sha256:${download.file.sha256}",
            engagement = linked(id, "evidence_engagement"),
            agreement = linked(id, "evidence_agreement"),
            evidence = id
        )
    }

    // AI-01 y AI-03 sobre la minuta de una interacción; los nombres, teléfonos y correos de sus asistentes son valores restringidos
    // de la llamada (security.md §7.9)
    suspend fun minutes(id: UUID): RunSource {
        val record = records.get(ENGAGEMENT, id)
        val text = Values.text(record.attributes["minutes"]) ?: throw AiErrors.noText()
        return RunSource(
            objectName = ENGAGEMENT,
            id = id,
            project = Records.uuid(record.attributes[PROJECT]),
            text = text,
            inputReference = "$ENGAGEMENT:$id updated_at:${record.updatedAt}",
            restricted = restricted(participants(id)),
            engagement = id
        )
    }

    // AI-02: lo registrado de la interacción (tipo, título, objetivo, lugar, fechas, descripción, resultados, temas, grupos
    // representados y preguntas con su respuesta) y su acta vigente. los nombres de los asistentes no se mandan: son restringidos
    suspend fun engagementNotes(id: UUID): RunSource {
        val record = records.get(ENGAGEMENT, id)
        val attributes = record.attributes
        val participants = participants(id)
        val questions = quietly { records.all("engagement_question", mapOf(ENGAGEMENT to id.toString())) }
        val topics = quietly { records.related(ENGAGEMENT, id, "engagement_topic") }.mapNotNull { Values.text(it.attributes["name"]) }
        val groups =
            participants
                .filter { Values.bool(it.attributes["attended"]) != false }
                .mapNotNull { Values.text(it.attributes["represented_group"]) }
                .groupingBy { it }
                .eachCount()
        val text =
            buildList {
                line("Tipo", attributes["engagement_type"])
                line("Título", attributes["title"])
                line("Objetivo", attributes["objective"])
                line("Lugar", attributes["place"])
                line("Programada para", attributes["scheduled_at"])
                line("Realizada el", attributes["held_at"])
                line("Descripción", attributes["description"])
                line("Resultados", attributes["results"])
                if (topics.isNotEmpty()) add("Temas: ${topics.joinToString(", ")}")
                add("Asistentes registrados: ${participants.size}")
                if (groups.isNotEmpty()) add("Grupos representados: ${groups.entries.joinToString(", ") { (group, count) -> "$group ($count)" }}")
                if (questions.isNotEmpty()) {
                    add("Preguntas:")
                    questions.forEach { question ->
                        val asked = Values.text(question.attributes["question_text"]) ?: return@forEach
                        val answer = Values.text(question.attributes["answer"])?.let { " Respuesta: $it" }.orEmpty()
                        add("- $asked$answer")
                    }
                }
                line("Acta vigente", attributes["minutes"])
            }.joinToString("\n")
        return RunSource(
            objectName = ENGAGEMENT,
            id = id,
            project = Records.uuid(attributes[PROJECT]),
            text = text,
            inputReference = "$ENGAGEMENT:$id updated_at:${record.updatedAt}",
            restricted = restricted(participants),
            engagement = id
        )
    }

    // AI-04: el proyecto y los indicadores de sus tableros del mes en curso de Lima (el periodo por defecto de los tableros), con
    // los permisos de quien pide: un indicador que no puede ver llega como «no disponible» (dashboards.md §8), y un tablero que
    // no se puede leer, como una línea «no disponible» (nunca se calla: el modelo no lo confunde con cero). el tablero
    // territorial queda fuera: exige CRS de trabajo y sus cifras son de geometrías, no de gestión
    suspend fun projectSummary(id: UUID): RunSource {
        val record = records.get(PROJECT, id)
        val attributes = record.attributes
        val params = LinkedMultiValueMap<String, String>().apply { add(PROJECT, id.toString()) }
        var period: String? = null
        val indicators =
            SUMMARY_DASHBOARDS.flatMap { name ->
                val board =
                    try {
                        dashboards.dashboard(name, params)
                    } catch (_: WasichaiException) {
                        return@flatMap listOf(unavailableBoard(name))
                    }
                period = period ?: "${board.filters["periodStart"]} a ${board.filters["periodEnd"]}"
                board.indicators.map { indicatorLine(it) }
            }
        val text =
            buildList {
                line("Proyecto", listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["name"])).joinToString(" · "))
                line("Tipo", attributes["project_type"])
                line("Etapa", attributes["stage"])
                line("Descripción", attributes["description"])
                period?.let { add("Periodo de los indicadores: $it") }
                add("Indicadores:")
                addAll(indicators)
            }.joinToString("\n")
        return RunSource(
            objectName = PROJECT,
            id = id,
            project = id,
            text = text,
            inputReference = "$PROJECT:$id updated_at:${record.updatedAt}${period?.let { " periodo:${it.replace(" a ", "/")}" }.orEmpty()}"
        )
    }

    // AI-08: el texto que se clasifica (la descripción de una QCS, la pregunta y su respuesta, el titular y el resumen de una
    // mención) y los valores vigentes de los campos que se pueden proponer
    suspend fun classification(
        objectName: String,
        id: UUID
    ): RunSource {
        val record = records.get(objectName, id)
        val attributes = record.attributes
        val text =
            when (objectName) {
                "engagement_question" ->
                    listOfNotNull(Values.text(attributes["question_text"]), Values.text(attributes["answer"])?.let { "Respuesta: $it" }).joinToString("\n")
                "media_mention" -> listOfNotNull(Values.text(attributes["headline"]), Values.text(attributes["summary"])).joinToString("\n")
                else -> Values.text(attributes["description"]).orEmpty()
            }
        if (text.isBlank()) throw AiErrors.noText()
        val project =
            if (objectName == "engagement_question") {
                Records.uuid(attributes[ENGAGEMENT])?.let { Records.uuid(records.get(ENGAGEMENT, it).attributes[PROJECT]) }
            } else {
                Records.uuid(attributes[PROJECT])
            }
        return RunSource(
            objectName = objectName,
            id = id,
            project = project,
            text = text,
            inputReference = "$objectName:$id updated_at:${record.updatedAt}",
            current = attributes
        )
    }

    // los asistentes de la interacción que la persona lee; sin READ, ninguno
    private suspend fun participants(engagement: UUID): List<RecordResponse> =
        quietly { records.all("engagement_participant", mapOf(ENGAGEMENT to engagement.toString())) }

    // el único registro vinculado por la relación M2M que se lee; ninguno o varios, null
    private suspend fun linked(
        id: UUID,
        relationship: String
    ): UUID? = quietly { records.related(EVIDENCE, id, relationship, max = 1) }.singleOrNull()?.let { UUID.fromString(it.id) }

    private suspend fun <T> quietly(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (_: WasichaiException) {
            emptyList()
        }

    private fun MutableList<String>.line(
        label: String,
        value: Any?
    ) {
        Values.text(value)?.let { add("$label: $it") }
    }

    companion object {
        const val EVIDENCE = "evidence"
        const val ENGAGEMENT = "engagement"
        const val PROJECT = "project"

        // los tableros de gestión que resume AI-04
        val SUMMARY_DASHBOARDS = listOf(Kpis.STAKEHOLDERS, Kpis.ENGAGEMENT, Kpis.COMMITMENTS, Kpis.ATTENTION, Kpis.RISKS, Kpis.INVESTMENT)

        // los nombres, teléfonos y correos de los asistentes: salen con su marcador sin número y nunca se restauran; los campos y
        // sus marcadores son los de RestrictedFields (una sola fuente)
        fun restricted(participants: List<RecordResponse>): List<RestrictedValue> {
            val fields = RestrictedFields.of("engagement_participant")
            return participants.flatMap { row ->
                fields.mapNotNull { (field, marker) -> Values.text(row.attributes[field])?.let { RestrictedValue(marker, it) } }
            }
        }

        fun unavailableBoard(name: String): String = "- Tablero $name: no disponible"

        // «- KPI-COM-03 Compromisos vencidos: 4 %» o «no disponible»
        fun indicatorLine(value: IndicatorValue): String =
            if (!value.available || value.value == null) {
                "- ${value.code} ${value.label}: no disponible"
            } else {
                "- ${value.code} ${value.label}: ${value.value}${value.unit?.let { " $it" }.orEmpty()}"
            }
    }
}
