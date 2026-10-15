package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ReportImage
import sgspe.privacy.ReportMapImages
import sgspe.territory.Crs
import wasichai.gis.GeoServerProperties
import wasichai.gis.GeoServerUrls
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

// los mapas de informe de producción (especificación de F8 §3.7, gis.md §7.3, plan 8.2c): solo JOB-05 los pide, dentro de su
// corrida como la plataforma. encuadra en el CRS de trabajo (MapFraming), resuelve las capas del pedido (ReportLayers: las
// publicadas por su nombre, las demás leídas como la plataforma), pide por nivel un GetMap por POST XML (GetMapRequest) con
// WebClient y el plazo sgspe.deliverable.maps.timeout contra el WMS del workspace de GeoServer, y compone cada cuerpo
// (MapComposer). EXTERNO sin las capas confidenciales, con el aviso. un mapa que falla devuelve ReportMapImages() (el borrador
// sale LISTO con MAP_FAILED): sin GeoServer, sin CRS de trabajo, sin geometrías, con más elementos en línea que el tope
// (sin pedir el GetMap), vencido o con una respuesta que no es un PNG. el log lleva la clave del mapa y la causa, nunca
// geometrías ni el cuerpo
@Component
class GeoServerReportMaps internal constructor(
    private val data: MapData,
    private val wmsUrl: String?,
    private val properties: DeliverableProperties
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

    class MapFailure(
        val code: String
    ) : RuntimeException(code)

    override suspend fun draw(
        organizationId: UUID,
        request: MapRequest
    ): ReportMapImages =
        try {
            images(organizationId, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MapFailure) {
            LOG.warn("JOB-05: el mapa {} falló con {}", request.key, e.code)
            ReportMapImages()
        } catch (e: Exception) {
            LOG.warn("JOB-05: el mapa {} falló ({})", request.key, e.javaClass.simpleName)
            ReportMapImages()
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
        val internalImage = level(organizationId, url, srid, frame, layers, context, request.author, omitted = false)
        val externalImage = level(organizationId, url, srid, frame, external, context, request.author, omitted = external.size < layers.size)
        return ReportMapImages(internalImage, externalImage)
    }

    private suspend fun level(
        organizationId: UUID,
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
                    val name = data.published(organizationId, layer.objectName, layer.geometry, listOfNotNull(layer.label, layer.filter?.first)) ?: continue
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
        val body = fetch(url, GetMapRequest.xml(frame, srid, resolved))
        val legend = resolved.map { LegendEntry(it.legend, it.style) }.distinctBy { it.label }
        val png = MapComposer.compose(body, frame, srid, legend, author, omitted)
        return ReportImage(Base64.getEncoder().encodeToString(png), CanonicalJson.sha256(png), omitted)
    }

    private suspend fun fetch(
        url: String,
        xml: String
    ): BufferedImage {
        val bytes =
            withTimeoutOrNull(properties.maps.timeout.toMillis()) {
                client
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(xml)
                    .awaitExchangeOrNull { response ->
                        val type = response.headers().contentType().orElse(null)
                        if (response.statusCode().is2xxSuccessful && type != null && MediaType.IMAGE_PNG.isCompatibleWith(type)) {
                            response.awaitBody<ByteArray>()
                        } else {
                            response.releaseBody().awaitSingleOrNull()
                            null
                        }
                    }
            } ?: throw MapFailure(GEOSERVER_UNAVAILABLE)
        return ImageIO.read(ByteArrayInputStream(bytes)) ?: throw MapFailure(GEOSERVER_UNAVAILABLE)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GeoServerReportMaps::class.java)
        const val GEOSERVER_UNAVAILABLE = "GEOSERVER_UNAVAILABLE"
        const val NO_WORKING_CRS = "NO_WORKING_CRS"
        const val MAP_EMPTY = "MAP_EMPTY"
        const val MAP_TOO_MANY_FEATURES = "MAP_TOO_MANY_FEATURES"
        private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
    }
}
