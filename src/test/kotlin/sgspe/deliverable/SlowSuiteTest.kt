package sgspe.deliverable

import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull
import sgspe.privacy.Block
import sgspe.privacy.Disclosure
import sgspe.privacy.PhotoRef
import sgspe.privacy.ReportDisclosure
import sgspe.territory.MapExtent
import sgspe.territory.MapFeature
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO

// especificación de F8 §5, fila «Suite lenta» (plan 8.2c, Tarea 16): a pedido, con GeoServer y Gotenberg reales por el túnel
// (SGSPE_DELIVERABLE_GOTENBERG_URL, WASICHAI_GIS_GEOSERVER_URL). sin una variable, su prueba se salta
@Tag("slow")
class SlowSuiteTest {
    private fun env(name: String): String {
        val value = System.getenv(name)?.ifBlank { null }
        assumeTrue(value != null, "sin $name")
        return value!!
    }

    @Test
    fun `GetMap contra GeoServer real devuelve un PNG que se compone con su leyenda`() {
        val wms = env("WASICHAI_GIS_GEOSERVER_URL").trimEnd('/') + "/wms"
        val frame = MapFraming.frame(MapExtent(780_000.0, 9_240_000.0, 783_000.0, 9_242_000.0), 16.0, 12.0)
        val square =
            """<gml:Polygon srsName="${GetMapRequest.srsName(32717)}"><gml:outerBoundaryIs><gml:LinearRing><gml:coordinates>""" +
                "780500,9240500 782500,9240500 782500,9241500 780500,9241500 780500,9240500</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon>"
        val layer =
            GetMapLayer.Inline(
                "LYR-06",
                "Sectores comunales",
                ReportLayerStyles.INLINE.getValue("LYR-06"),
                listOf(MapFeature(square, "Sector Ejemplo"))
            )
        val xml = GetMapRequest.xml(frame, 32717, listOf(layer))
        val bytes =
            runBlocking {
                WebClient
                    .builder()
                    .codecs { it.defaultCodecs().maxInMemorySize(64 * 1024 * 1024) }
                    .build()
                    .post()
                    .uri(wms)
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(xml)
                    .awaitExchangeOrNull { response ->
                        if (response
                                .headers()
                                .contentType()
                                .orElse(null)
                                ?.isCompatibleWith(MediaType.IMAGE_PNG) == true
                        ) {
                            response.awaitBody<ByteArray>()
                        } else {
                            response.releaseBody().awaitSingleOrNull()
                            null
                        }
                    }
            }
        assertNotNull(bytes, "GeoServer no devolvió un PNG")
        val body = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(frame.widthPx to frame.heightPx, body.width to body.height)
        val composed =
            ImageIO.read(
                ByteArrayInputStream(MapComposer.compose(body, frame, 32717, listOf(LegendEntry(layer.legend, layer.style)), "Asociación Ejemplo", false))
            )
        var legend = 0
        for (y in MapComposer.TOP + body.height + 60 until MapComposer.TOP + body.height + 140) {
            for (x in MapComposer.LEFT until MapComposer.LEFT + 120) if (composed.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) legend++
        }
        assertTrue(legend > 50, "la leyenda no tiene píxeles ($legend)")
    }

    @Test
    fun `un T1 de unas 100 paginas con 40 fotos se convierte en menos de 180 s`() {
        val url = env("SGSPE_DELIVERABLE_GOTENBERG_URL")
        val converter = GotenbergConverter(DeliverableProperties(gotenberg = DeliverableProperties.Gotenberg(url, Duration.ofSeconds(180))))
        val base = T1Fixtures.model(chapters = 9, paragraphs = 520)
        val photos =
            (1..40).associate { n ->
                val id = "00000000-0000-0000-0000-%012d".format(1_000 + n)
                id to PhotoRef(id, id, "0".repeat(64), "Foto de la visita $n", PhotoRef.PHOTO, identifiable = false)
            }
        val photoSection =
            sgspe.privacy.ReportSection(
                "fotos",
                "Registro fotográfico",
                "MIXTA",
                listOf(Block.Photos("Visitas de campo", photos.keys.toList())),
                chapter = 10
            )
        val model = ReportDisclosure.apply(base.copy(sections = base.sections + photoSection, photos = photos), Disclosure.INTERNO)
        val image = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
        val started = System.nanoTime()
        val docx =
            DocxWriter(
                DeliverableProperties.Photos()
            ).write(
                DocxWriter.Input(
                    model,
                    BaseTemplate.bytes,
                    false,
                    "Asociación Ejemplo",
                    photos.keys.associateWith {
                        image
                    }
                )
            )
        val pdf = runBlocking { converter.convert(docx, PdfRequest(true, model.meta.title, model.meta.code, "0", "suite-lenta")) }
        val seconds = (System.nanoTime() - started) / 1e9
        val pages = Loader.loadPDF(pdf).use { it.numberOfPages }
        assertTrue(pages >= 90, "$pages páginas")
        assertTrue(seconds < 180, "$seconds s")
        assertEquals(emptyList<String>(), PdfChecks.problems(pdf, pdfa = true))
    }
}
