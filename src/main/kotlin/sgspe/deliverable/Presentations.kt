package sgspe.deliverable

import sgspe.privacy.Disclosure

// los nombres de deliverable_presentation (ADR-0031, deliverables.md §16.8, database-model): el objeto, sus campos, sus
// estados y los códigos de falla, que nunca llevan texto del documento
object Presentations {
    const val OBJECT = "deliverable_presentation"

    const val AUDIENCE = "audience"
    const val LEVEL = "disclosure_level"
    const val STATUS = "presentation_status"
    const val REQUESTED_AT = "requested_at"
    const val GENERATED_AT = "generated_at"
    const val FAILURE = "failure_code"
    const val SLIDES = "slide_count"
    const val SPEC = "spec"
    const val SPEC_SHA256 = "spec_sha256"
    const val PDF = "pdf"
    const val PDF_SHA256 = "pdf_sha256"
    const val MODEL_SHA256 = "model_sha256"
    const val PLANNER = "planner_version"
    const val DESIGN = "design_version"
    const val RENDERER = "renderer_version"
    const val DELIVERABLE = "deliverable"
    const val VERSION = "deliverable_version"

    const val QUEUED = "EN COLA"
    const val RUNNING = "EN PROCESO"
    const val READY = "LISTO"
    const val FAILED = "FALLIDO"

    const val SLIDES_UNAVAILABLE = "SLIDES_UNAVAILABLE"
    const val DECK_CHECK_FAILED = "DECK_CHECK_FAILED"
    const val MODEL_MISMATCH = "MODEL_MISMATCH"
    const val GENERATION_FAILED = "GENERATION_FAILED"
    const val FILE_TOO_LARGE = "FILE_TOO_LARGE"

    const val SPEC_TYPE = "text/plain"
    const val JOB_SOURCE = "job:presentation-generation"
    const val JOB_REASON = "JOB-09: composición de la presentación"
    const val JOB_LOCK = "sgspe.presentation"

    fun lock(id: String): String = "sgspe.presentation:$id"

    // del menos al más restrictivo: una presentación no puede salir con un nivel menos restrictivo que el de su versión
    private val ORDER = listOf(Disclosure.INTERNO, Disclosure.EXTERNO, Disclosure.PUBLICO)

    fun allowed(
        requested: Disclosure,
        version: Disclosure
    ): Boolean = ORDER.indexOf(requested) >= ORDER.indexOf(version)
}
