package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull

// el conversor de PDF de producción (especificación de F8 §3.3, deliverables.md §7.7, plan 8.2c): POST /forms/libreoffice/convert
// de Gotenberg con WebClient, solo desde JOB-05. lo que se envía lo arma SGSPE, nunca el cliente (§2.2, «Restricciones
// globales»): el DOCX como `files`, `updateIndexes` (índice y listas), `pdfa` PDF/A-2b en las candidatas y la cabecera
// Gotenberg-Trace con el id de la corrida; título, código y revisión van en las propiedades del DOCX (DocxWriter). con plazo
// sgspe.deliverable.gotenberg.timeout; sin URL, vencido, caído o con una respuesta que no es un PDF, PdfConverterUnavailable
// (PDF_CONVERTER_UNAVAILABLE). el log lleva el estado HTTP o la clase de la excepción, nunca el cuerpo
@Component
class GotenbergConverter(
    private val properties: DeliverableProperties
) : PdfConverter {
    // un WebClient propio, como WmsProxy: un PDF de un entregable cabe en el tope de los archivos que sube el servidor
    private val client: WebClient =
        WebClient
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize(properties.maxFileBytes.toInt()) }
            .build()

    override suspend fun convert(
        docx: ByteArray,
        request: PdfRequest
    ): ByteArray {
        val settings = properties.gotenberg
        val base =
            settings.url
                ?.trim()
                ?.trimEnd('/')
                ?.ifEmpty { null } ?: throw PdfConverterUnavailable("Este despliegue no tiene conversor de PDF")
        val pdf =
            try {
                withTimeoutOrNull(settings.timeout.toMillis()) {
                    client
                        .post()
                        .uri(base + ROUTE)
                        .headers { headers -> request.correlation?.let { headers.set(TRACE, it) } }
                        .body(BodyInserters.fromMultipartData(form(docx, request)))
                        .awaitExchangeOrNull { response ->
                            val type = response.headers().contentType().orElse(null)
                            if (response.statusCode().is2xxSuccessful && type != null && MediaType.APPLICATION_PDF.isCompatibleWith(type)) {
                                response.awaitBody<ByteArray>()
                            } else {
                                LOG.warn("Gotenberg respondió {} con {}", response.statusCode().value(), type)
                                response.releaseBody().awaitSingleOrNull()
                                null
                            }
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Gotenberg no devolvió el PDF: {}", e.javaClass.simpleName)
                null
            }
        return pdf ?: throw PdfConverterUnavailable("Gotenberg no devolvió el PDF")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GotenbergConverter::class.java)
        const val ROUTE = "/forms/libreoffice/convert"
        const val TRACE = "Gotenberg-Trace"
        const val PDFA = "PDF/A-2b"
        const val FILE_NAME = "entregable.docx"

        // el formulario: solo lo que SGSPE decide (decisión 3 del plan 8.2c)
        fun form(
            docx: ByteArray,
            request: PdfRequest
        ): MultiValueMap<String, HttpEntity<*>> =
            MultipartBodyBuilder()
                .apply {
                    part("files", ByteArrayResource(docx)).filename(FILE_NAME).contentType(MediaType.parseMediaType(Deliverables.DOCX_TYPE))
                    part("updateIndexes", "true")
                    if (request.pdfa) part("pdfa", PDFA)
                }.build()
    }
}
