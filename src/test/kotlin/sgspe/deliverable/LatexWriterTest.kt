package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import sgspe.privacy.ReportFixtures
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// ADR-0030, ASM-139, deliverables.md §7.7: el paquete de LuaLaTeX de un nivel. mismas leyendas que el DOCX, todo texto
// escapado, la portada de la clase, las secciones horizontales, el mismo paquete para el borrador y la candidata
class LatexWriterTest {
    private val writer = LatexWriter(DeliverableProperties.Photos())

    private fun meta(title: String = "INFORME DE CAMPO Y AVANCE") =
        ReportMeta(
            "INFORME DE CAMPO Y AVANCE",
            1,
            "ENT-002",
            title,
            "A",
            "0",
            "Proyecto Ejemplo A",
            dateLabel = "Setiembre, 2026",
            templateSha256 = "0".repeat(64),
            level = "INTERNO"
        )

    private fun write(vararg blocks: Block): String =
        writer.write(LatexWriter.Input(ReportModel(meta(), listOf(ReportSection("s", "Conclusiones", "NARRATIVA", blocks.toList()))), null, emptyMap())).main

    private fun png(): ByteArray =
        ByteArrayOutputStream()
            .also { ImageIO.write(BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()

    @Test
    fun `las leyendas son las mismas del DOCX, en el mismo orden, con su numero literal`() {
        val model = ReportDisclosure.apply(T1Fixtures.model(), Disclosure.INTERNO)
        val main = writer.write(LatexWriter.Input(model, "Asociación Ejemplo", emptyMap())).main
        val expected = ReportCaptions.of(model).ordered
        val found =
            Regex("""\\sgspenumero\{(table|figure)\}\{([^}]*)\}(?:\\captionof\{figure\}\{([^}]*)\}|[\s\S]*?\\caption\{([^}]*)\})""")
                .findAll(main)
                .map { m -> (if (m.groupValues[1] == "table") "Tabla" else "Figura") + " " + m.groupValues[2] }
                .toList()
        assertEquals(expected.map { it.first.label }, found)
        assertTrue("\\documentclass[capitulos]{sgspe-informe}" in main, "T1 lleva índice: capítulos")
        assertTrue("\\tableofcontents" in main && "\\listoftables" in main && "\\listoffigures" in main)
    }

    @Test
    fun `ningun texto del modelo entra sin escapar`() {
        val hostile = "\\input{/etc/passwd} \\directlua{os.exit(1)} ^^5c 100% & #1 _x ~ \$5 [opc] *"
        val main =
            write(
                Block.Heading(2, hostile),
                Block.Paragraph("**$hostile**\n\n- $hostile\n\n| A | B |\n|---|---|\n| $hostile | 2 |", footnotes = listOf(hostile)),
                Block.Table(hostile, listOf(hostile, "B"), listOf(listOf(Cell(hostile), Cell("1")))),
                Block.LineChart(hostile, listOf(hostile), listOf(sgspe.privacy.ChartSeries(hostile, listOf("1}\\x"))))
            )
        assertFalse("\\input{/etc" in main, main)
        assertFalse("\\directlua{" in main)
        assertFalse("^^" in main)
        assertFalse(Regex("""(?<!\\)%""").containsMatchIn(main.substringAfter("\\begin{document}")), "un % sin escapar")
        assertTrue("\\textbackslash{}input\\{/etc/passwd\\}" in main)
        assertTrue("(0,nan)" in main, "un valor que no es decimal es un hueco")
    }

    @Test
    fun `los titulos llevan su numero literal y sin numero van sin numeracion`() {
        val main =
            writer
                .write(
                    LatexWriter.Input(
                        ReportModel(
                            meta(),
                            listOf(
                                ReportSection(
                                    "a",
                                    "4. Áreas de influencia",
                                    "MIXTA",
                                    listOf(Block.Heading(2, "4.1 Área directa"), Block.Heading(3, "Caserío Ejemplo"))
                                ),
                                ReportSection("b", "Conclusiones", "NARRATIVA", emptyList())
                            )
                        ),
                        null,
                        emptyMap()
                    )
                ).main
        assertTrue("\\sgspenumero{section}{4.}\\section{Áreas de influencia}" in main, main)
        assertTrue("\\sgspenumero{subsection}{4.1}\\subsection{Área directa}" in main)
        assertTrue("\\subsubsection*{Caserío Ejemplo}\\addcontentsline{toc}{subsubsection}{Caserío Ejemplo}" in main)
        assertTrue("\\addsec{Conclusiones}" in main)
    }

    @Test
    fun `una tabla con celdas combinadas, sombra, cifras a la derecha y fuente al pie de su ultima pagina`() {
        val main =
            write(
                Block.Table(
                    "Miembros",
                    listOf("Grupo", "Detalle", "Familias"),
                    listOf(
                        listOf(Cell("Grupo A", rowSpan = 2), Cell("uno"), Cell("10", shade = "f8cbad")),
                        listOf(Cell("dos"), Cell("20")),
                        listOf(Cell("todo junto", colSpan = 3))
                    ),
                    source = "Fuente: Censo 2017"
                )
            )
        assertTrue("\\begin{longtable}" in main && "\\endhead" in main && "\\endlastfoot" in main)
        assertTrue("\\multirow[t]{2}{=}{Grupo A}" in main)
        assertTrue("\\multicolumn{3}{@{}>{\\RaggedRight\\arraybackslash}p{" in main)
        assertTrue("\\cellcolor[HTML]{F8CBAD}10" in main)
        assertTrue(">{\\RaggedLeft\\arraybackslash}p{" in main, "las cifras a la derecha")
        assertTrue("\\sgspefuenteentabla{Fuente: Censo 2017}" in main)
        // la línea fina no corta la celda que baja: entre la fila 1 y la 2 solo cubre las columnas 2 y 3
        assertTrue("\\sgspefina\\cmidrule{2-3}\\sgspefuerte" in main, main)
    }

    @Test
    fun `una tabla ancha y un salto de seccion abren y cierran su pagina horizontal`() {
        val main = write(Block.Table("Ancha", listOf("A"), listOf(listOf(Cell("x"))), wide = true), Block.SectionBreak(true), Block.Paragraph("horizontal"))
        assertEquals(2, Regex("""\\begin\{landscape\}""").findAll(main).count())
        assertEquals(2, Regex("""\\end\{landscape\}""").findAll(main).count())
        assertTrue(main.indexOf("horizontal") < main.lastIndexOf("\\end{landscape}"))
    }

    @Test
    fun `la portada de la clase lleva el mapa de la seccion portada con su leyenda y no repite su parrafo`() {
        val model =
            ReportModel(
                meta().copy(preparedFor = "Empresa Ejemplo", coverMap = "portada"),
                listOf(
                    ReportSection(
                        "portada",
                        "Portada",
                        "CALCULADA",
                        listOf(Block.Paragraph("Título repetido"), Block.MapFigure("portada", "Mapa de ubicación"))
                    ),
                    ReportSection("s", "Conclusiones", "NARRATIVA", listOf(Block.Paragraph("Cuerpo")))
                ),
                maps =
                    mapOf(
                        "portada" to
                            sgspe.privacy.ReportMapImages(
                                sgspe.privacy.ReportImage(
                                    java.util.Base64
                                        .getEncoder()
                                        .encodeToString(png()),
                                    "1".repeat(64)
                                ),
                                null
                            )
                    )
            )
        val bundle = writer.write(LatexWriter.Input(model, null, emptyMap(), logo = png(), accent = "2e7d32"))
        val main = bundle.main
        assertFalse("Título repetido" in main)
        assertTrue("\\sgspeportada{INFORME DE CAMPO Y AVANCE}{Proyecto Ejemplo A}{Empresa Ejemplo}" in main)
        assertTrue("\\sgspenumero{figure}{1}\\captionof{figure}{Mapa de ubicación}" in main)
        assertTrue("{img/logo.png}" in main)
        assertTrue("\\definecolor{acento}{HTML}{2E7D32}" in main)
        assertTrue("img/mapa-portada.png" in bundle.files && "img/logo.png" in bundle.files)
    }

    @Test
    fun `el mismo paquete sirve al borrador y a la candidata y el nivel sale fuera de INTERNO`() {
        val external = ReportDisclosure.apply(ReportFixtures.model(), Disclosure.EXTERNO)
        val main = writer.write(LatexWriter.Input(external, null, emptyMap())).main
        assertTrue(main.startsWith("\\DocumentMetadata{pdfstandard=a-2b, lang=es-PE}"))
        assertTrue("\\sgspemeta{ENT-001}{A}{0}{EXTERNO}{Informe de campo y avance}" in main, main)
        val internal = writer.write(LatexWriter.Input(ReportDisclosure.apply(ReportFixtures.model(), Disclosure.INTERNO), null, emptyMap())).main
        assertTrue("\\sgspemeta{ENT-001}{A}{0}{}{Informe de campo y avance}" in internal)
        assertFalse("BORRADOR" in main, "la marca la pone la clase en la pasada del borrador")
    }

    @Test
    fun `las fotos van reducidas, de a dos y con texto alternativo`() {
        val model = ReportDisclosure.apply(ReportFixtures.model(), Disclosure.INTERNO)
        val bundle = writer.write(LatexWriter.Input(model, null, model.photos.keys.associateWith { png() }))
        val photos = bundle.files.keys.filter { it.startsWith("img/foto-") }
        assertEquals(model.photos.size, photos.size)
        assertTrue(photos.all { it.endsWith(".jpg") })
        assertTrue("alt={Entrega}" in bundle.main)
        assertTrue("\\hfill " in bundle.main)
    }

    @Test
    fun `el paquete es determinista y la clase viaja con el`() {
        val model = ReportDisclosure.apply(T1Fixtures.model(), Disclosure.INTERNO)
        val first = writer.write(LatexWriter.Input(model, "Asociación Ejemplo", emptyMap()))
        val second = writer.write(LatexWriter.Input(model, "Asociación Ejemplo", emptyMap()))
        assertEquals(first.files.keys, second.files.keys)
        first.files.forEach { (k, v) -> assertArrayEquals(v, second.files[k], k) }
        assertTrue(String(first.files.getValue(LatexWriter.CLASS)).contains("\\ProvidesClass{sgspe-informe}"))
    }

    @Test
    fun `un anidado muy profundo no rompe el paquete y las listas pasan de cuatro niveles a parrafos`() {
        val deep = (1..6).joinToString("\n") { "  ".repeat(it - 1) + "- nivel $it" }
        val quotes = ">".repeat(5000) + " fondo"
        val main = write(Block.Paragraph(deep), Block.Paragraph(quotes), Block.Paragraph("*".repeat(20000) + "x"))
        assertTrue(Regex("""\\begin\{itemize\}""").findAll(main).count() <= 4)
        assertTrue("nivel 6" in main && "fondo" in main)
        assertEquals(Regex("""\\begin\{quote\}""").findAll(main).count(), Regex("""\\end\{quote\}""").findAll(main).count())
    }

    // un mapa que falló (GeoServer caído) deja su aviso, con el código escapado: un `_` sin escapar rompe la composición
    @Test
    fun `un mapa fallido deja su aviso escapado`() {
        val main = write(Block.MapFigure("anexo-1", "Concesiones"))
        assertTrue("\\sgspeausente{Mapa no disponible: MAP\\_FAILED}" in main, main)
        assertFalse(Regex("""(?<!\\)_""").containsMatchIn(main.substringAfter("\\begin{document}")), "un _ sin escapar")
    }
}
