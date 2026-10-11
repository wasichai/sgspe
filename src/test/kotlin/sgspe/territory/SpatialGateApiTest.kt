package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID

// la compuerta por los endpoints (especificación de F6 §5, gis.md §9 «Compuerta de permisos»): sin READ sobre el predio, 403;
// el titular ilegible, no disponible y nunca vacío como si no hubiera; los componentes de un proyecto no asignado no aparecen
// (land_parcel es un maestro del tenant: lo que se acota es el componente, decisión 3); las QCS confidenciales no cuentan
// para quien no las ve; otro tenant no ve nada; el ADMIN ve todo
class SpatialGateApiTest : F6QueryApiTest() {
    private val y = -110_000.0

    private class Fixture(
        val a: UUID,
        val b: UUID,
        val parcel: UUID,
        val inA: UUID,
        val place: UUID
    )

    private fun fixture(): Fixture =
        runBlocking {
            val a = utmProject()
            val b = utmProject()
            val parcel = parcel(uniqueName("PR-"), "PROPIEDAD", rect(0.0, y, 1000.0, y + 1000))
            parcelOwner(parcel, "Persona Ejemplo")
            val inA = component(a, uniqueName("PLT-"), "PLATAFORMA", rect(400.0, y + 400, 600.0, y + 600))
            component(b, uniqueName("PLT-"), "PLATAFORMA", rect(400.0, y + 400, 600.0, y + 600))
            Fixture(a, b, parcel, inA, locality("Caserío Ejemplo", at(0.0, y)))
        }

    private fun parcelsPath(project: UUID) = "/api/sgspe/territory/projects/$project/component-parcels"

    @Test
    fun `sin READ sobre land_parcel la consulta de predios es 403`() {
        val f = fixture()
        val agent = member("FIELD_AGENT", f.a.toString())
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", parcelsPath(f.a), null, agent.token).status)
    }

    @Test
    fun `el titular ilegible sale no disponible con los cruces y sin titulares`() {
        val f = fixture()
        val analyst = member("ANALYST", f.a.toString())
        val body = get(parcelsPath(f.a), analyst.token)
        assertFalse(body["available"].asBoolean())
        assertTrue("land_right.holder_person" in body["missingFields"].values().map { it.asString() })
        val row = body["rows"].values().single { it["parcel"]["id"].asString() == f.parcel.toString() }
        assertEquals(f.inA.toString(), row["component"]["id"].asString())
        assertTrue(absent(row["holders"]))
    }

    @Test
    fun `los componentes de un proyecto no asignado no aparecen`() {
        val f = fixture()
        val analyst = member("ANALYST", f.a.toString())
        assertEquals("PROJECT_NOT_FOUND", failing(parcelsPath(f.b), HttpStatus.NOT_FOUND, analyst.token))
        grievance(f.a.toString(), attributes = mapOf("locality" to f.place.toString()))
        grievance(f.b.toString(), attributes = mapOf("locality" to f.place.toString()))
        val cells = get("/api/sgspe/territory/concentration?of=grievance&periodStart=2026-10-01&periodEnd=2026-10-31", analyst.token)["cells"].values()
        assertEquals(1, cells.single { it["id"].asString() == f.place.toString() }["count"].asInt())
    }

    @Test
    fun `una QCS confidencial no cuenta para quien no la ve y si para quien tiene READ_CONFIDENTIAL`() {
        val f = fixture()
        grievance(f.a.toString(), attributes = mapOf("locality" to f.place.toString()))
        runBlocking { confidentialGrievance(f.a, f.place) }
        val query = "/api/sgspe/territory/concentration?of=grievance&project=${f.a}&periodStart=2026-10-01&periodEnd=2026-10-31"

        fun count(token: String): Int = get(query, token)["cells"].values().single { it["id"].asString() == f.place.toString() }["count"].asInt()
        assertEquals(1, count(member("ANALYST", f.a.toString()).token))
        assertEquals(2, count(member("GRIEVANCE_MANAGER", f.a.toString()).token))
        assertEquals(2, count(token))
    }

    @Test
    fun `otro tenant no ve el proyecto y el ADMIN ve los cruces con sus titulares`() {
        val f = fixture()
        val stranger = provisionTenant()
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", parcelsPath(f.a), null, stranger.token).status)
        val body = get(parcelsPath(f.a))
        assertTrue(body["available"].asBoolean())
        val row = body["rows"].values().single { it["parcel"]["id"].asString() == f.parcel.toString() }
        assertEquals(listOf("person"), row["holders"].values().map { it["object"].asString() })
    }
}
