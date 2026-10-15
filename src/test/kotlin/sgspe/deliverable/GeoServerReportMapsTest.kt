package sgspe.deliverable

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import sgspe.privacy.CanonicalJson
import sgspe.territory.GeometryRef
import sgspe.territory.MapExtent
import sgspe.territory.MapFeature
import sgspe.territory.MapLayerQuery
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.imageio.ImageIO

// especificación de F8 §3.7 y §5 («Mapas»; plan 8.2c, Tarea 8; Review Focus 1 y 3): los mapas reales contra un GeoServer
// falso (el servidor HTTP del JDK) y unas geometrías falsas. GeoServer no está en la CI (decisión 23)
class GeoServerReportMapsTest {
    private val project = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val community = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val point = """<gml:Point srsName="http://www.opengis.net/gml/srs/epsg.xml#32717"><gml:coordinates>781000,9241000</gml:coordinates></gml:Point>"""

    private class FakeData(
        val drills: Int = 1,
        val unpublished: Set<String> = emptySet()
    ) : MapData {
        val queries = CopyOnWriteArrayList<MapLayerQuery>()

        override suspend fun extent(
            organizationId: UUID,
            srid: Int,
            refs: List<GeometryRef>
        ): MapExtent = MapExtent(780_000.0, 9_240_000.0, 783_000.0, 9_242_000.0)

        override suspend fun features(
            organizationId: UUID,
            srid: Int,
            query: MapLayerQuery,
            bbox: MapExtent,
            tolerance: Double,
            max: Int
        ): List<MapFeature> {
            queries += query
            return if (query.objectName == "project_component" && query.geometry == "location" && "SONDAJE" in query.equals["component_type"].orEmpty()) {
                List(drills) {
                    MapFeature(
                        """<gml:Point srsName="http://www.opengis.net/gml/srs/epsg.xml#32717"><gml:coordinates>781000,9241000</gml:coordinates></gml:Point>""",
                        "DDH-0$it"
                    )
                }
            } else {
                emptyList()
            }
        }

        override suspend fun published(
            organizationId: UUID,
            objectName: String,
            geometry: String,
            fields: List<String>
        ): PublishedName? = PublishedName("sgspe_${objectName}__$geometry", fields.associateWith { it }).takeUnless { objectName in unpublished }
    }

    private val bodies = CopyOnWriteArrayList<String>()

    private fun server(respond: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext("/") { exchange ->
                exchange.use {
                    bodies += it.requestBody.readAllBytes().toString(Charsets.UTF_8)
                    respond(it)
                }
            }
            start()
        }

