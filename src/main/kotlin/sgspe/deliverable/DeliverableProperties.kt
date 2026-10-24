package sgspe.deliverable

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

// sgspe.deliverable.* (especificación de F8 §2.2.8, §2.2.17, §3.3, §4.2): el sondeo de JOB-05 y cuándo un pedido en curso se
// vuelve a tomar, el tope de un archivo que sube el servidor (64 MB, ASM-138), las fotos reducidas, el tope de elementos en
// línea y el plazo de un mapa, sgspe-latex (ADR-0030), las plantillas (20 MB por HTTP, entradas y bytes descomprimidos) y el esquema
// de revisiones (ASM-027) general y por slug de organización. un valor fuera de rango detiene el arranque
@ConfigurationProperties("sgspe.deliverable")
data class DeliverableProperties(
    val generation: Generation = Generation(),
    val maxFileBytes: Long = 64L * MB,
    val photos: Photos = Photos(),
    val maps: Maps = Maps(),
    val latex: Latex = Latex(),
    val slides: Slides = Slides(),
    val templates: Templates = Templates(),
    val revisions: Revisions = Revisions()
) {
    init {
        require(maxFileBytes in MB..256L * MB) { "sgspe.deliverable.max-file-bytes debe estar entre 1 MB y 256 MB" }
    }

    data class Generation(
        val poll: Duration = Duration.ofSeconds(5),
        val staleAfter: Duration = Duration.ofMinutes(15)
    ) {
        init {
            require(!poll.isNegative && !poll.isZero) { "sgspe.deliverable.generation.poll debe ser positivo" }
            require(staleAfter >= Duration.ofMinutes(1)) { "sgspe.deliverable.generation.stale-after debe ser de al menos 1 minuto" }
        }
    }

    data class Photos(
        val maxSide: Int = 2000,
        val jpegQuality: Int = 85,
        val maxMegapixels: Int = 250
    ) {
        init {
            require(maxSide in 200..8000) { "sgspe.deliverable.photos.max-side debe estar entre 200 y 8000" }
            require(jpegQuality in 10..100) { "sgspe.deliverable.photos.jpeg-quality debe estar entre 10 y 100" }
            require(maxMegapixels in 1..1000) { "sgspe.deliverable.photos.max-megapixels debe estar entre 1 y 1000" }
        }
    }

    data class Maps(
        val maxFeatures: Int = 5000,
        val timeout: Duration = Duration.ofSeconds(30)
    ) {
        init {
            require(maxFeatures >= 1) { "sgspe.deliverable.maps.max-features debe ser al menos 1" }
            require(!timeout.isNegative && !timeout.isZero) { "sgspe.deliverable.maps.timeout debe ser positivo" }
        }
    }

    // el compositor de PDF (ADR-0030, deliverables.md §7.7): la URL de sgspe-latex (127.0.0.1:8097 por el túnel en desarrollo;
    // vacía en producción hasta que el despliegue la fije) y el plazo de un pedido (la candidata y el borrador de un nivel).
    // sin URL, cada borrador queda FALLIDO con PDF_CONVERTER_UNAVAILABLE y el resto de SGSPE funciona
    data class Latex(
        val url: String? = null,
        val timeout: Duration = Duration.ofSeconds(180)
    ) {
        init {
            require(!timeout.isNegative && !timeout.isZero) { "sgspe.deliverable.latex.timeout debe ser positivo" }
            require(url.isNullOrBlank() || url.trim().startsWith("http://") || url.trim().startsWith("https://")) {
                "sgspe.deliverable.latex.url debe ser una URL http o https"
            }
        }

        val configured: Boolean get() = !url.isNullOrBlank()
    }

    // el compositor de las presentaciones (ADR-0031, deliverables.md §16.7): la URL de sgspe-slides (127.0.0.1:8099 por el
    // túnel en desarrollo; vacía en producción hasta que el despliegue la fije), el plazo de una presentación, el sondeo de
    // JOB-09 con su retoma y el plazo de un MP4 (ASM-148), menor que la retoma para que otra vuelta no tome un video en curso.
    // sin URL, cada presentación queda FALLIDO con SLIDES_UNAVAILABLE y el resto de SGSPE funciona
    data class Slides(
        val url: String? = null,
        val timeout: Duration = Duration.ofSeconds(180),
        val poll: Duration = Duration.ofSeconds(5),
        val staleAfter: Duration = Duration.ofMinutes(15),
        val videoTimeout: Duration = Duration.ofSeconds(600)
    ) {
        init {
            require(!timeout.isNegative && !timeout.isZero) { "sgspe.deliverable.slides.timeout debe ser positivo" }
            require(!videoTimeout.isNegative && !videoTimeout.isZero) { "sgspe.deliverable.slides.video-timeout debe ser positivo" }
            require(videoTimeout < staleAfter) { "sgspe.deliverable.slides.video-timeout debe ser menor que stale-after" }
            require(!poll.isNegative && !poll.isZero) { "sgspe.deliverable.slides.poll debe ser positivo" }
            require(staleAfter >= Duration.ofMinutes(1)) { "sgspe.deliverable.slides.stale-after debe ser de al menos 1 minuto" }
            require(url.isNullOrBlank() || url.trim().startsWith("http://") || url.trim().startsWith("https://")) {
                "sgspe.deliverable.slides.url debe ser una URL http o https"
            }
        }
    }

    data class Templates(
        val maxBytes: Long = 20L * MB,
        val maxEntries: Int = 1000,
        val maxInflatedBytes: Long = 100L * MB
    ) {
        init {
            require(maxBytes in 1..64L * MB) { "sgspe.deliverable.templates.max-bytes debe estar entre 1 byte y 64 MB" }
            require(maxEntries >= 10) { "sgspe.deliverable.templates.max-entries debe ser al menos 10" }
            require(maxInflatedBytes >= maxBytes) { "sgspe.deliverable.templates.max-inflated-bytes no puede ser menor que max-bytes" }
        }
    }

    // la letra del primer borrador de cada revisión y el número de la primera emisión, generales y por slug
    data class Revisions(
        val draftStart: String = "A",
        val issueStart: Int = 0,
        val byOrganization: Map<String, Scheme> = emptyMap()
    ) {
        init {
            Scheme(draftStart, issueStart)
        }

        fun of(slug: String?): Scheme = slug?.let { byOrganization[it] } ?: Scheme(draftStart, issueStart)
    }

    data class Scheme(
        val draftStart: String = "A",
        val issueStart: Int = 0
    ) {
        init {
            require(draftStart.length == 1 && draftStart[0] in 'A'..'Z') { "El primer borrador es una letra de la A a la Z" }
            require(issueStart >= 0) { "La primera emisión no puede ser negativa" }
        }
    }

    companion object {
        const val MB = 1024L * 1024
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeliverableProperties::class)
class DeliverableConfiguration
