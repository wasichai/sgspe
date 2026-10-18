package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ReportImage
import sgspe.privacy.ReportMapImages
import sgspe.territory.Crs
import wasichai.gis.GeoServerProperties
import wasichai.gis.GeoServerUrls
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.coroutines.CoroutineContext

// los mapas de informe de producción (especificación de F8 §3.7, gis.md §7.3, plan 8.2c): solo JOB-05 los pide, dentro de su
// corrida como la plataforma. encuadra en el CRS de trabajo (MapFraming), resuelve las capas del pedido (ReportLayers: las
// publicadas por su nombre, las demás leídas como la plataforma), pide por nivel un GetMap por POST XML (GetMapRequest) con
// WebClient y el plazo sgspe.deliverable.maps.timeout contra el WMS del workspace de GeoServer, y compone cada cuerpo
// (MapComposer). EXTERNO sin las capas confidenciales, con el aviso. un mapa que falla devuelve ReportMapImages() (el borrador
// sale LISTO con MAP_FAILED): sin GeoServer, sin CRS de trabajo, sin geometrías, con más elementos en línea que el tope
// (sin pedir el GetMap), vencido, con una ServiceException o con una respuesta que no es un PNG del tamaño del marco. el log
// lleva en WARN la clave del mapa y el código de la causa, nunca geometrías, etiquetas ni el cuerpo
@Component
class GeoServerReportMaps internal constructor(
    private val data: MapData,
    private val wmsUrl: String?,
    private val properties: DeliverableProperties,
    // donde corre el trabajo bloqueante de cada nivel (ImageIO, Java 2D y el PNG); las pruebas lo observan
    private val blocking: CoroutineContext = Dispatchers.IO
) : ReportMaps {
    @Autowired
    constructor(
        data: MapData,
        geoserver: GeoServerProperties,
        properties: DeliverableProperties
    ) : this(data, if (geoserver.enabled) GeoServerUrls.wms(geoserver.baseUrl, geoserver.workspace) else null, properties)

    private val client: WebClient =
        WebClient
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize(MAX_IMAGE_BYTES) }
            .build()

    // la causa de un mapa fallido: solo su código va al log en WARN; la excepción de fondo, solo en DEBUG
    class MapFailure(
        val code: String,
        cause: Throwable? = null
    ) : RuntimeException(code, cause)

    override suspend fun draw(
        organizationId: UUID,
        request: MapRequest
    ): ReportMapImages =
        try {
            images(organizationId, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MapFailure) {
            failed(request.key, e.code, e)
        } catch (e: Exception) {
            failed(request.key, MAP_ERROR, e)
        } catch (e: StackOverflowError) {
            // un Error dentro de un mapa deja el borrador LISTO con MAP_FAILED, no FALLIDO (#83)
            failed(request.key, MAP_ERROR, e)
        } catch (e: OutOfMemoryError) {
            failed(request.key, MAP_ERROR, e)
        }

    // WARN con la clave y el código; la clase y la traza de la excepción solo en DEBUG (nunca geometrías ni el cuerpo)
    private fun failed(
        key: String,
        code: String,
        e: Throwable
    ): ReportMapImages {
        LOG.warn("JOB-05: el mapa {} falló con {}", key, code)
        LOG.debug("JOB-05: el detalle del mapa {} fallido", key, e)
        return ReportMapImages()
    }

    private suspend fun images(
        organizationId: UUID,
        request: MapRequest
    ): ReportMapImages {
        val url = wmsUrl ?: throw MapFailure(GEOSERVER_UNAVAILABLE)
        val srid = Crs.srid(request.workingCrs)?.takeIf { Crs.isWorkingZone(it) } ?: throw MapFailure(NO_WORKING_CRS)
        val extent = data.extent(organizationId, srid, ReportLayers.frameRefs(request)) ?: throw MapFailure(MAP_EMPTY)
        val frame = if (request.landscape) MapFraming.frame(extent, 24.0, 16.0) else MapFraming.frame(extent, 16.0, 12.0)
        val context = MapContext(request.project, request.community, request.areas, request.concessions)
        val layers = ReportLayers.forRequest(request)
        val external = ReportLayers.external(layers)
        val internalImage = level(organizationId, request.key, url, srid, frame, layers, context, request.author, omitted = false)
        val externalImage =
            level(organizationId, request.key, url, srid, frame, external, context, request.author, omitted = external.size < layers.size)
        return ReportMapImages(internalImage, externalImage)
    }

    private suspend fun level(
        organizationId: UUID,
        key: String,
        url: String,
        srid: Int,
        frame: MapFrame,
        layers: List<ReportLayer>,
        context: MapContext,
        author: String?,
        omitted: Boolean
    ): ReportImage {
        val tolerance = MapFraming.tolerance(frame)
        val max = properties.maps.maxFeatures
        var count = 0
        val resolved = mutableListOf<GetMapLayer>()
        for (layer in layers) {
            when (layer) {
                is ReportLayer.Published -> {
                    val name = data.published(organizationId, layer.objectName, layer.geometry, listOfNotNull(layer.label, layer.filter?.first))
                    if (name == null) {
                        // el tenant no tiene el objeto, la geometría o un campo: la capa se omite y queda el rastro, sin datos
                        LOG.warn("JOB-05: el mapa {} omite la capa {}, que el modelo del tenant no resuelve", key, layer.code)
                        continue
                    }
                    resolved +=
                        GetMapLayer.Named(
                            name.layer,
                            layer.legend,
                            layer.style,
                            layer.label?.let { name.columns[it] },
                            layer.filter?.let { (field, value) -> name.columns.getValue(field) to value }
                        )
                }
                is ReportLayer.Inline -> {
                    val query = layer.query(context) ?: continue
                    val found = data.features(organizationId, srid, query, frame.bbox, tolerance, max)
                    count += found.size
                    if (count > max) throw MapFailure(MAP_TOO_MANY_FEATURES)
                    if (found.isNotEmpty()) resolved += GetMapLayer.Inline(layer.code, layer.legend, layer.style, found)
                }
            }
        }
        val bytes = fetch(url, GetMapRequest.xml(frame, srid, resolved))
        val legend = resolved.map { LegendEntry(it.legend, it.style) }.distinctBy { it.label }
        // tras awaitBody la corrida sigue en el hilo de Reactor Netty que completó la respuesta: decodificar, dibujar y codificar
        // el PNG (cientos de ms por nivel) van al contexto bloqueante para no retener el event loop de las peticiones
        return withContext(blocking) {
            val png = MapComposer.compose(decode(bytes, frame), frame, srid, legend, author, omitted)
            ReportImage(Base64.getEncoder().encodeToString(png), CanonicalJson.sha256(png), omitted)
        }
    }

    // el cuerpo del GetMap: un 2xx con image/png dentro del plazo. una ServiceException (XML), otro estado, otro tipo, el plazo
    // vencido o un error de conexión fallan con su propio código
    private suspend fun fetch(
        url: String,
        xml: String
    ): ByteArray =
        try {
            withTimeoutOrNull(properties.maps.timeout.toMillis()) {
                client
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(xml)
                    .awaitExchange { response ->
                        val status = response.statusCode()
                        val type = response.headers().contentType().orElse(null)
                        val failure =
                            when {
                                !status.is2xxSuccessful -> "HTTP_${status.value()}"
                                type != null && SERVICE_EXCEPTION_TYPES.any { it.isCompatibleWith(type) } -> SERVICE_EXCEPTION
                                type == null || !MediaType.IMAGE_PNG.isCompatibleWith(type) -> NOT_PNG
                                else -> null
                            }
                        if (failure != null) {
                            response.releaseBody().awaitSingleOrNull()
                            throw MapFailure(failure)
                        }
                        response.awaitBody<ByteArray>()
                    }
            } ?: throw MapFailure(TIMEOUT)
        } catch (e: WebClientRequestException) {
            throw MapFailure(GEOSERVER_UNAVAILABLE, e)
        }

    // lee el tamaño del PNG antes de decodificarlo: uno que no es del tamaño del marco pedido falla sin reservar su imagen
    private fun decode(
        bytes: ByteArray,
        frame: MapFrame
    ): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: throw MapFailure(NOT_PNG)
        return input.use {
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: throw MapFailure(NOT_PNG)
            try {
                if (!reader.formatName.equals("png", ignoreCase = true)) throw MapFailure(NOT_PNG)
                reader.setInput(input, true, true)
                if (reader.getWidth(0) != frame.widthPx || reader.getHeight(0) != frame.heightPx) throw MapFailure(BAD_IMAGE_SIZE)
                reader.read(0)
            } catch (e: IOException) {
                throw MapFailure(NOT_PNG, e)
            } finally {
                reader.dispose()
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GeoServerReportMaps::class.java)
        const val GEOSERVER_UNAVAILABLE = "GEOSERVER_UNAVAILABLE"
        const val NO_WORKING_CRS = "NO_WORKING_CRS"
        const val MAP_EMPTY = "MAP_EMPTY"
        const val MAP_TOO_MANY_FEATURES = "MAP_TOO_MANY_FEATURES"
        const val SERVICE_EXCEPTION = "SERVICE_EXCEPTION"
        const val TIMEOUT = "TIMEOUT"
        const val NOT_PNG = "NOT_PNG"
        const val BAD_IMAGE_SIZE = "BAD_IMAGE_SIZE"
        const val MAP_ERROR = "MAP_ERROR"
        private val SERVICE_EXCEPTION_TYPES =
            listOf(MediaType.parseMediaType("application/vnd.ogc.se_xml"), MediaType.APPLICATION_XML, MediaType.TEXT_XML)
        private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
    }
}