    private fun reply(
        exchange: HttpExchange,
        type: String,
        bytes: ByteArray,
        status: Int = 200
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    // del tamaño del marco horizontal de 24 × 16 cm a 300 dpi, el del pedido de abajo
    private fun png(
        width: Int = MapFraming.px(24.0),
        height: Int = MapFraming.px(16.0)
    ): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    private val log = LoggerFactory.getLogger(GeoServerReportMaps::class.java) as Logger
    private val lines = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun capture() {
        lines.start()
        log.addAppender(lines)
    }

    @AfterEach
    fun release() {
        log.detachAppender(lines)
    }

    // las líneas WARN: solo la clave y el código, sin excepción adjunta
    private fun warnings(): List<String> =
        lines.list.filter { it.level == Level.WARN }.map {
            assertNull(it.throwableProxy, it.formattedMessage)
            it.formattedMessage
        }

    private fun maps(
        data: MapData,
        url: String?,
        maxFeatures: Int = 5_000,
        timeout: Duration = Duration.ofSeconds(5)
    ) = GeoServerReportMaps(data, url, DeliverableProperties(maps = DeliverableProperties.Maps(maxFeatures, timeout)))

    private fun url(server: HttpServer) = "http://127.0.0.1:${server.address.port}/geoserver/sgspe/wms"

    private val request =
        MapRequest(
            "anexo-1",
            project,
            "EPSG 32717",
            community,
            listOf("LYR-10", "LYR-05", "LYR-06"),
            MapFrameKind.INTEREST_AND_CONCESSIONS,
            landscape = true,
            author = "Asociación Ejemplo"
        )

    @Test
    fun `el GetMap EXTERNO no lleva las capas confidenciales y su imagen avisa que las omitio`() {
        val server = server { reply(it, "image/png", png()) }
        val images =
            try {
                runBlocking { maps(FakeData(), url(server)).draw(UUID.randomUUID(), request) }
            } finally {
                server.stop(0)
            }
        assertFalse(images.failed)
        val (internal, external) = bodies
        assertTrue("<UserLayer><Name>LYR-10</Name>" in internal, internal)
        assertFalse("LYR-10" in external, external)
        assertTrue("<NamedLayer><Name>sgspe_community__territory</Name>" in external, external)
        assertFalse(images.internal!!.omittedLayers)
        assertTrue(images.external!!.omittedLayers)
        val bytes = Base64.getDecoder().decode(images.internal.pngBase64)
        assertEquals(CanonicalJson.sha256(bytes), images.internal.sha256)
        assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `un ServiceException, un plazo vencido, mas elementos que el tope, sin GeoServer o sin CRS de trabajo dejan el mapa fallido`() {
        val xml = server { reply(it, "application/vnd.ogc.se_xml", "<ServiceExceptionReport/>".toByteArray()) }
        val slow =
            server {
                Thread.sleep(2_000)
                reply(it, "image/png", png())
            }
        val ok = server { reply(it, "image/png", png()) }
        try {
            assertTrue(runBlocking { maps(FakeData(), url(xml)).draw(UUID.randomUUID(), request) }.failed)
            assertTrue(runBlocking { maps(FakeData(), url(slow), timeout = Duration.ofMillis(300)).draw(UUID.randomUUID(), request) }.failed)
            val before = bodies.size
            assertTrue(runBlocking { maps(FakeData(drills = 3), url(ok), maxFeatures = 2).draw(UUID.randomUUID(), request) }.failed)
            assertEquals(before, bodies.size, "con el tope pasado no se pide el GetMap")
            assertTrue(runBlocking { maps(FakeData(), null).draw(UUID.randomUUID(), request) }.failed)
            assertTrue(runBlocking { maps(FakeData(), url(ok)).draw(UUID.randomUUID(), request.copy(workingCrs = "EPSG 4326")) }.failed)
        } finally {
            listOf(xml, slow, ok).forEach { it.stop(0) }
        }
    }

    @Test
    fun `los sectores se piden para la comunidad del mapa`() {
        val data = FakeData()
        val server = server { reply(it, "image/png", png()) }
        try {
            runBlocking { maps(data, url(server)).draw(UUID.randomUUID(), request) }
        } finally {
            server.stop(0)
        }
        val sectors = data.queries.first { it.objectName == "community_sector" }
        assertEquals(setOf(community.toString()), sectors.equals["community"])
        // una capa en línea sin elementos no va al GetMap: un FeatureCollection vacío rompe la inferencia de GeoServer
        val (internal, external) = bodies
        assertFalse("<UserLayer><Name>LYR-06</Name>" in internal, internal)
        assertFalse("<UserLayer><Name>LYR-06</Name>" in external, external)
        assertTrue("<UserLayer><Name>LYR-10</Name>" in internal, internal)
    }

    @Test
    fun `cada causa queda en el log con su codigo y solo la clave del mapa`() {
        val xml = server { reply(it, "application/vnd.ogc.se_xml", "<ServiceExceptionReport/>".toByteArray()) }
        val error = server { reply(it, "text/plain", "error interno".toByteArray(), status = 500) }
        val text = server { reply(it, "text/plain", "no es una imagen".toByteArray()) }
        val small = server { reply(it, "image/png", png(1890, 1417)) }
        val huge = server { reply(it, "image/png", png(MapFraming.px(24.0) + 1, MapFraming.px(16.0))) }
        val slow =
            server {
                Thread.sleep(2_000)
                reply(it, "image/png", png())
            }
        val closed = server { reply(it, "image/png", png()) }.also { it.stop(0) }
        val expected =
            listOf(
                maps(FakeData(), url(xml)) to "SERVICE_EXCEPTION",
                maps(FakeData(), url(error)) to "HTTP_500",
                maps(FakeData(), url(text)) to "NOT_PNG",
                maps(FakeData(), url(small)) to "BAD_IMAGE_SIZE",
                maps(FakeData(), url(huge)) to "BAD_IMAGE_SIZE",
                maps(FakeData(), url(slow), timeout = Duration.ofMillis(300)) to "TIMEOUT",
                maps(FakeData(), url(closed)) to "GEOSERVER_UNAVAILABLE"
            )
        try {
            for ((reportMaps, code) in expected) {
                lines.list.clear()
                assertTrue(runBlocking { reportMaps.draw(UUID.randomUUID(), request) }.failed, code)
                assertEquals(listOf("JOB-05: el mapa anexo-1 falló con $code"), warnings())
            }
        } finally {
            listOf(xml, error, text, small, huge, slow).forEach { it.stop(0) }
        }
    }

    @Test
    fun `una capa publicada que el modelo no resuelve se omite con un rastro sin datos`() {
        val server = server { reply(it, "image/png", png()) }
        val images =
            try {
                runBlocking { maps(FakeData(unpublished = setOf("community")), url(server)).draw(UUID.randomUUID(), request) }
            } finally {
                server.stop(0)
            }
        assertFalse(images.failed)
        assertFalse("sgspe_community__territory" in bodies.first(), bodies.first())
        assertEquals(List(2) { "JOB-05: el mapa anexo-1 omite la capa LYR-05, que el modelo del tenant no resuelve" }, warnings())
    }
}
