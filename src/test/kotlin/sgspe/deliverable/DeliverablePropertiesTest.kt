package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

// los valores por defecto de la especificación de F8 y los rangos que detienen el arranque
class DeliverablePropertiesTest {
    @Test
    fun `los valores por defecto son los de la especificacion`() {
        val properties = DeliverableProperties()
        assertEquals(Duration.ofSeconds(5), properties.generation.poll)
        assertEquals(Duration.ofMinutes(15), properties.generation.staleAfter)
        assertEquals(64L * 1024 * 1024, properties.maxFileBytes)
        assertEquals(2000, properties.photos.maxSide)
        assertEquals(85, properties.photos.jpegQuality)
        assertEquals(250, properties.photos.maxMegapixels)
        assertEquals(5000, properties.maps.maxFeatures)
        assertEquals(20L * 1024 * 1024, properties.templates.maxBytes)
        assertEquals(Duration.ofSeconds(180), properties.slides.timeout)
        assertEquals(Duration.ofSeconds(600), properties.slides.videoTimeout)
    }

    @Test
    fun `el plazo del video es positivo y menor que la retoma de JOB-09`() {
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Slides(videoTimeout = Duration.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Slides(videoTimeout = Duration.ofMinutes(15)) }
        assertEquals(
            Duration.ofMinutes(20),
            DeliverableProperties.Slides(videoTimeout = Duration.ofMinutes(15), staleAfter = Duration.ofMinutes(20)).staleAfter
        )
    }

    @Test
    fun `un valor fuera de rango detiene el arranque`() {
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Generation(poll = Duration.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Photos(jpegQuality = 101) }
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Photos(maxMegapixels = 0) }
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Templates(maxBytes = 65L * 1024 * 1024) }
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties.Scheme("ab", 0) }
        assertThrows(IllegalArgumentException::class.java) { DeliverableProperties(maxFileBytes = 0) }
    }
}
