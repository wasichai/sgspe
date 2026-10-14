package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import wasichai.core.common.ConflictException

// el modelo de los indicadores contra wasichai (§4.1 de la especificación de F8): el catálogo cargado, SOCIAL_MANAGER edita la
// meta pero no el código, la API genérica no escribe mediciones (ni al admin) y una medición es inmutable, también como la
// plataforma
class IndicatorModelApiTest : F8IndicatorsApiTest() {
    @Test
    fun `el catalogo cargado trae los 53 KPI con la meta de KPI-ENG-02`() {
        val all = get("/api/objects/indicator/records?size=200")["content"].values().associateBy { it["attributes"]["code"].asString() }
        assertEquals(true, all.size >= 53, all.keys.toString())
        val meetings = all.getValue("KPI-ENG-02")["attributes"]
        assertEquals(2, meetings["target_value"].asInt())
        assertEquals("Mensual", meetings["frequency"].asString())
    }

    @Test
    fun `SOCIAL_MANAGER edita la meta pero no el codigo`() {
        val social = member("SOCIAL_MANAGER")
        val id = indicatorId("KPI-COM-02")
        val stored = attributesOf(record("indicator", id))
        val path = "/api/objects/indicator/records/$id"
        assertEquals(HttpStatus.OK, exchange("PUT", path, mapOf("attributes" to stored + ("target_value" to 5)), social.token).status)
        val renamed = exchange("PUT", path, mapOf("attributes" to stored + ("code" to "KPI-COM-99")), social.token)
        assertEquals(HttpStatus.BAD_REQUEST, renamed.status, renamed.body)
        assertEquals("FIELD_NOT_EDITABLE", code(renamed))
        assertEquals("KPI-COM-02", record("indicator", id)["attributes"]["code"].asString())
        assertEquals(HttpStatus.FORBIDDEN, exchange("PUT", path, mapOf("attributes" to stored), member("READ_ONLY").token).status)
        // vuelve a su meta (null) para no tocar las demás pruebas de la JVM
        send("PUT", path, mapOf("attributes" to stored), HttpStatus.OK)
    }

    @Test
    fun `la API generica no escribe mediciones, ni al admin`() {
        val attributes = sample("indicator_measurement")
        for (caller in listOf(token, member("SOCIAL_MANAGER").token)) {
            val created = exchange("POST", "/api/objects/indicator_measurement/records", mapOf("attributes" to attributes), caller)
            assertEquals(HttpStatus.FORBIDDEN, created.status, created.body)
        }
    }

    @Test
    fun `una medicion no cambia ni se borra, tampoco como la plataforma`() {
        val (start, end) = lastMonth()
        // quien cierra es un usuario real del tenant: la regla de closed_by lo comprueba también para la plataforma
        val closer = userWithRole("SOCIAL_MANAGER")
        val measurement =
            platformCreate(
                "indicator_measurement",
                mapOf(
                    "indicator" to indicatorId("KPI-COM-01"),
                    "project" to testProject(),
                    "period_start_date" to start.toString(),
                    "period_end_date" to end.toString(),
                    "measured_value" to 4,
                    "closed_by_user_id" to closer.id,
                    "closed_by_user_email" to closer.email
                )
            )
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/indicator_measurement/records/$measurement").status)
        assertThrows<ConflictException> { platformUpdate(measurement, mapOf("measured_value" to 5)) }
        assertEquals(4, record("indicator_measurement", measurement)["attributes"]["measured_value"].asInt())
    }

    private fun platformUpdate(
        id: String,
        attributes: Map<String, Any?>
    ) {
        kotlinx.coroutines.runBlocking {
            platformRecords.asPlatform(organizationId()) {
                platformRecords.patch("indicator_measurement", java.util.UUID.fromString(id), attributes, "cambio de prueba como la plataforma")
            }
        }
    }
}
