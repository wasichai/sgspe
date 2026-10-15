package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.territory.MapExtent
import sgspe.territory.MapFeature
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

// especificación de F8 §2.2.11, §3.7 pasos 2 y 3 y §5 («Mapas», GetMapRequestTest): el SLD en línea con srsName de URL, las
// capas publicadas por nombre con su estilo de la paleta escalado a 300 dpi, el recuadro y el tamaño, y nada sensible en EXTERNO
class GetMapRequestTest {
    private val frame = MapFraming.frame(MapExtent(780_000.0, 9_240_000.0, 783_000.0, 9_242_000.0), 16.0, 12.0)
    private val project = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val polygon =
        """<gml:Polygon srsName="http://www.opengis.net/gml/srs/epsg.xml#32717"><gml:outerBoundaryIs><gml:LinearRing><gml:coordinates>""" +
            "780100,9240100 780900,9240100 780900,9240900 780100,9240100</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon>"

    private fun xml(): String =
        GetMapRequest.xml(
            frame,
            32717,
            listOf(
                GetMapLayer.Named(
                    "sgspe_locality__location",
                    "Capital provincial",
                    ReportLayerStyles.PUBLISHED.getValue("sgspe-lyr-03"),
                    "name",
                    "locality_type" to "CIUDAD"
                ),
                GetMapLayer.Inline(
                    "LYR-06",
                    "Sectores comunales",
                    ReportLayerStyles.INLINE.getValue("LYR-06"),
                    listOf(MapFeature(polygon, "Sector <Alto> & Bajo"), MapFeature(polygon, null))
                )
            )
        )

    @Test
    fun `el GetMap es XML valido con el recuadro, el tamano a 300 dpi y el srsName de URL`() {
        val xml = xml()
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue("""<BoundingBox srsName="http://www.opengis.net/gml/srs/epsg.xml#32717">""" in xml, xml)
        assertTrue("<gml:X>${"%.2f".format(java.util.Locale.ROOT, frame.bbox.minX)}</gml:X>" in xml, xml)
        assertTrue("<Width>1890</Width>" in xml && "<Height>1417</Height>" in xml, xml)
        assertTrue("<Format>image/png</Format>" in xml && "<BGcolor>#FFFFFF</BGcolor>" in xml)
    }

    @Test
    fun `la capa publicada va por nombre con su filtro y la capa en linea con sus elementos y etiquetas escapadas`() {
        val xml = xml()
        assertTrue("<NamedLayer><Name>sgspe_locality__location</Name>" in xml, xml)
        assertTrue("<PropertyName>locality_type</PropertyName><Literal>CIUDAD</Literal>" in xml, xml)
        assertTrue("<UserLayer><Name>LYR-06</Name><InlineFeature>" in xml, xml)
        assertEquals(2, Regex("<gml:_Feature>").findAll(xml).count())
        assertTrue("<label>Sector &lt;Alto&gt; &amp; Bajo</label>" in xml, xml)
        assertTrue("<label></label>" in xml, "la etiqueta vacía mantiene el esquema")
        // 1.0 de la paleta a 300 dpi
        assertTrue("""<CssParameter name="stroke-width">${GetMapRequest.number(1.0 * GetMapRequest.SCALE)}</CssParameter>""" in xml, xml)
    }

    @Test
    fun `EXTERNO deja fuera sondajes, plataformas y predios de la empresa, y las concesiones del proyecto van en linea`() {
        val request =
            MapRequest(
                "anexo-1",
                project,
                "EPSG 32717",
                null,
                listOf("LYR-10", "LYR-02", "LYR-03", "LYR-04", "LYR-09", "LYR-07", "LYR-05", "LYR-14", "LYR-15", "LYR-01"),
                MapFrameKind.INTEREST_AND_CONCESSIONS,
                concessions = listOf(UUID.fromString("00000000-0000-0000-0000-000000000009")),
                landscape = true
            )
        val all = ReportLayers.forRequest(request)
        assertTrue(all.any { it.code == "LYR-10" } && all.any { it.code == "LYR-11" || it.code == "LYR-15" })
        assertTrue(all.contains(ReportLayers.PROJECT_CONCESSIONS))
        val external = ReportLayers.external(all).map { it.code }
        for (code in listOf("LYR-10", "LYR-11", "LYR-15")) assertFalse(code in external, code)
        assertTrue("LYR-14" in external && "LYR-07" in external)
        val context = MapContext(project, null, emptyList(), request.concessions)
        val parcels = (ReportLayers.of("LYR-14").single() as ReportLayer.Inline).query(context)!!
        assertEquals(null, parcels.label, "un predio nunca lleva atributos")
        assertEquals(setOf("PROPIEDAD", "POSESION", "COMUNAL"), parcels.equals.getValue("tenure_type"))
    }

    @Test
    fun `cada encuadre nombra sus geometrias`() {
        val community = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val cover = ReportLayers.frameRefs(MapRequest("portada", project, "EPSG 32717", community, emptyList(), MapFrameKind.COMMUNITY_AND_PROJECT))
        assertEquals(listOf("community.territory", "project.location"), cover.map { "${it.objectName}.${it.geometry}" })
        val areas = ReportLayers.frameRefs(MapRequest("areas", project, "EPSG 32717", null, emptyList(), MapFrameKind.AREAS, areas = listOf(community)))
        assertEquals(listOf("area_of_influence.boundary", "project.location"), areas.map { "${it.objectName}.${it.geometry}" })
    }
}
