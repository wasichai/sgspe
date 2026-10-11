package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.SgspeApiTest

// GET /api/sgspe/map/settings (especificación de F6 §3.3): la organización sembrada (slug demo) con su estilo, otra con el
// general; sin token, 401
@TestPropertySource(
    properties = [
        "sgspe.map.base-style-url=https://estilos.sgspe.test/general.json",
        "sgspe.map.base-style-url-by-organization.demo=https://estilos.sgspe.test/demo.json"
    ]
)
class MapSettingsApiTest : SgspeApiTest() {
    @Test
    fun `cada organizacion recibe su estilo o el general`() {
        val demo = tree(send("GET", "/api/sgspe/map/settings", null, HttpStatus.OK))
        assertEquals("style", demo["basemap"]["type"].asString())
        assertEquals("https://estilos.sgspe.test/demo.json", demo["basemap"]["url"].asString())
        val other = provisionTenant()
        val general = tree(send("GET", "/api/sgspe/map/settings", null, HttpStatus.OK, other.token))
        assertEquals("https://estilos.sgspe.test/general.json", general["basemap"]["url"].asString())
        assertTrue(general["attribution"].isNull)
    }

    @Test
    fun `sin token es 401 y un parametro desconocido es 422`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/sgspe/map/settings", null, null).status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/map/settings?slug=otra").status)
    }
}
