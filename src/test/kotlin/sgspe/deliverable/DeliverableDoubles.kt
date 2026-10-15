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
// - FakePdfConverter: un PDF con Liberation Sans incrustada (y la identificación PDF/A-2b en las candidatas) en lugar de
//   Gotenberg (plan 8.2c); `unavailable` simula Gotenberg caído
// - FakeReportMaps: dos PNG (INTERNO y EXTERNO) en lugar de GeoServer; `failing` simula un mapa fallido
// los siete archivos del borrador los sube y adjunta la plataforma (PlatformDraftFiles, UPS-31 resuelta en wasichai 1.3.0)
class DeliverableDoubles {
    class FakePdfConverter : PdfConverter {
        @Volatile var unavailable = false
        val requests: MutableList<PdfRequest> = java.util.Collections.synchronizedList(mutableListOf())

        override suspend fun convert(
            docx: ByteArray,
            request: PdfRequest
        ): ByteArray {
            if (unavailable) throw PdfConverterUnavailable("doble sin conversor")
            requests += request
            return TestPdfs.liberation(request.pdfa, "${request.code} Rev. ${request.revision}")
        }
    }

    class FakeReportMaps : ReportMaps {
        @Volatile var failing = false

        override suspend fun draw(
            organizationId: UUID,
            request: MapRequest
        ): ReportMapImages {
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

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fakePdfConverter(): FakePdfConverter = FakePdfConverter()

        @Bean
        @Primary
        fun fakeReportMaps(): FakeReportMaps = FakeReportMaps()
    }
}
