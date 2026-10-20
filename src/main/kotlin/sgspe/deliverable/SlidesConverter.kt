package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull
import tools.jackson.databind.json.JsonMapper

// lo que devuelve el compositor de láminas: el PDF, cuántas láminas dice que compuso y sus versiones (ADR-0031, §16.7)
class RenderedDeck(
    val pdf: ByteArray,
    val slides: Int?,
    val renderer: String?,
    val design: String?
)

// sgspe-slides no respondió, venció el plazo o no está configurado: SLIDES_UNAVAILABLE
class SlidesUnavailable(
    message: String
) : RuntimeException(message)

// una lámina no pasó la verificación de la composición (SLIDE_OVERFLOW, SLIDE_OVERLAP, TEXT_TOO_SMALL, SLIDE_EMPTY,
// FONT_MISSING o ASSET_INVALID), o la especificación no es válida (SPEC_INVALID): el código, nunca texto del documento
class SlideFailed(
    val code: String,
    val slide: String?
) : RuntimeException(code)

// el puerto del compositor de láminas: la especificación en JSON canónico y el id de la corrida (X-Trace)
fun interface SlidesRenderer {
    suspend fun render(
        spec: ByteArray,
        correlation: String
    ): RenderedDeck
}

// el compositor de producción (ADR-0031): POST /render de sgspe-slides con WebClient, solo desde JOB-09. envía la
// especificación y la cabecera X-Trace, nada que venga del cliente; recibe el PDF. sin URL, vencido, caído o con una respuesta
// que no es un PDF, SlidesUnavailable; un 400 o un 422, SlideFailed con su código y la lámina; un PDF más grande que el tope,
// PdfTooLarge. el log lleva el estado y el código, nunca el cuerpo
@Component
class SlidesConverter(
    private val properties: DeliverableProperties
) : SlidesRenderer {
    private val client: WebClient =
        WebClient
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize((properties.maxFileBytes + OVERHEAD).toInt()) }
            .build()
    private val json = JsonMapper.builder().build()

    override suspend fun render(
        spec: ByteArray,
        correlation: String
    ): RenderedDeck {
        val settings = properties.slides
        val base =
            settings.url
                ?.trim()
                ?.trimEnd('/')
                ?.ifEmpty { null } ?: throw SlidesUnavailable("Este despliegue no tiene compositor de láminas")
        var failure: SlideFailed? = null
        var meta: Triple<Int?, String?, String?> = Triple(null, null, null)
        val pdf =
            try {
                withTimeoutOrNull(settings.timeout.toMillis()) {
                    client
                        .post()
                        .uri(base + ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TRACE, correlation)
                        .bodyValue(spec)
                        .awaitExchangeOrNull { response ->
                            val status = response.statusCode().value()
                            val type = response.headers().contentType().orElse(null)
                            if (response.statusCode().is2xxSuccessful && type != null && type.equalsTypeAndSubtype(MediaType.APPLICATION_PDF)) {
                                val headers = response.headers()
                                meta =
                                    Triple(
                                        headers.header(COUNT).firstOrNull()?.toIntOrNull(),
                                        headers.header(RENDERER).firstOrNull(),
                                        headers.header(DESIGN).firstOrNull()
                                    )
                                response.awaitBody<ByteArray>()
                            } else {
                                // el error de sgspe-slides es JSON con su código y la lámina, sin texto del documento
                                val body = runCatching { response.awaitBody<String>() }.getOrNull()
                                val error = body?.let { runCatching { json.readTree(it) }.getOrNull() }
                                val code = error?.get("code")?.asString()
                                // la lámina que no cupo, o la ruta del campo y el motivo de una especificación inválida (SPEC_INVALID):
                                // un id, un nombre de campo y un código de zod, nunca un valor del documento
                                val path = error?.get("path")?.asString()?.take(DETAIL_CHARS)
                                val reason = error?.get("reason")?.asString()?.take(DETAIL_CHARS)
                                val slide =
                                    error
                                        ?.get("slide")
                                        ?.takeIf { !it.isNull }
                                        ?.asString()
                                        ?.take(DETAIL_CHARS)
                                LOG.warn("sgspe-slides respondió {} {} {}{} {} (corrida {})", status, code, slide ?: "", path ?: "", reason ?: "", correlation)
                                if ((status == BAD_REQUEST || status == UNPROCESSABLE) && code != null) failure = SlideFailed(code, slide)
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
                LOG.warn("sgspe-slides no devolvió el PDF: {}", e.javaClass.simpleName)
                null
            }
        failure?.let { throw it }
        if (pdf == null || pdf.isEmpty()) throw SlidesUnavailable("sgspe-slides no devolvió el PDF")
        if (pdf.size > properties.maxFileBytes) throw PdfTooLarge()
        return RenderedDeck(pdf, meta.first, meta.second, meta.third)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SlidesConverter::class.java)
        const val ROUTE = "/render"
        const val TRACE = "X-Trace"
        const val COUNT = "X-Slides-Count"
        const val RENDERER = "X-Slides-Renderer"
        const val DESIGN = "X-Slides-Design"
        private const val OVERHEAD = 64 * 1024L
        private const val DETAIL_CHARS = 120
        private const val BAD_REQUEST = 400
        private const val UNPROCESSABLE = 422
    }
}
