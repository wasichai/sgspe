package sgspe.deliverable

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream

// un PDF de láminas de prueba (ADR-0031): una página de 1440 × 810 pt por lámina de la especificación, con su título en Source
// Sans 3 (la fuente de las láminas). lo usan DeckChecksTest y el doble del compositor de las pruebas de integración
object TestDecks {
    fun pdf(
        spec: ByteArray,
        width: Float = 1440f,
        height: Float = 810f,
        pages: Int? = null,
        titles: Boolean = true,
        font: (
            PDDocument
        ) -> PDFont = { doc -> PDType0Font.load(doc, TestDecks::class.java.getResourceAsStream("/sgspe/deliverable/deck/fonts/SourceSans3-Regular.ttf")!!) }
    ): ByteArray {
        val slides =
            JsonMapper
                .builder()
                .build()
                .readTree(spec)
                .get("slides")
        val texts = (0 until slides.size()).map { slides.get(it).get("title").asString() }
        return PDDocument().use { doc ->
            val face = font(doc)
            for (i in 0 until (pages ?: texts.size)) {
                val page = PDPage(PDRectangle(width, height))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(face, 24f)
                    stream.newLineAtOffset(72f, height - 100f)
                    stream.showText(if (titles) texts.getOrElse(i) { "lámina" } else "otra cosa")
                    stream.endText()
                }
            }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }
}

// un MP4 de prueba (deliverables.md §16.2): solo las cajas que lee DeckVideoChecks, `ftyp` y `moov` con su `mvhd` de la versión
// 0, con la duración de `slides` láminas de 8 s (o `seconds`) en una escala de 1000 por segundo
object TestVideos {
    fun mp4(
        slides: Int,
        seconds: Long = slides * 8L,
        brand: String = "isom"
    ): ByteArray {
        fun box(
            type: String,
            body: ByteArray
        ): ByteArray =
            java.nio.ByteBuffer
                .allocate(8 + body.size)
                .putInt(8 + body.size)
                .put(type.toByteArray(Charsets.ISO_8859_1))
                .put(body)
                .array()
        val mvhd =
            java.nio.ByteBuffer
                .allocate(100)
                .putInt(0)
                .putInt(0)
                .putInt(0)
                .putInt(1000)
                .putInt((seconds * 1000).toInt())
                .array()
        return box("ftyp", "${brand}\u0000\u0000\u0002\u0000isomavc1".toByteArray(Charsets.ISO_8859_1)) + box("moov", box("mvhd", mvhd)) +
            box("mdat", ByteArray(16))
    }
}
