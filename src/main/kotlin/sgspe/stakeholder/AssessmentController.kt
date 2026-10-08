package sgspe.stakeholder

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.IfMatch
import wasichai.core.data.ChangeReason
import java.net.URI
import java.util.UUID

// api.md §4.3. Idempotency-Key se acepta y se ignora en F2 (§3.5): se honra desde F3, cuando llega idempotency_key
@RestController
@RequestMapping("/api/sgspe/stakeholders/{id}/assessments")
class AssessmentController(
    private val assessments: Assessments
) {
    @PostMapping
    suspend fun register(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?
    ): ResponseEntity<AssessmentResult> {
        val result = assessments.register(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/objects/stakeholder_assessment/records/${result.assessment.id}"))
            .body(result)
    }

    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>
    ): AssessmentPreview = assessments.preview(id, body)
}
