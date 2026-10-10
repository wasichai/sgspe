package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.common.Raised
import sgspe.deadline.Deadlines

// las hojas de attention-register por nivel de divulgación (deliverables.md §10, ASM-101), sin base
class AttentionSheetsTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val all = AttentionSheets.REQUIRES.toSet()
    private val stakeholder = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val grievance =
        Raised(
            "grievance",
            "q1",
            "received",
            mapOf(
                "code" to "QJ-1",
                "channel" to "PRESENCIAL",
                "received_at" to "2026-10-06T02:00:00Z",
                "received_by_user_email" to "relacionista@sgspe.test",
                "anonymous" to false,
                "confidential" to true,
                "category" to "EMPLEO",
                "deadline_status" to "AL DIA",
                "ack_due_date" to "2026-10-13",
                "owner_user_email" to "gestor@sgspe.test",
                "stakeholder" to stakeholder,
                "person" to "p1",
                "description" to "Texto libre de la queja",
                "resolution" to "Texto libre de la resolución",
                "severity" to "BAJA"
            )
        )
    private val context = AttentionContext(all, stakeholders = mapOf(stakeholder to "Asociación Ejemplo"), people = mapOf("p1" to "Persona Ejemplo"))

    private fun cells(sheet: XlsxSheet): List<Any?> = sheet.rows.toList().flatten()

    @Test
    fun `INTERNO trae persona, receptor, responsable, texto libre y el plazo del estado en hora de Lima`() {
        val sheet = AttentionSheets.records("grievance", listOf(grievance), context, Disclosure.INTERNO)
        assertEquals("Quejas", sheet.name)
        val row = cells(sheet)
        assertTrue(
            listOf("Persona Ejemplo", "Asociación Ejemplo", "relacionista@sgspe.test", "gestor@sgspe.test", "Texto libre de la queja").all {
                it in row
            },
            row.toString()
        )
        // las 02:00 UTC del 6 son las 21:00 del 5 en Lima; en received corre el acuse
        assertTrue("2026-10-05 21:00" in row)
        assertEquals(listOf("AL DIA", "2026-10-13"), row.subList(sheet.header.indexOf("Situación de plazo"), sheet.header.indexOf("Plazo que corre") + 1))
        assertFalse(sheet.header.any { it.contains("identidad", ignoreCase = true) || it.contains("teléfono", ignoreCase = true) })
    }

    @Test
    fun `EXTERNO cita por codigo GI y no trae persona, receptor, responsable ni texto libre`() {
        val sheet = AttentionSheets.records("grievance", listOf(grievance), context, Disclosure.EXTERNO)
        val row = cells(sheet)
        assertTrue("GI-AAAAAAAABBBB" in row, row.toString())
        assertTrue(
            listOf(
                "Persona Ejemplo",
                "Asociación Ejemplo",
                "relacionista@sgspe.test",
                "gestor@sgspe.test",
                "Texto libre de la queja",
                "Texto libre de la resolución"
            ).none {
                it in
                    row
            }
        )
        assertFalse(listOf("Persona", "Responsable", "Recibida por", "Descripción", "Respuesta", "Resolución").any { it in sheet.header })
        assertEquals(listOf("Las QCS confidenciales no se incluyen en esta exportación."), cells(AttentionSheets.notes(Disclosure.EXTERNO, false)).take(1))
    }

    @Test
    fun `una anonima sale anonima y lo que no se lee sale no disponible`() {
        val anonymous = grievance.copy(attributes = grievance.attributes + ("anonymous" to true))
        assertTrue("anónima" in cells(AttentionSheets.records("grievance", listOf(anonymous), context, Disclosure.INTERNO)))
        val blind = AttentionContext(all - "grievance.person")
        assertTrue(NOT_AVAILABLE in cells(AttentionSheets.records("grievance", listOf(grievance), blind, Disclosure.INTERNO)))
    }

    @Test
    fun `PUBLICO cuenta planteamientos sobre la grilla con reservado y dice si incluye confidenciales`() {
        val original = Raised("inquiry", "c1", "closed", mapOf("channel" to "CARTA"))
        val sheets =
            AttentionSheets.aggregates(
                listOf(grievance, original, grievance.copy(id = "q2", attributes = grievance.attributes + ("reclassified_from_inquiry" to "c1"))),
                all,
                listOf("grievance", "inquiry", "stakeholder_request").associateWith { catalog.states(it) },
                catalog.enumValues("qcs_channel"),
                catalog.enumValues("qcs_category"),
                confidentialIncluded = false
            )
        assertEquals(listOf("Por tipo y estado", "Por tipo y canal", "Por tipo y categoría", "Absueltas por tipo", "Notas"), sheets.map { it.name })
        val states = sheets.first().rows.toList()
        // grilla completa: la solicitud tiene sus declinadas aunque no haya ninguna, y todo se reserva
        assertTrue(states.any { it[0] == "Solicitudes" && it[1] == "declinadas" })
        assertTrue(states.all { it[2] == SmallCells.HIDDEN })
        assertTrue(cells(sheets.last()).any { it.toString().contains("confidenciales: no") })
        assertFalse(sheets.flatMap { cells(it) }.any { it == "QJ-1" })
    }

    @Test
    fun `el resumen trae los conteos del reporte y el estado agrupado de PUBLICO`() {
        val summary = AttentionSheets.summary(mapOf("grievance" to sgspe.common.AttentionCounting.totals(listOf(grievance))))
        assertEquals(listOf<Any?>("Quejas", 1, 1, 0, 0, 0, 0, 0), summary.rows.first())
        assertEquals("abiertas", AttentionSheets.group("in_action"))
        assertEquals("declinadas", AttentionSheets.group("declined"))
    }

    // los estados con plazo y su fecha son los de JOB-01 (privacy solo depende de common)
    @Test
    fun `los plazos de las hojas son los de JOB-01 y sus campos existen`() {
        for (tracked in Deadlines.TRACKED.filter { it.objectName in AttentionSheets.SHEETS }) {
            assertEquals(tracked.states.associateWith { tracked.dateFieldIn(it) }, AttentionSheets.DUE_FIELDS[tracked.objectName], tracked.objectName)
        }
        for (type in AttentionSheets.SHEETS.keys) {
            val fields = catalog.objectOrNull(type)!!.fields.keys + catalog.manyToOneFields(type).map { it.first }
            assertEquals(emptyList<String>(), AttentionSheets.fields(type).filter { it !in fields }, type)
        }
    }
}
