package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import sgspe.common.Records
import tools.jackson.databind.JsonNode
import wasichai.core.data.RecordService
import java.util.UUID
import kotlin.random.Random

// LYR-23 (gis.md §2.2 y §5.3 caso 8, ASM-063): las iniciativas y las entregas como registros, y los aportes ejecutados sumados
// por localidad y por moneda, sin mezclarlas; con periodo, los aportes entregados en él y las iniciativas que lo cruzan
class InvestmentLayerApiTest : F6QueryApiTest() {
    @Autowired
    private lateinit var platformRecords: Records

    @Autowired
    private lateinit var platformService: RecordService

    private fun band(): Double = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)

    // una entrega de un compromiso con su ubicación, como la plataforma (delivery es apiOnly)
    private fun deliveryAt(
        commitment: String,
        location: Map<String, Any?>
    ): String =
        runBlocking {
            platformService.asPlatform(organizationId()) {
                platformRecords.create(
                    "delivery",
                    mapOf(
                        "commitment" to commitment,
                        "delivery_date" to "2026-10-12",
                        "conformity_given" to true,
                        "recipients" to "Junta directiva de la Asociación Ejemplo",
                        "conformity_by" to "Persona Ejemplo"
                    ),
                    "entrega de prueba como la plataforma",
                    mapOf("location" to location)
                )
            }
        }.id.toString()

    // un aporte de un solo ítem BIEN de `amount` en `currency`, en `locality`, aprobado sin revisión interna (el proyecto no la
    // tiene) y, con `deliveredOn`, adquirido y entregado ese día por el endpoint de 5.2a con sus tres evidencias (P8)
    private fun contribution(
        project: String,
        investor: Account,
        locality: UUID,
        currency: String,
        amount: Number,
        deliveredOn: String?
    ): String {
        val id = socialContribution(project, investor, mapOf("currency" to currency, "beneficiaries_count" to 30, "locality" to locality.toString()), items = 0)
        contributionItem(id, investor, mapOf("item_kind" to "BIEN", "line_amount" to amount))
        move("social_contribution", id, "approve_direct", investor.token, "aprobación de prueba")
        if (deliveredOn == null) return id
        move("social_contribution", id, "register_procurement", investor.token, "compra de prueba")
        val evidence = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE").map { evidence(project, it, account = investor) }
        val captured =
            exchange(
                "POST",
                "/api/sgspe/social-contributions/$id/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to deliveredOn, "conformity_given" to true, "conformity_by" to "Persona Ejemplo"),
                    "evidence" to evidence
                ),
                investor.token,
                ifMatch("social_contribution", id, investor.token) + withReason("entrega de prueba")
            )
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", id, "register_delivery", investor.token, "entrega registrada")
        return id
    }

    // las sumas del elemento de aportes de la capa: moneda -> (cuántos, total)
    private fun sums(body: JsonNode): Map<String, Pair<Int, Double>> =
        body["features"]
            .values()
            .single { it["properties"]["__aggregate"]?.asString() == "social_contribution" }["properties"]["contributions"]
            .values()
            .associate { it["currency"].asString() to (it["count"].asInt() to it["totalAmount"].asDouble()) }

    @Test
    fun `LYR-23 suma por localidad y por moneda los aportes ejecutados y dibuja iniciativas y entregas`() {
        val y = band()
        val project = projectWithCrs(mapOf("policy_internal_approval" to false))
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        contribution(project, investor, place, "PEN", 2000, "2026-10-10")
        contribution(project, investor, place, "PEN", 3000, "2026-10-11")
        contribution(project, investor, place, "USD", 500, "2026-10-12")
        contribution(project, investor, place, "PEN", 4000, "2026-08-10")
        // aprobado y no entregado: no es ejecutado
        contribution(project, investor, place, "PEN", 9000, null)
        val program = socialProgram(project)
        val initiative = socialInitiative(program, attributes = mapOf("locality" to place.toString()))
        val old = socialInitiative(program, attributes = mapOf("start_date" to "2025-01-01", "end_date" to "2025-06-30", "locality" to place.toString()))
        val handed = deliveryAt(commitment(project), at(300.0, y))

        val october = get("/api/sgspe/territory/layers/LYR-23?project=$project&periodStart=2026-10-01&periodEnd=2026-10-31")
        assertTrue(october["available"].asBoolean(), october.toString())
        assertTrue(absent(october["periodNotApplied"]))
        assertEquals(mapOf("PEN" to (2 to 5000.0), "USD" to (1 to 500.0)), sums(october))
        val records = october["features"].values().filter { absent(it["properties"]["__aggregate"]) }.associateBy { it["properties"]["__id"].asString() }
        assertEquals(setOf(initiative, handed), records.keys, "la iniciativa de 2025 ($old) no cruza octubre")
        assertEquals("locality", records.getValue(initiative)["properties"]["__target"].asString())
        assertEquals("$handed:location", records.getValue(handed)["id"].asString())
        val contributions = october["features"].values().single { it["properties"]["__aggregate"]?.asString() == "social_contribution" }
        assertEquals("$place:location", contributions["id"].asString())
        assertEquals("locality", contributions["properties"]["__object"].asString())

        val all = get("/api/sgspe/territory/layers/LYR-23?project=$project")
        assertEquals(mapOf("PEN" to (3 to 9000.0), "USD" to (1 to 500.0)), sums(all))
        assertTrue(all["features"].values().any { it["properties"]["__id"].asString() == old })
    }

    @Test
    fun `los receptores de una entrega solo los ve quien los lee y sin READ sobre la inversion es 403`() {
        val project = projectWithCrs()
        val handed = deliveryAt(commitment(project), at(300.0, band()))

        fun properties(token: String): JsonNode =
            get("/api/sgspe/territory/layers/LYR-23?project=$project", token)["features"]
                .values()
                .single { it["properties"]["__id"].asString() == handed }["properties"]
        val forAnalyst = properties(member("ANALYST", project).token)
        assertFalse(forAnalyst.has("recipients") || forAnalyst.has("conformity_by"), forAnalyst.toString())
        assertEquals("Junta directiva de la Asociación Ejemplo", properties(token)["recipients"].asString())
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("GET", "/api/sgspe/territory/layers/LYR-23?project=$project", token = member("FIELD_AGENT", project).token).status
        )
    }
}
