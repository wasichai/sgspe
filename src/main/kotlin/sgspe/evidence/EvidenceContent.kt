package sgspe.evidence

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import wasichai.files.FileDownload
import wasichai.files.FileService
import java.nio.charset.StandardCharsets
import java.util.UUID

// la descarga de una evidencia (RF-DOC-003, api.md §4.9): READ sobre evidence como quien llama (un 403 o 404 si no la lee)
// y, si la clasificación es CONFIDENCIAL o DATOS PERSONALES, la acción DOWNLOAD_RESTRICTED. si quien llama no puede leer la
// clasificación, se la trata como restringida (falla cerrado, EvidenceRules.downloadRestricted). los bytes los entrega
// FileService de wasichai-files, que vuelve a leer el registro como quien llama; aquí se agregan el ETag (el SHA-256, para lo
// inmutable, api.md §3.10), attachment siempre (nunca se muestra en línea), nosniff y no-store
@Service
class EvidenceContent(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val files: FileService
) {
    suspend fun download(
        id: UUID,
        field: String
    ): ResponseEntity<Flux<DataBuffer>> {
        val download = open(id, field)
        val file = download.file
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .contentLength(file.sizeBytes)
            .eTag(file.sha256)
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(file.fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            ).header(NOSNIFF, "nosniff")
            .body(download.content)
    }

    // el archivo de la evidencia con la misma comprobación que su descarga: READ como quien llama (404 si no la lee) y, si es
    // CONFIDENCIAL o DATOS PERSONALES, DOWNLOAD_RESTRICTED (403 ACTION_REQUIRED). desde F7 también lo lee la IA (sgspe.ai, plan
    // 7.2b) antes de extraer el texto de AI-01 y AI-03: la IA ve solo lo que quien pide puede descargar (RF-DOC-003)
    suspend fun open(
        id: UUID,
        field: String = EvidenceRules.FILE
    ): FileDownload {
        val user = callers.require()
        val evidence = records.get(EvidenceRules.EVIDENCE, id)
        if (EvidenceRules.downloadRestricted(evidence.attributes) &&
            !permissions.has(user, EvidenceRules.DOWNLOAD_RESTRICTED, EvidenceRules.EVIDENCE)
        ) {
            throw Finding(
                "ACTION_REQUIRED",
                "Descargar una evidencia CONFIDENCIAL o DATOS PERSONALES exige la acción DOWNLOAD_RESTRICTED sobre evidence",
                EvidenceRules.CLASSIFICATION,
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
        return files.open(EvidenceRules.EVIDENCE, id, field)
    }

    companion object {
        const val NOSNIFF = "X-Content-Type-Options"
    }
}

// las dos rutas de descarga de una evidencia pasan por la misma comprobación. la nativa de wasichai-files
// (GET /api/objects/{object}/records/{id}/files/{field}) solo mira READ y los permisos de campo: aquí se la reemplaza para
// evidence con una ruta más específica (el segmento literal `evidence` gana al patrón {object}), así que la clasificación
// también vale por la API genérica y por los componentes de @wasichai/ui que bajan un campo FILE
@RestController
class EvidenceContentController(
    private val content: EvidenceContent
) {
    @GetMapping("/api/sgspe/evidence/{id}/content")
    suspend fun content(
        @PathVariable id: UUID,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return content.download(id, EvidenceRules.FILE)
    }

    @GetMapping("/api/objects/evidence/records/{id}/files/{field}")
    suspend fun native(
        @PathVariable id: UUID,
        @PathVariable field: String
    ): ResponseEntity<Flux<DataBuffer>> = content.download(id, field)
}
