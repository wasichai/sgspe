package sgspe.deliverable

import java.time.Instant

// los nombres de BC-15 que usa más de una clase de sgspe.deliverable (domain-model §6.15, model/model.json): objetos, campos,
// estados de WF-H, estados del borrador, niveles y formatos
object Deliverables {
    const val DELIVERABLE = "deliverable"
    const val SECTION = "deliverable_section"
    const val VERSION = "deliverable_version"
    const val TEMPLATE = "deliverable_template"
    const val PROJECT = "project"

    // acciones declaradas sobre deliverable
    const val GENERATE = "GENERATE"
    const val APPROVE = "APPROVE"
    const val ISSUE = "ISSUE"
    const val EXPORT = "EXPORT"
    const val DOWNLOAD_RESTRICTED = "DOWNLOAD_RESTRICTED"

    // estados de WF-H
    const val DRAFT = "draft"
    const val IN_REVIEW = "in_review"
    const val OBSERVED = "observed"
    const val APPROVED = "approved"
    const val ISSUED = "issued"
    val REQUESTABLE: Set<String> = setOf(DRAFT, OBSERVED)

    // transiciones de WF-H que piden el borrador LISTO y sin secciones cambiadas
    const val SUBMIT = "submit_for_review"
    const val RESUBMIT = "resubmit"
    const val APPROVE_TRANSITION = "approve"
    const val ISSUE_TRANSITION = "issue"

    // campos de deliverable
    const val TYPE = "deliverable_type"
    const val CODE = "document_code"
    const val TITLE = "title"
    const val TEMPLATE_KEY = "template_key"
    const val PERIOD_START = "period_start_date"
    const val PERIOD_END = "period_end_date"
    const val CUTOFF = "cutoff_date"
    const val REQUESTED_AT = "generation_requested_at"
    const val STATUS = "draft_generation_status"
    const val GENERATED_AT = "draft_generated_at"
    const val MODEL_SHA256 = "draft_model_sha256"
    const val DRAFT_REVISION = "draft_revision"
    const val WARNINGS = "draft_warnings"
    const val CURRENT_VERSION = "current_version"
    const val CURRENT_REVISION = "current_revision"

    // los siete campos FILE del borrador (ASM-133)
    const val DRAFT_MODEL = "draft_model"
    const val DRAFT_INTERNAL_DOCX = "draft_internal_docx"
    const val DRAFT_INTERNAL_PDF = "draft_internal_pdf"
    const val DRAFT_EXTERNAL_PDF = "draft_external_pdf"
    const val CANDIDATE_INTERNAL_DOCX = "draft_candidate_internal_docx"
    const val CANDIDATE_INTERNAL_PDF = "draft_candidate_internal_pdf"
    const val CANDIDATE_EXTERNAL_PDF = "draft_candidate_external_pdf"
    val DRAFT_FILES: List<String> =
        listOf(
            DRAFT_MODEL,
            DRAFT_INTERNAL_DOCX,
            DRAFT_INTERNAL_PDF,
            DRAFT_EXTERNAL_PDF,
            CANDIDATE_INTERNAL_DOCX,
            CANDIDATE_INTERNAL_PDF,
            CANDIDATE_EXTERNAL_PDF
        )

    // lo que se sirve de un borrador (con la marca) y lo que nunca (las candidatas y el modelo)
    val SERVED_DRAFT_FILES: Set<String> = setOf(DRAFT_INTERNAL_DOCX, DRAFT_INTERNAL_PDF, DRAFT_EXTERNAL_PDF)

    // estados del borrador (catálogo «Estado de generación del borrador»)
    const val QUEUED = "EN COLA"
    const val RUNNING = "EN PROCESO"
    const val READY = "LISTO"
    const val FAILED = "FALLIDO"

    // campos de deliverable_section
    const val SECTION_KEY = "section_key"
    const val CONTENT = "content"
    const val CONTENT_ORIGIN = "content_origin"
    const val AI_ASSISTED = "ASISTIDO POR IA"
    const val HUMAN = "HUMANO"

    // campos de deliverable_version
    const val LEVEL = "disclosure_level"
    const val VERSION_NUMBER = "version_number"
    const val DOCX = "docx"
    const val PDF = "pdf"
    const val MODEL = "model"
    const val DERIVED_FROM = "derived_from"

    // niveles y formatos
    const val INTERNAL = "INTERNO"
    const val EXTERNAL = "EXTERNO"
    const val DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val PDF_TYPE = "application/pdf"
    const val MODEL_TYPE = "text/plain"

    // el candado de sesión de JOB-05 (ClusterLock.tryLock, con la clave tal cual) y el de la generación de cada entregable. es
    // otra clave que la de Transactions.locked("deliverable:<id>") de los comandos (Transactions antepone «sgspe.»): un pedido
    // de borrador o una transición no esperan a que termine una generación (DraftQueue atiende el pedido que llega mientras
    // tanto)
    const val JOB_LOCK = "sgspe.deliverable"

    fun generationLock(id: Any): String = "sgspe.deliverable-generation:$id"

    // el origen de las filas de auditoría de JOB-05 (WAS-ADR-0050) y el motivo de sus escrituras
    const val JOB_SOURCE = "job:deliverable-generation"
    const val JOB_REASON = "borrador de entregable (JOB-05 DeliverableGenerationJob)"

    // un instante guardado (generation_requested_at, draft_generated_at), o null si falta o no se entiende
    fun instant(raw: Any?): Instant? = raw?.toString()?.let { runCatching { Instant.parse(it) }.getOrNull() }

    // los códigos de aviso del borrador (draft_warnings): uno por línea, `<código>` o `<código> <sección>`
    const val MAP_FAILED = "MAP_FAILED"
    const val PDF_CHECK_FAILED = "PDF_CHECK_FAILED"
    const val PDF_CONVERTER_UNAVAILABLE = "PDF_CONVERTER_UNAVAILABLE"
    const val FILE_TOO_LARGE = "FILE_TOO_LARGE"
    const val GENERATION_FAILED = "GENERATION_FAILED"
    const val TEMPLATE_UNAVAILABLE = "TEMPLATE_UNAVAILABLE"
}
