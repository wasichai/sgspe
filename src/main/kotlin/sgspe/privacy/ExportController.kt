package sgspe.privacy

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Finding
import sgspe.common.StrictQuery
import sgspe.common.toCodedException

// POST /api/sgspe/exports/{kind} (api.md §4.13): el XLSX en streaming, con su fila de export_log ya escrita. el archivo
// temporal se borra al terminar, fallar o cancelarse el envío (ExportFile.stream); si nadie se suscribe, lo barre la siguiente
// exportación pasada una hora. los tipos de F3 y F4 se agregan con su módulo; uno que no existe aún es un 404
// EXPORT_NOT_FOUND. una exportación no crea registros de negocio: no lleva Idempotency-Key (api.md §3.5)
@RestController
@RequestMapping("/api/sgspe/exports")
class ExportController(
    private val register: StakeholderRegister,
    private val matrix: CommitmentMatrix,
    private val attention: AttentionRegister
) {
    @PostMapping("/{kind}")
    suspend fun export(
        @PathVariable kind: String,
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val export =
            when (kind) {
                "stakeholder-register" -> register.export(body)
                "commitment-matrix" -> matrix.export(body)
                "attention-register" -> attention.export(body)
                else ->
                    throw Finding(
                        "EXPORT_NOT_FOUND",
                        "La exportación $kind no existe o no está disponible en esta fase",
                        "kind",
                        status = HttpStatus.NOT_FOUND
                    ).toCodedException()
            }
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(export.stream())
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
