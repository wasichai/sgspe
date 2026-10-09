package sgspe.engagement

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.IfMatch
import sgspe.common.SgspeWrite
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordService
import java.util.UUID

// AttendanceCountsListener y su recalculador de JOB-02 (workflows.md §Pruebas «Listeners» y «Trabajos»): alta, cambio y
// baja de participantes mueven los conteos; un conteo que quedó mal lo corrige DerivedFieldsJob, con su motivo; un PUT
// que no trae los conteos, como los del portal, los conserva (DerivedFieldsKeeper). un listener o un trabajo que no
// cambia ningún valor no escribe: updatedAt y la historia quedan como estaban, y el If-Match de quien edita sigue valiendo.
// sgspe.jobs.enabled=false en las dos tareas de Gradle: la programación no corre durante las pruebas, que llaman a runOnce
class AttendanceCountsApiTest : DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Autowired
    private lateinit var recordService: RecordService

    private fun total(engagement: String): Int = record("engagement", engagement)["attributes"]["attendees_total_count"].asInt()

    private fun updatedAt(engagement: String): String = record("engagement", engagement)["updatedAt"].asString()

    private fun historySize(engagement: String): Int = tree(send("GET", "/api/objects/engagement/records/$engagement/history", null, HttpStatus.OK)).size()

    // un conteo dañado, como si el listener hubiera fallado: lo escribe la plataforma con la marca
    private fun damageTotal(engagement: String) {
        val organization = UUID.fromString(me()["organizationId"].asString())
        runBlocking {
            recordService.asPlatform(organization) {
                val stored = recordService.get("engagement", UUID.fromString(engagement)).attributes
                withContext(SgspeWrite) {
                    recordService.update(
                        "engagement",
                        UUID.fromString(engagement),
                        RecordRequest(stored + ("attendees_total_count" to 99)),
                        "prueba: conteo dañado"
                    )
                }
            }
        }
    }

    @Test
    fun `alta, cambio y baja de participantes mueven los conteos`() {
        val engagement = engagement(projectWithCrs())
        val first = participant(engagement, attributes = mapOf("represented_people_count" to 3, "sex" to "MASCULINO"))
        participant(engagement, attributes = mapOf("represented_people_count" to 2, "sex" to "FEMENINO"))
        assertEquals(5, total(engagement))
        edit("engagement_participant", first, mapOf("attended" to false))
        assertEquals(2, total(engagement))
        send("DELETE", "/api/objects/engagement_participant/records/$first", null, HttpStatus.NO_CONTENT)
        assertEquals(2, record("engagement", engagement)["attributes"]["attendees_female_count"].asInt())
    }

    @Test
    fun `DerivedFieldsJob corrige un conteo pendiente`() {
        val engagement = engagement(projectWithCrs())
        participant(engagement, attributes = mapOf("represented_people_count" to 4))
        damageTotal(engagement)
        assertEquals(99, total(engagement))
        val stateBefore = state("engagement", engagement)
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(4, total(engagement))
        // JOB-02 nunca aplica transiciones (RN-WFL-001)
        assertEquals(stateBefore, state("engagement", engagement))
        val history = tree(send("GET", "/api/objects/engagement/records/$engagement/history", null, HttpStatus.OK))
        // la escritura del trabajo nombra al trabajo (RQ-AUD-007); la del listener, al alta del participante, no
        assertEquals("derivado: conteos de asistentes (JOB-02 DerivedFieldsJob)", history.values().first()["reason"].asString())
        assertEquals(DerivedFieldsJob.reason("conteos de asistentes"), AttendanceCountsRecalculator.REASON)
        // y el origen de la fila de auditoría es el del trabajo (UPS-03, WAS-ADR-0050)
        assertEquals("job:derived-fields", history.values().first()["source"].asString())
        assertEquals("job:derived-fields", DerivedFieldsJob.SOURCE)
        assertEquals(AttendanceCountsWriter.REASON, history.values().map { it["reason"].asString() }.last { it.startsWith("derivado") })
    }

    @Test
    fun `la vuelta horaria corrige la interaccion cuyos participantes cambiaron desde la anterior`() {
        val engagement = engagement(projectWithCrs())
        assertNotNull(runBlocking { job.runOnce(full = false) })
        participant(engagement, attributes = mapOf("represented_people_count" to 6))
        damageTotal(engagement)
        assertNotNull(runBlocking { job.runOnce(full = false) })
        assertEquals(6, total(engagement))
    }

    @Test
    fun `un PUT que omite los derivados, como el portal, los conserva`() {
        val engagement = engagement(projectWithCrs())
        participant(engagement, attributes = mapOf("represented_people_count" to 7, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        val current = record("engagement", engagement)["attributes"]
        assertEquals(7, current["attendees_total_count"].asInt())
        val attributes =
            current.properties().associate { (k, v) -> k to json(v) }.filterKeys { it !in AttendanceCounts.FIELDS } + ("title" to "Reunión renombrada")
        val response = exchange("PUT", "/api/objects/engagement/records/$engagement", mapOf("attributes" to attributes))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val stored = record("engagement", engagement)["attributes"]
        assertEquals("Reunión renombrada", stored["title"].asString())
        assertEquals(7, stored["attendees_total_count"].asInt())
        assertEquals(7, stored["attendees_female_count"].asInt())
        assertEquals(7, stored["attendees_direct_area_count"].asInt())
        assertEquals(0, stored["attendees_national_count"].asInt())
    }

    @Test
    fun `un participante que no cambia ningun conteo no toca la interaccion y el If-Match anterior sigue valiendo`() {
        val engagement = engagement(projectWithCrs())
        participant(engagement, attributes = mapOf("represented_people_count" to 3, "sex" to "MASCULINO"))
        val read = updatedAt(engagement)
        val entries = historySize(engagement)
        // no asistió: la suma queda igual
        val absent = participant(engagement, attributes = mapOf("attended" to false, "represented_people_count" to 2, "sex" to "FEMENINO"))
        assertEquals(3, total(engagement))
        // y un cambio del participante que no toca ningún campo del que dependen los conteos
        edit("engagement_participant", absent, mapOf("phone" to "999 000 111"))
        assertEquals(read, updatedAt(engagement))
        assertEquals(entries, historySize(engagement))
        val response = edit("engagement", engagement, mapOf("title" to "Reunión renombrada"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.OK, response.status, response.body)
    }

    @Test
    fun `DerivedFieldsJob no escribe lo que ya esta bien`() {
        val engagement = engagement(projectWithCrs())
        participant(engagement, attributes = mapOf("represented_people_count" to 5))
        val read = updatedAt(engagement)
        val entries = historySize(engagement)
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(read, updatedAt(engagement))
        assertEquals(entries, historySize(engagement))
    }
}
