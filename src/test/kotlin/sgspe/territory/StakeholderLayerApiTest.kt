package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import sgspe.stakeholder.InfluenceClassifier
import java.util.UUID
import kotlin.random.Random

// LYR-17 (gis.md §2.2, RQ-GIS-015, RQ-DSH-032): grupos de interés contados por postura en el punto de su localidad, nunca
// como puntos de personas; sin leer la postura, solo el total
class StakeholderLayerApiTest : F6QueryApiTest() {
    private val high: Map<String, Any?> = InfluenceClassifier.INDICATORS.associateWith { 4 }

    // un grupo de interés del proyecto en `locality` (o sin localidad) y, con `stance`, evaluado con esa postura por el
    // endpoint de evaluaciones, la única vía de la postura vigente (derivada)
    private fun stakeholderAt(
        project: String,
        manager: Account,
        locality: UUID?,
        stance: String?
    ): String {
        val id = stakeholder(project, profileFields(manager) + listOfNotNull(locality?.let { "locality" to it.toString() }))
        if (stance != null) {
            send(
                "POST",
                "/api/sgspe/stakeholders/$id/assessments",
                mapOf("attributes" to high + mapOf("assessment_date" to "2026-04-10", "basis" to "ENTREVISTA", "stance" to stance, "trust_score" to 3)),
                HttpStatus.CREATED,
                manager.token,
                mapOf(REASON to reason("evaluación de prueba"))
            )
        }
        return id
    }

    private fun band(): Double = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)

    @Test
    fun `LYR-17 cuenta los grupos de interes por postura en su localidad y nunca dibuja a una persona`() {
        val y = band()
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        val sector = localityWith("Sector Ejemplo", boundary = rect(1000.0, y, 2000.0, y + 1000), type = "SECTOR")
        val ids =
            listOf(
                stakeholderAt(project, manager, place, "A FAVOR"),
                stakeholderAt(project, manager, place, "A FAVOR"),
                stakeholderAt(project, manager, place, "EN CONTRA"),
                stakeholderAt(project, manager, place, null),
                stakeholderAt(project, manager, sector, "NEUTRAL"),
                stakeholderAt(project, manager, null, "NEUTRAL")
            )
        val raw = send("GET", "/api/sgspe/territory/layers/LYR-17?project=$project", null, HttpStatus.OK, manager.token)
        val body = tree(raw)
        assertTrue(body["available"].asBoolean(), raw)
        val features = body["features"].values().associateBy { it["properties"]["__id"].asString() }
        assertEquals(setOf(place.toString(), sector.toString()), features.keys)
        val here = features.getValue(place.toString())
        assertEquals("$place:location", here["id"].asString())
        assertEquals("Point", here["geometry"]["type"].asString())
        assertEquals("locality", here["properties"]["__object"].asString())
        assertEquals("stakeholder", here["properties"]["__aggregate"].asString())
        assertEquals("Caserío Ejemplo", here["properties"]["name"].asString())
        assertEquals(4, here["properties"]["total"].asInt())
        assertEquals(
            mapOf("A FAVOR" to 2, "EN CONTRA" to 1, "SIN DATO" to 1),
            here["properties"]["byStance"].properties().associate { it.key to it.value.asInt() }
        )
        // el punto de un sector sin ubicación sale de su límite
        assertEquals("$sector:boundary", features.getValue(sector.toString())["id"].asString())
        assertEquals(1, features.getValue(sector.toString())["properties"]["total"].asInt())
        // ningún grupo de interés se nombra: ni su id ni un punto suyo
        for (id in ids) assertFalse(raw.contains(id), "LYR-17 nombra al grupo de interés $id: $raw")
    }

    @Test
    fun `sin leer la postura solo sale el total, el periodo no aplica y sin READ es 403`() {
        val y = band()
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        stakeholderAt(project, manager, place, "A FAVOR")
        stakeholderAt(project, manager, place, "EN CONTRA")
        val analyst = member("ANALYST", project)
        val body = get("/api/sgspe/territory/layers/LYR-17?project=$project&periodStart=2026-10-01&periodEnd=2026-10-31", analyst.token)
        assertFalse(body["available"].asBoolean())
        assertEquals(listOf("stakeholder.stance"), body["missingFields"].values().map { it.asString() })
        assertEquals(listOf("stakeholder"), body["periodNotApplied"].values().map { it.asString() })
        val properties = body["features"].values().single()["properties"]
        assertEquals(2, properties["total"].asInt())
        assertTrue(absent(properties["byStance"]))
        // PROJECT_ADMIN no lee grupos de interés
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("GET", "/api/sgspe/territory/layers/LYR-17?project=$project", token = member("PROJECT_ADMIN", project).token).status
        )
        // sin asignación al proyecto no se cuenta nada (ProjectReadScope)
        assertEquals(0, get("/api/sgspe/territory/layers/LYR-17?project=$project", userWithRole("ANALYST").token)["features"].size())
    }
}
