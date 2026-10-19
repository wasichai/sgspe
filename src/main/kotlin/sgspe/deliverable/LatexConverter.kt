package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull

// el compositor de PDF de producción (ADR-0030, deliverables.md §7.7): POST /render de sgspe-latex con WebClient, solo desde
// JOB-05. envía el paquete de LatexWriter como tar y la cabecera X-Trace con el id de la corrida, nada que venga del cliente;
// recibe un tar con candidata.pdf y borrador.pdf. con plazo sgspe.deliverable.latex.timeout; sin URL, vencido, caído o con una
// respuesta que no es el tar de los dos PDF, PdfConverterUnavailable (PDF_CONVERTER_UNAVAILABLE); un 422 (el paquete no
// compuso), PdfCompositionFailed (GENERATION_FAILED). el log lleva el estado HTTP,
// el código del error y la línea de main.tex, o la clase de la excepción, nunca el cuerpo
@Component
class LatexConverter(
    private val properties: DeliverableProperties
) : PdfRenderer {
    // un WebClient propio: los dos PDF de un nivel caben en dos veces el tope de los archivos que sube el servidor
    private val client: WebClient =
        WebClient
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize((2 * properties.maxFileBytes + TAR_OVERHEAD).toInt()) }
            .build()

    override suspend fun render(
        bundle: LatexWriter.Bundle,
        request: PdfRequest
    ): RenderedPdfs {
        val settings = properties.latex
        val base =
            settings.url
                ?.trim()
                ?.trimEnd('/')
                ?.ifEmpty { null } ?: throw PdfConverterUnavailable("Este despliegue no tiene compositor de PDF")
        val body = Tar.write(bundle.files)
        var overfull: Int? = null
        var refused = false
        val reply =
            (
                try {
                    withTimeoutOrNull(settings.timeout.toMillis()) {
                        client
                            .post()
                            .uri(base + ROUTE)
                            .contentType(TAR)
                            .headers { headers -> request.correlation?.let { headers.set(TRACE, it) } }
                            .bodyValue(body)
                            .awaitExchangeOrNull { response ->
                                val type = response.headers().contentType().orElse(null)
                                if (response.statusCode().is2xxSuccessful && type != null && type.equalsTypeAndSubtype(TAR)) {
                                    overfull =
                                        response
                                            .headers()
                                            .header(OVERFULL)
                                            .firstOrNull()
                                            ?.toIntOrNull()
                                    response.awaitBody<ByteArray>()
                                } else {
                                    // el error de sgspe-latex es JSON con su código y la línea de main.tex, sin texto del documento
                                    val detail = runCatching { response.awaitBody<String>() }.getOrNull()?.take(DETAIL_CHARS)
                                    LOG.warn("sgspe-latex respondió {}: {}", response.statusCode().value(), detail)
                                    refused = response.statusCode().value() == UNPROCESSABLE
                                    null
                                }
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DataBufferLimitException) {
                    throw PdfTooLarge()
                } catch (e: Exception) {
                    if (generateSequence<Throwable>(e) { it.cause }.any { it is DataBufferLimitException }) throw PdfTooLarge()
                    LOG.warn("sgspe-latex no devolvió los PDF: {}", e.javaClass.simpleName)
                    null
                }
            ) ?: if (refused) throw PdfCompositionFailed() else throw PdfConverterUnavailable("sgspe-latex no devolvió los PDF")
        val files = runCatching { Tar.read(reply) }.getOrNull()
        val candidate = files?.get(CANDIDATE)
        val draft = files?.get(DRAFT)
        if (candidate == null || draft == null) throw PdfConverterUnavailable("sgspe-latex devolvió una respuesta sin los dos PDF")
        if (candidate.size > properties.maxFileBytes || draft.size > properties.maxFileBytes) throw PdfTooLarge()
        if ((overfull ?: 0) > 0) LOG.info("sgspe-latex: {} cajas desbordadas (corrida {})", overfull, request.correlation)
        return RenderedPdfs(candidate, draft, overfull)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LatexConverter::class.java)
        const val ROUTE = "/render"
        const val TRACE = "X-Trace"
        const val OVERFULL = "X-Latex-Overfull"
        const val CANDIDATE = "candidata.pdf"
        const val DRAFT = "borrador.pdf"
        val TAR: MediaType = MediaType.parseMediaType("application/x-tar")
        private const val TAR_OVERHEAD = 64 * 1024L
        private const val DETAIL_CHARS = 200
        private const val UNPROCESSABLE = 422
    }
}
