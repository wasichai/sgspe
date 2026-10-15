package sgspe.deliverable

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

// sgspe.deliverable.* (especificación de F8 §2.2.8, §2.2.17, §3.3, §4.2): el sondeo de JOB-05 y cuándo un pedido en curso se
// vuelve a tomar, el tope de un archivo que sube el servidor (64 MB, ASM-138), las fotos reducidas, el tope de elementos en
// línea y el plazo de un mapa (los usa 8.2c), las plantillas (20 MB por HTTP, entradas y bytes descomprimidos) y el esquema
// de revisiones (ASM-027) general y por slug de organización. un valor fuera de rango detiene el arranque
@ConfigurationProperties("sgspe.deliverable")
data class DeliverableProperties(
    val generation: Generation = Generation(),
    val maxFileBytes: Long = 64L * MB,
    val photos: Photos = Photos(),
    val maps: Maps = Maps(),
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
        val jpegQuality: Int = 85
    ) {
        init {
            require(maxSide in 200..8000) { "sgspe.deliverable.photos.max-side debe estar entre 200 y 8000" }
            require(jpegQuality in 10..100) { "sgspe.deliverable.photos.jpeg-quality debe estar entre 10 y 100" }
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
