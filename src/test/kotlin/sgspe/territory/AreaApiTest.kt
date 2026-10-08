package sgspe.territory

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.IfMatch
import sgspe.common.Records
import sgspe.common.SgspeWrite
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordService
import java.math.BigDecimal
import java.util.UUID

// gis.md §9 «Área derivada»: cambiar la geometría por la API genérica recalcula calculated_area_ha (AreaListener); si el
// valor quedó mal, DerivedFieldsJob lo corrige (JOB-02); el motivo dice qué regla lo escribió y, en el trabajo, cuál lo
// escribió. un listener o un trabajo que no cambia el valor no escribe: una entrada vacía en la historia sería un 412 para
// quien tiene el If-Match anterior. sgspe.jobs.enabled=false en las dos tareas de Gradle: la programación no corre
class AreaApiTest : DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Autowired
    private lateinit var recordService: RecordService

    private fun communityAt(side: Double): String =
        createWith(
            "community",
            mapOf("name" to "Comunidad Ejemplo"),
            mapOf("territory" to square(-78.6, -7.9, side, "MultiPolygon"))
        )

    private fun areaOf(
        objectName: String,
        id: String
    ): BigDecimal = record(objectName, id)["attributes"]["calculated_area_ha"].decimalValue()

    private fun updatedAt(
        objectName: String,
        id: String
    ): String = record(objectName, id)["updatedAt"].asString()

    private fun history(
        objectName: String,
        id: String
    ) = tree(send("GET", "/api/objects/$objectName/records/$id/history", null, HttpStatus.OK))

    // un área dañada, como si el listener hubiera fallado: la escribe la plataforma con la marca, con su misma geometría
    private fun damage(
        community: String,
        value: Int
    ) {
        val organization = UUID.fromString(me()["organizationId"].asString())
        runBlocking {
            recordService.asPlatform(organization) {
                val stored = recordService.get("community", UUID.fromString(community)).attributes
                withContext(SgspeWrite) {
                    val request = RecordRequest(stored + ("calculated_area_ha" to value))
                    request.section(Records.GEOMETRIES, mapOf("territory" to square(-78.6, -7.9, 0.01, "MultiPolygon")))
                    recordService.update("community", UUID.fromString(community), request, "prueba: área dañada")
                }
            }
        }
    }

    @Test
    fun `el area de una comunidad sale de su territorio en la zona del centroide`() {
        val community = communityAt(0.01)
        val area = areaOf("community", community)
        assertTrue(area > BigDecimal(100) && area < BigDecimal(150), "área $area")
        edit("community", community, emptyMap(), geometries = mapOf("territory" to square(-78.6, -7.9, 0.02, "MultiPolygon")))
        val bigger = areaOf("community", community)
        assertTrue(bigger > area * BigDecimal(3), "área $bigger")
        // el listener firma con una razón fija; la escritura del cliente, sin motivo
        assertEquals("derivado: área en ha", history("community", community).values().first()["reason"].asString())
    }

    @Test
    fun `el area de un area de influencia usa el working_crs del proyecto y el trabajo corrige lo pendiente`() {
        val project = projectWithCrs()
        val area =
            createWith(
                "area_of_influence",
                mapOf("project" to project, "area_type" to "AIDS", "version_number" to 1),
                mapOf("boundary" to square(-78.6, -7.9, 0.01, "MultiPolygon"))
            )
        val computed = areaOf("area_of_influence", area)
        assertTrue(computed > BigDecimal(100))
        val read = updatedAt("area_of_influence", area)
        val entries = history("area_of_influence", area).size()
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(computed, areaOf("area_of_influence", area))
        // lo que ya está bien no se vuelve a escribir
        assertEquals(read, updatedAt("area_of_influence", area))
        assertEquals(entries, history("area_of_influence", area).size())
    }

    @Test
    fun `DerivedFieldsJob corrige un area danada y su escritura nombra al trabajo`() {
        val community = communityAt(0.01)
        val area = areaOf("community", community)
        damage(community, 1)
        assertEquals(0, BigDecimal.ONE.compareTo(areaOf("community", community)))
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(0, area.compareTo(areaOf("community", community)))
        val reasons = history("community", community).values().map { it["reason"].asString() }
        // la del trabajo es la más reciente y dice «JOB-02»; la del listener, al alta, no
        assertEquals(DerivedFieldsJob.reason("área en ha"), reasons.first())
        assertEquals(AreaRecalculator.REASON, reasons.first())
        assertTrue(reasons.contains(Areas.REASON), "$reasons")
        assertEquals("derivado: área en ha (JOB-02 DerivedFieldsJob)", AreaRecalculator.REASON)
    }

    @Test
    fun `un PUT con If-Match tambien recalcula el area, porque la escritura del listener no es la del cliente`() {
        val community = communityAt(0.01)
        val read = updatedAt("community", community)
        val response =
            edit(
                "community",
                community,
                emptyMap(),
                headers = mapOf(IfMatch.HEADER to "\"$read\""),
                geometries = mapOf("territory" to square(-78.6, -7.9, 0.02, "MultiPolygon"))
            )
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertTrue(areaOf("community", community) > BigDecimal(400))
    }

    @Test
    fun `el updatedAt de la respuesta del alta sigue valiendo como If-Match aunque el listener escribio el area`() {
        val created =
            tree(
                send(
                    "POST",
                    "/api/objects/community/records",
                    mapOf(
                        "attributes" to mapOf("name" to "Comunidad Ejemplo"),
                        "geometries" to mapOf("territory" to square(-78.6, -7.9, 0.01, "MultiPolygon"))
                    ),
                    HttpStatus.CREATED
                )
            )
        val community = created["id"].asString()
        val stale = created["updatedAt"].asString()
        assertTrue(stale != updatedAt("community", community), "el listener debió escribir el área")
        val response = edit("community", community, mapOf("registered_members_count" to 40), headers = mapOf(IfMatch.HEADER to "\"$stale\""))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val again = edit("community", community, mapOf("registered_members_count" to 41), headers = mapOf(IfMatch.HEADER to "\"$stale\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, again.status, again.body)
    }

    @Test
    fun `una edicion que no toca la geometria no reescribe el area y el If-Match de su respuesta sigue valiendo`() {
        val community = communityAt(0.01)
        val area = areaOf("community", community)
        val entries = history("community", community).size()
        val response = edit("community", community, mapOf("registered_members_count" to 40))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val returned = tree(response.body)["updatedAt"].asString()
        // el listener calculó el mismo valor y no escribió: ni updatedAt ni una entrada vacía en la historia
        assertEquals(returned, updatedAt("community", community))
        assertEquals(entries + 1, history("community", community).size())
        assertEquals(0, area.compareTo(areaOf("community", community)))
        // y el If-Match de esa respuesta vale para la edición siguiente
        val next = edit("community", community, mapOf("registered_members_count" to 41), headers = mapOf(IfMatch.HEADER to "\"$returned\""))
        assertEquals(HttpStatus.OK, next.status, next.body)
    }

    @Test
    fun `los miembros de una version en draft se crean, cambian y borran por la API generica`() {
        // que la guarda de miembros congelados no sobrebloquee; los casos de una versión aprobada, con su endpoint, son de la Tarea 16
        val area = fixture("area_of_influence", mapOf("area_type" to "AIDS", "version_number" to 1)).id
        val member = fixture("area_of_influence_member", mapOf("area_of_influence" to area, "member_condition" to "INCLUIDO")).id
        assertEquals(HttpStatus.OK, edit("area_of_influence_member", member, mapOf("rationale" to "Dentro del criterio de distancia")).status)
        send("DELETE", "/api/objects/area_of_influence_member/records/$member", null, HttpStatus.NO_CONTENT)
    }
}
