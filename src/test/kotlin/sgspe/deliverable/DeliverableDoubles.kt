package sgspe.deliverable

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.withContext
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Flux
import sgspe.common.Records
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ReportImage
import sgspe.privacy.ReportMapImages
import wasichai.files.FileService
import wasichai.files.FileUpload
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
// - PersonDraftFiles: sube y adjunta los siete archivos como la persona de `token` (el admin de la prueba), con la marca de
//   Records, porque wasichai 1.2.0 no deja a la plataforma adjuntar (UPS-31). la Tarea 15 lo quita
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

    class PersonDraftFiles(
        private val records: Records,
        private val files: FileService,
        private val decoder: ReactiveJwtDecoder
    ) : DraftFiles {
        @Volatile var token: String? = null

        override suspend fun attach(
            organizationId: UUID,
            deliverableId: UUID,
            files: List<DraftFile>,
            fields: Map<String, Any?>
        ) {
            val bearer = checkNotNull(token) { "PersonDraftFiles sin token: F8ApiTest.generate() lo fija" }
            val jwt = decoder.decode(bearer.removePrefix("Bearer ")).awaitSingle()
            withContext(ReactiveSecurityContextHolder.withAuthentication(JwtAuthenticationToken(jwt)).asCoroutineContext()) {
                val ids =
                    files.associate { file ->
                        val upload = FileUpload(file.fileName, file.contentType, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(file.bytes)))
                        file.field to
                            this@PersonDraftFiles
                                .files
                                .stage(Deliverables.DELIVERABLE, file.field, upload)
                                .id
                                .toString()
                    }
                records.patch(Deliverables.DELIVERABLE, deliverableId, fields + ids, Deliverables.JOB_REASON)
            }
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

        @Bean
        @Primary
        fun personDraftFiles(
            records: Records,
            files: FileService,
            decoder: ReactiveJwtDecoder
        ): PersonDraftFiles = PersonDraftFiles(records, files, decoder)
    }
}
