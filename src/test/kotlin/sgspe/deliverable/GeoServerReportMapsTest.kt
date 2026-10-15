package sgspe.deliverable

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        val drills: Int = 1
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
        ): PublishedName = PublishedName("sgspe_${objectName}__$geometry", fields.associateWith { it })
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
        bytes: ByteArray
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun png(): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1890, 1417, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

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
}
