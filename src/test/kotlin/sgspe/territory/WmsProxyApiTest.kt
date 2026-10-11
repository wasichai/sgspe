package sgspe.territory

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import sgspe.DomainApiTest

// gis.md §9 «Proxy WMS» y especificación de F6 §5 (RQ-SEC-011): con un GeoServer de imitación. los nombres de las capas del
// tenant salen de /api/gis/layers (la metadata de la organización); el de otro tenant es ficticio
class WmsProxyApiTest : DomainApiTest() {
    companion object {
        private val geoserver = GeoServerImitation.start()

        @JvmStatic
        @DynamicPropertySource
        fun geoserverProperties(registry: DynamicPropertyRegistry) {
            registry.add("wasichai.gis.geoserver.enabled") { "true" }
            registry.add("wasichai.gis.geoserver.url") { geoserver.url }
            registry.add("wasichai.gis.geoserver.workspace") { "sgspe" }
            registry.add("sgspe.territory.wms.timeout") { "1s" }
            registry.add("sgspe.territory.wms.published-ttl") { "0s" }
        }

        @JvmStatic
        @AfterAll
        fun stopGeoServer() = geoserver.close()

        private const val OTHER_TENANT = "t_otro_tenant_unidad__limite"
    }

    private data class Wms(
        val status: HttpStatus,
        val body: ByteArray,
        val headers: HttpHeaders
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    @BeforeEach
    fun resetGeoServer() = geoserver.reset()

    private fun wms(
        query: String,
        token: String? = this.token
    ): Wms {
        val result =
            client
                .get()
                .uri("/api/sgspe/territory/wms?$query")
                .headers { h -> if (token != null) h.set(HttpHeaders.AUTHORIZATION, token) }
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return Wms(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun code(response: Wms): String = tree(response.text)["errors"][0]["message"].asString()

    private fun layerName(
        objectName: String,
        geometry: String
    ): String =
        tree(send("GET", "/api/gis/layers", null, HttpStatus.OK))
            .values()
            .first { it["objectName"].asString() == objectName && it["geometryName"].asString() == geometry }["layerName"]
            .asString()

    private fun getMap(
        layer: String,
        extra: String = ""
    ) = "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=$layer&STYLES=&CRS=EPSG:3857" +
        "&BBOX=-8740000,-890000,-8730000,-880000&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE$extra"

    private fun styleOf(objectName: String) = WmsLayersCatalog.ALL.first { it.objectName == objectName }.style

    @Test
    fun `sin token es 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, wms(getMap("t_x__y"), token = null).status)
    }

    @Test
    fun `una capa publica publicada pasa como imagen, sin cabeceras de GeoServer y con su estilo`() {
        val units = layerName("administrative_unit", "boundary")
        geoserver.published += units
        val response = wms(getMap(units))
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals(MediaType.IMAGE_PNG, response.headers.contentType)
        assertEquals("private, max-age=300", response.headers.cacheControl)
        assertNull(response.headers.getFirst(GeoServerImitation.SECRET_HEADER))
        assertArrayEquals(GeoServerImitation.PNG, response.body)
        val forwarded = geoserver.wmsQueries.single()
        assertTrue(forwarded.contains("LAYERS=$units"), forwarded)
        assertTrue(forwarded.contains("STYLES=${styleOf("administrative_unit")}"), forwarded)
    }

    @Test
    fun `una capa de otro tenant o no publicada es 403`() {
        geoserver.published += OTHER_TENANT
        assertEquals(HttpStatus.FORBIDDEN, wms(getMap(OTHER_TENANT)).status)
        // en layers.json pero no publicada en GeoServer
        val localities = layerName("locality", "location")
        assertEquals(HttpStatus.FORBIDDEN, wms(getMap(localities)).status)
        assertTrue(geoserver.wmsQueries.isEmpty())
    }

    @Test
    fun `sin READ sobre el objeto de la capa es 403`() {
        val concessions = layerName("mining_concession", "boundary")
        geoserver.published += concessions
        assertEquals(HttpStatus.OK, wms(getMap(concessions)).status)
        // FIELD_AGENT no tiene READ sobre mining_concession (roles.json)
        val agent = userWithRole("FIELD_AGENT")
        assertEquals(HttpStatus.FORBIDDEN, wms(getMap(concessions), agent.token).status)
    }

    @Test
    fun `SLD_BODY, CQL_FILTER y GetFeatureInfo son 422 sin tocar GeoServer`() {
        geoserver.down = true
        val sld = wms(getMap("t_x__y", "&SLD_BODY=x"))
        assertEquals(HttpStatus.valueOf(422), sld.status, sld.text)
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(sld))
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(wms(getMap("t_x__y", "&CQL_FILTER=INCLUDE"))))
        val info = wms("SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=t_x__y")
        assertEquals(HttpStatus.valueOf(422), info.status)
        assertEquals("WMS_REQUEST_NOT_ALLOWED", code(info))
    }

    @Test
    fun `formato y tamano fuera de la lista son 422`() {
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(wms(getMap("t_x__y").replace("image/png", "image/gif"))))
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(wms(getMap("t_x__y").replace("WIDTH=256", "WIDTH=4096"))))
    }

    @Test
    fun `GetCapabilities lista solo la lista blanca y no se reenvia`() {
        val units = layerName("administrative_unit", "boundary")
        val localities = layerName("locality", "location")
        geoserver.published += setOf(units, OTHER_TENANT)
        val response = wms("SERVICE=WMS&REQUEST=GetCapabilities")
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertTrue(response.text.contains("<Name>$units</Name>"), response.text)
        assertFalse(response.text.contains(OTHER_TENANT))
        assertFalse(response.text.contains(localities))
        assertTrue(geoserver.wmsQueries.isEmpty())
    }

    @Test
    fun `GeoServer caido o lento es 503`() {
        val units = layerName("administrative_unit", "boundary")
        geoserver.published += units
        geoserver.down = true
        val down = wms(getMap(units))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, down.status)
        assertEquals("GEOSERVER_UNAVAILABLE", code(down))
        geoserver.down = false
        geoserver.delayMillis = 3000
        val started = System.nanoTime()
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, wms(getMap(units)).status)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2900, "el plazo del proxy es 1 s")
    }

    @Test
    fun `una respuesta que no es imagen es 503`() {
        val units = layerName("administrative_unit", "boundary")
        geoserver.published += units
        geoserver.wmsContentType = "application/vnd.ogc.se_xml"
        val response = wms(getMap(units))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status)
        assertFalse(response.text.contains("t_secreta"))
    }
}
