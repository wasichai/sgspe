package sgspe.stakeholder

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.CommandAnswer
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.StrictQuery
import wasichai.core.data.ChangeReason
import java.util.UUID

// api.md §4.3. desde F3 el registro honra Idempotency-Key (decisión D3): un reenvío con la misma clave devuelve la respuesta de
// la primera evaluación sin evaluar otra vez. el ETag es el updatedAt del grupo de interés, el registro que el portal sigue
// editando
@RestController
@RequestMapping("/api/sgspe/stakeholders/{id}/assessments")
class AssessmentController(
    private val assessments: Assessments,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun register(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            val result = assessments.register(id, body, ChangeReason.fromHeader(reason), ifMatch)
            CommandAnswer(
                HttpStatus.CREATED,
                result,
                result.stakeholder.updatedAt,
                "/api/objects/stakeholder_assessment/records/${result.assessment.id}"
            )
        }
    }

    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): AssessmentPreview {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return assessments.preview(id, body)
    }
}
