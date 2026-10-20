package sgspe.deliverable

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ReportImage
import sgspe.privacy.ReportMapImages
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

// los dobles de los puertos de JOB-05 en las pruebas de integración de F8 (especificación de F8 §2.2.17; decisión 1 del plan
// 8.2b). un solo contexto de Spring para todas las clases que extienden F8ApiTest:
// - FakePdfRenderer: dos PDF de prueba (la candidata con la identificación PDF/A-2b) en lugar de sgspe-latex (ADR-0030);
//   `unavailable` simula sgspe-latex caído y `error`, un Error de la máquina virtual en la composición
// - FakeReportMaps: dos PNG (INTERNO y EXTERNO) en lugar de GeoServer; `failing` simula un mapa fallido
// - FakeSlidesRenderer: un PDF de láminas de prueba en lugar de sgspe-slides (ADR-0031)
// los siete archivos del borrador los sube y adjunta la plataforma (PlatformDraftFiles, UPS-31 resuelta en wasichai 1.3.0)
class DeliverableDoubles {
    class FakePdfRenderer : PdfRenderer {
        @Volatile var unavailable = false

        @Volatile var error: Throwable? = null

        // los paquetes recibidos, uno por nivel, y cuántos pedidos llegaron a la vez como máximo (JOB-05 pide los dos niveles juntos)
        val bundles: MutableList<LatexWriter.Bundle> = java.util.Collections.synchronizedList(mutableListOf())
        private val running =
            java.util.concurrent.atomic
                .AtomicInteger()
        val maxConcurrent =
            java.util.concurrent.atomic
                .AtomicInteger()

        override suspend fun render(
            bundle: LatexWriter.Bundle,
            request: PdfRequest
        ): RenderedPdfs {
            if (unavailable) throw PdfConverterUnavailable("doble sin compositor")
            error?.let { throw it }
            bundles += bundle
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                kotlinx.coroutines.delay(150)
            } finally {
                running.decrementAndGet()
            }
            return RenderedPdfs(TestPdfs.source(pdfa = true), TestPdfs.source(pdfa = false, text = "BORRADOR"), 0)
        }
    }

    class FakeReportMaps : ReportMaps {
        @Volatile var failing = false

        // lo pedido, para comprobar las claves y los encuadres de cada tipo (plan 8.2c)
        val requests: MutableList<MapRequest> = java.util.Collections.synchronizedList(mutableListOf())

        override suspend fun draw(
            organizationId: UUID,
            request: MapRequest
        ): ReportMapImages {
            requests += request
            if (failing) return ReportMapImages()
            return ReportMapImages(image(255), image(128, omitted = true))
        }

        private fun image(
            shade: Int,
            omitted: Boolean = false
        ): ReportImage {
            val png =
                ByteArrayOutputStream()
                    .also { out ->
                        ImageIO.write(BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, shade) }, "png", out)
                    }.toByteArray()
            return ReportImage(Base64.getEncoder().encodeToString(png), CanonicalJson.sha256(png), omitted)
        }
    }

    // el doble de sgspe-slides (ADR-0031): un PDF de prueba con una página 16:9 por lámina y su título (TestDecks), que pasa
    // DeckChecks; `unavailable` simula el servicio caído y `failure`, una lámina que no pasa su verificación
    class FakeSlidesRenderer : SlidesRenderer {
        @Volatile var unavailable = false

        @Volatile var failure: String? = null

        val specs: MutableList<ByteArray> = java.util.Collections.synchronizedList(mutableListOf())

        override suspend fun render(
            spec: ByteArray,
            correlation: String
        ): RenderedDeck {
            if (unavailable) throw SlidesUnavailable("doble sin compositor")
            failure?.let { throw SlideFailed(it, "tesis") }
            specs += spec
            return RenderedDeck(TestDecks.pdf(spec), null, "remotion@prueba", "1")
        }
    }

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fakeSlidesRenderer(): FakeSlidesRenderer = FakeSlidesRenderer()

        @Bean
        @Primary
        fun fakePdfRenderer(): FakePdfRenderer = FakePdfRenderer()

        @Bean
        @Primary
        fun fakeReportMaps(): FakeReportMaps = FakeReportMaps()
    }
}
