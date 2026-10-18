package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F8ApiTest
import sgspe.SyntheticGeo
import java.util.UUID
import kotlin.random.Random

// especificación de F8 §3.7 paso 2 y decisión 7 del plan 8.2c: las geometrías de los mapas de informe leídas como la plataforma
// por SpatialGate, en el UTM del proyecto, con el srsName que pide GeoServer, el filtro de cada capa y el tope
class ReportMapFeaturesApiTest : F8ApiTest() {
    @Autowired
    private lateinit var features: ReportMapFeatures

    @Test
    fun `la extension del territorio comunal sale en metros UTM y sus elementos en GML con el srsName de GeoServer`() {
        val scenario = reportScenario()
        val org = organizationId()
        val community = UUID.fromString(scenario.community)
        val extent = runBlocking { features.extent(org, 32717, listOf(GeometryRef("community", "territory", listOf(community)))) }!!
        assertEquals(4_000.0, extent.width, 60.0)
        assertEquals(4_000.0, extent.height, 60.0)
        assertTrue(extent.minY > 9_000_000 && extent.minX in 100_000.0..900_000.0, "$extent")
        val rows =
            runBlocking { features.features(org, 32717, MapLayerQuery("community", "territory", ids = listOf(community), label = "name"), extent, 1.0, 10) }
        assertEquals(1, rows.size)
        assertTrue("srsName=\"http://www.opengis.net/gml/srs/epsg.xml#32717\"" in rows.single().gml, rows.single().gml)
        assertEquals("Comunidad Ejemplo", rows.single().label)
        assertEquals(null, runBlocking { features.extent(org, 32717, listOf(GeometryRef("community", "territory", emptyList()))) })
    }

    @Test
    fun `la igualdad deja fuera los predios de la empresa y el tope devuelve uno de mas`() {
        val org = organizationId()
        // lejos del origen de las demás pruebas: la base es compartida
        val (lon, lat) = SyntheticGeo.offset(SyntheticGeo.ORIGIN.first, SyntheticGeo.ORIGIN.second, Random.nextDouble(20_000.0, 40_000.0), -30_000.0)
        val owned = runBlocking { parcel(uniqueName("PRD-"), "PROPIEDAD", SyntheticGeo.square(lon, lat, 200.0)) }
        val company = runBlocking { parcel(uniqueName("PRD-"), "PROPIEDAD DE LA EMPRESA", SyntheticGeo.square(lon + 0.003, lat, 200.0)) }
        val ids = listOf(owned, company)
        val bbox = runBlocking { features.extent(org, 32717, listOf(GeometryRef("land_parcel", "boundary", ids))) }!!
        val others =
            runBlocking {
                features.features(
                    org,
                    32717,
                    MapLayerQuery(
                        "land_parcel",
                        "boundary",
                        equals =
                            mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL")),
                        ids = ids
                    ),
                    bbox,
                    1.0,
                    10
                )
            }
        assertEquals(1, others.size)
        assertEquals(null, others.single().label)
        val capped = runBlocking { features.features(org, 32717, MapLayerQuery("land_parcel", "boundary", ids = ids), bbox, 1.0, 1) }
        assertEquals(2, capped.size, "el tope pide max + 1 para saber que se pasó")
    }

    // #83: el filtro project deja fuera los componentes de otro proyecto y currentOnly, las versiones reemplazadas (superseded):
    // un sondaje movido sale una vez, en su versión vigente
    @Test
    fun `project deja fuera otro proyecto y currentOnly las versiones reemplazadas`() {
        val org = organizationId()
        // lejos del origen de las demás pruebas: la base es compartida
        val (lon, lat) = SyntheticGeo.offset(SyntheticGeo.ORIGIN.first, SyntheticGeo.ORIGIN.second, Random.nextDouble(40_000.0, 60_000.0), -60_000.0)
        val (own, other) = runBlocking { utmProject() to utmProject() }
        val code = uniqueName("SND-")
        val first = runBlocking { component(own, code, "SONDAJE", SyntheticGeo.point(lon, lat)) }
        // el mismo código con otras coordenadas es la versión 2: la 1 queda superseded
        val moved = runBlocking { component(own, code, "SONDAJE", SyntheticGeo.point(lon + 0.002, lat)) }
        val elsewhere = runBlocking { component(other, uniqueName("SND-"), "SONDAJE", SyntheticGeo.point(lon + 0.001, lat)) }
        val ids = listOf(first, moved, elsewhere)
        // los tres puntos están en una misma latitud: el encuadre lleva un margen para no ser una línea
        val bbox =
            runBlocking { features.extent(org, 32717, listOf(GeometryRef("project_component", "location", ids))) }!!.let {
                MapExtent(it.minX - 100, it.minY - 100, it.maxX + 100, it.maxY + 100)
            }

        fun labels(
            project: UUID?,
            currentOnly: Boolean
        ): List<String?> =
            runBlocking {
                features
                    .features(
                        org,
                        32717,
                        MapLayerQuery("project_component", "location", ids = ids, project = project, label = "code", currentOnly = currentOnly),
                        bbox,
                        1.0,
                        10
                    ).map { it.label }
            }
        assertEquals(3, labels(null, currentOnly = false).size)
        assertEquals(listOf(code, code), labels(own, currentOnly = false))
        assertEquals(listOf(code), labels(own, currentOnly = true))
        assertEquals(2, labels(null, currentOnly = true).size)
    }

    @Test
    fun `con una persona en el contexto no corre`() {
        val org = organizationId()
        assertThrows<IllegalStateException> {
            asUser(admin()) { features.extent(org, 32717, listOf(GeometryRef("community", "territory", listOf(UUID.randomUUID())))) }
        }
    }
}
