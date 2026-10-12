package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ForbiddenException
import wasichai.core.common.WasichaiException

// security.md §10.2 y especificación de F6 §3.3: solo imágenes de capas de la lista blanca, con parámetros en lista blanca.
// los nombres de capa son ficticios
class WmsRequestFilterTest {
    private val units = WmsLayer("t_demo_unidad__limite", "administrative_unit", "boundary", listOf("sgspe-lyr-01"), "LYR-01")
    private val concessions = WmsLayer("t_demo_concesion__poligono", "mining_concession", "boundary", listOf("sgspe-lyr-07"), "LYR-07")
    private val localities = WmsLayer("t_demo_localidad__ubicacion", "locality", "location", listOf("sgspe-lyr-02", "sgspe-lyr-03"), "LYR-02, LYR-03")
    private val whitelist = listOf(units, concessions, localities).associateBy { it.name }

    private fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> = pairs.groupBy({ it.first }, { it.second })

    private fun getMap(vararg extra: Pair<String, String>) =
        query(
            "SERVICE" to "WMS",
            "VERSION" to "1.3.0",
            "REQUEST" to "GetMap",
            "LAYERS" to units.name,
            "STYLES" to "",
            "CRS" to "EPSG:3857",
            "BBOX" to "-8740000,-890000,-8730000,-880000",
            "WIDTH" to "256",
            "HEIGHT" to "256",
            "FORMAT" to "image/png",
            "TRANSPARENT" to "TRUE",
            *extra
        )

    private fun code(e: WasichaiException): Pair<Int, String> = e.status.value() to e.violations.single().message

    @Test
    fun `un GetMap de una capa de la lista pasa con sus parametros y el estilo de layers json`() {
        val call = WmsRequestFilter.parse(getMap()) as WmsCall.Image
        val forward = WmsRequestFilter.authorize(call, whitelist)
        assertEquals(units.name, forward["LAYERS"])
        assertEquals("sgspe-lyr-01", forward["STYLES"])
        assertEquals("image/png", forward["FORMAT"])
        assertEquals("EPSG:3857", forward["CRS"])
        assertFalse(forward.keys.any { it.startsWith("SLD") || it == "CQL_FILTER" })
    }

    @Test
    fun `GetCapabilities no necesita mas parametros`() {
        assertEquals(WmsCall.Capabilities, WmsRequestFilter.parse(query("SERVICE" to "WMS", "REQUEST" to "GetCapabilities")))
    }

    @Test
    fun `GetFeatureInfo, DescribeLayer y GetFeature son WMS_REQUEST_NOT_ALLOWED`() {
        for (request in listOf("GetFeatureInfo", "DescribeLayer", "GetFeature")) {
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap("REQUEST" to request).mapValues { it.value.takeLast(1) }) }
            assertEquals(422 to "WMS_REQUEST_NOT_ALLOWED", code(e), request)
        }
        val wfs = assertThrows<WasichaiException> { WmsRequestFilter.parse(query("SERVICE" to "WFS", "REQUEST" to "GetCapabilities")) }
        assertEquals(422 to "WMS_REQUEST_NOT_ALLOWED", code(wfs))
    }

    @Test
    fun `SLD, SLD_BODY, CQL_FILTER, FILTER, FEATUREID, ENV y VIEWPARAMS son WMS_PARAMETER_NOT_ALLOWED`() {
        for (name in listOf("SLD", "SLD_BODY", "CQL_FILTER", "FILTER", "FEATUREID", "ENV", "VIEWPARAMS", "EXCEPTIONS")) {
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap(name to "x")) }
            assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e), name)
            assertEquals(name, e.violations.single().field)
        }
    }

    @Test
    fun `claves sin caja, y una repetida o prohibida en minusculas es 422`() {
        val lower = WmsRequestFilter.parse(getMap().mapKeys { it.key.lowercase() }) as WmsCall.Image
        assertEquals(units.name, WmsRequestFilter.authorize(lower, whitelist)["LAYERS"])
        val repeated = getMap() + ("layers" to listOf(concessions.name))
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(assertThrows<WasichaiException> { WmsRequestFilter.parse(repeated) }))
        val twice = getMap().mapValues { (key, values) -> if (key == "LAYERS") values + concessions.name else values }
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(assertThrows<WasichaiException> { WmsRequestFilter.parse(twice) }))
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap("sld_body" to "<x/>")) }))
    }

    @Test
    fun `formato, tamano, CRS y version fuera de la lista son WMS_PARAMETER_NOT_ALLOWED`() {
        val cases = listOf("FORMAT" to "image/gif", "WIDTH" to "4096", "HEIGHT" to "0", "CRS" to "EPSG:32717", "VERSION" to "2.0.0", "TRANSPARENT" to "quizas")
        for ((name, value) in cases) {
            val bad = getMap().mapValues { (key, values) -> if (key == name) listOf(value) else values }
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(bad) }
            assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e), "$name=$value")
        }
        val big = getMap().mapValues { (key, values) -> if (key == "WIDTH") listOf("2048") else values }
        assertTrue(WmsRequestFilter.parse(big) is WmsCall.Image)
    }

    @Test
    fun `una capa fuera de la lista blanca es 403 y un estilo ajeno es 422`() {
        val other = getMap().mapValues { (key, values) -> if (key == "LAYERS") listOf("t_otro_tenant__limite") else values }
        assertThrows<ForbiddenException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(other) as WmsCall.Image, whitelist) }
        val style = getMap().mapValues { (key, values) -> if (key == "STYLES") listOf("estilo-ajeno") else values }
        val e = assertThrows<WasichaiException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(style) as WmsCall.Image, whitelist) }
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e))
    }

    @Test
    fun `una capa con dos estilos acepta cualquiera de los suyos y sin estilo usa el primero`() {
        val second =
            getMap().mapValues { (key, values) ->
                if (key ==
                    "LAYERS"
                ) {
                    listOf(localities.name)
                } else if (key == "STYLES") {
                    listOf("sgspe-lyr-03")
                } else {
                    values
                }
            }
        assertEquals("sgspe-lyr-03", WmsRequestFilter.authorize(WmsRequestFilter.parse(second) as WmsCall.Image, whitelist)["STYLES"])
        val none = getMap().mapValues { (key, values) -> if (key == "LAYERS") listOf(localities.name) else values }
        assertEquals("sgspe-lyr-02", WmsRequestFilter.authorize(WmsRequestFilter.parse(none) as WmsCall.Image, whitelist)["STYLES"])
    }

    @Test
    fun `GetLegendGraphic de una capa de la lista`() {
        val call =
            WmsRequestFilter.parse(query("SERVICE" to "WMS", "REQUEST" to "GetLegendGraphic", "LAYER" to concessions.name, "FORMAT" to "image/png"))
        val forward = WmsRequestFilter.authorize(call as WmsCall.Image, whitelist)
        assertEquals(concessions.name, forward["LAYER"])
        assertEquals("sgspe-lyr-07", forward["STYLE"])
    }

    @Test
    fun `las capacidades solo nombran la lista blanca y escapan el texto`() {
        val xml = WmsCapabilities.xml(listOf(units.copy(title = "LYR-01 <límites>"), localities), "/api/sgspe/territory/wms")
        assertTrue(xml.contains("<Name>${units.name}</Name>"))
        assertTrue(xml.contains("<Name>sgspe-lyr-03</Name>"))
        assertTrue(xml.contains("LYR-01 &lt;límites&gt;"))
        assertFalse(xml.contains(concessions.name))
    }

    private fun with(
        name: String,
        vararg values: String
    ) = getMap().toMutableMap().also { it[name] = values.toList() }

    @Test
    fun `el BBOX se reenvia canonico y lo que no es decimal llano se rechaza`() {
        val ok = WmsRequestFilter.parse(with("BBOX", "1, 2,3,4\r\n")) as WmsCall.Image
        assertEquals("1.0,2.0,3.0,4.0", WmsRequestFilter.authorize(ok, whitelist)["BBOX"])
        for (bad in listOf("0x1p3,2,3,4", "1d,2,3,4", "1,2,3,4\u0000", "NaN,2,3,4", "1,2,3", "3,2,1,4")) {
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(with("BBOX", bad)) }
            assertEquals("BBOX", e.violations.single().field)
        }
    }

    @Test
    fun `capas repetidas se quitan con su estilo y las que no estan en la lista son 403 antes de contarlas`() {
        val dup = with("LAYERS", "${units.name},${units.name}").also { it["STYLES"] = listOf("sgspe-lyr-01,sgspe-lyr-01") }
        val forward = WmsRequestFilter.authorize(WmsRequestFilter.parse(dup) as WmsCall.Image, whitelist)
        assertEquals(units.name, forward["LAYERS"])
        val many = with("LAYERS", (1..4).joinToString(",") { "x$it" })
        assertThrows<ForbiddenException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(many) as WmsCall.Image, whitelist) }
    }

    @Test
    fun `con la lista blanca vacia toda capa es 403`() {
        assertThrows<ForbiddenException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(getMap()) as WmsCall.Image, emptyMap()) }
        val legend = WmsRequestFilter.parse(query("SERVICE" to "WMS", "REQUEST" to "GetLegendGraphic", "LAYER" to units.name, "FORMAT" to "image/png"))
        assertThrows<ForbiddenException> { WmsRequestFilter.authorize(legend as WmsCall.Image, emptyMap()) }
    }

    @Test
    fun `el campo del error se acota y no lleva controles`() {
        val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap("a\nb".repeat(40) to "x")) }
        val field = e.violations.single().field!!
        assertTrue(field.length <= 64 && field.none { it.isISOControl() })
    }

    @Test
    fun `capa con espacio de trabajo es 403, estilo de otra capa es 422, SRS con CRS es 422 y faltan LAYERS o BBOX`() {
        val ws = with("LAYERS", "sgspe:${units.name}")
        assertThrows<ForbiddenException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(ws) as WmsCall.Image, whitelist) }
        val two = with("LAYERS", "${units.name},${concessions.name}").also { it["STYLES"] = listOf("sgspe-lyr-07,sgspe-lyr-01") }
        val e = assertThrows<WasichaiException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(two) as WmsCall.Image, whitelist) }
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e))
        val both = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap("SRS" to "EPSG:3857")) }
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(both))
        for (name in listOf("LAYERS", "BBOX")) {
            val missing = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap().filterKeys { it != name }) }
            assertEquals(name, missing.violations.single().field)
        }
    }
}
