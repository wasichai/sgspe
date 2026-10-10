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

    // «Reclasificada de»: el código del original; vacío si no se muestra (en EXTERNO, un original confidencial), y «no
    // disponible» si quien exporta no lee los vínculos de reclasificación
    @Test
    fun `Reclasificada de trae el codigo, vacio si se reserva y no disponible si el vinculo no se lee`() {
        val reclassified = grievance.copy(attributes = grievance.attributes + ("reclassified_from_inquiry" to "c1"))

        fun cell(context: AttentionContext): Any? {
            val sheet = AttentionSheets.records("grievance", listOf(reclassified), context, Disclosure.EXTERNO)
            return sheet.rows.first()[sheet.header.indexOf("Reclasificada de")]
        }
        assertEquals("CO-1", cell(AttentionContext(all, codes = mapOf("c1" to "CO-1"))))
        assertEquals("", cell(AttentionContext(all, codes = mapOf("c1" to ""))))
        assertEquals(NOT_AVAILABLE, cell(AttentionContext(all - "grievance.reclassified_from_inquiry", codes = mapOf("c1" to "CO-1"))))
        assertTrue("grievance.reclassified_from_request" in AttentionSheets.REQUIRES)
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
        val summary = AttentionSheets.summary(mapOf("grievance" to sgspe.common.AttentionCounting.totals(listOf(grievance))), all)
        assertEquals(listOf<Any?>("Quejas", 1, 1, 0, 0, 0, 0, 0), summary.rows.first())
        assertEquals("abiertas", AttentionSheets.group("in_action"))
        assertEquals("declinadas", AttentionSheets.group("declined"))
    }

    // RQ-DSH-032: quien exporta sin leer responded_at no ve «Absueltas» como 0, sino «no disponible», en el «Resumen» de
    // INTERNO (también el total) y en «Absueltas por tipo» de PUBLICO; los demás conteos siguen
    @Test
    fun `sin leer responded_at las absueltas salen no disponible`() {
        val answered = grievance.copy(attributes = grievance.attributes + ("responded_at" to "2026-10-07T15:00:00Z"))
        val totals =
            mapOf(
                "grievance" to sgspe.common.AttentionCounting.totals(listOf(answered)),
                "inquiry" to sgspe.common.AttentionCounting.totals(emptyList())
            )
        val blind = AttentionSheets.summary(totals, all - "grievance.responded_at").rows.toList()
        assertEquals(listOf<Any?>("Quejas", 1, 1, 0, 0, 0, NOT_AVAILABLE, 0), blind[0])
        assertEquals(listOf<Any?>("Consultas", 0, 0, 0, 0, 0, 0, 0), blind[1])
        assertEquals(listOf<Any?>("Total", 1, 1, 0, 0, 0, NOT_AVAILABLE, 0), blind[2])
        assertEquals(listOf<Any?>("Total", 1, 1, 0, 0, 0, 1, 0), AttentionSheets.summary(totals, all).rows.toList()[2])
        val absolved =
            AttentionSheets
                .aggregates(
                    List(6) { answered.copy(id = "q$it") },
                    all - "grievance.responded_at",
                    listOf("grievance", "inquiry", "stakeholder_request").associateWith { catalog.states(it) },
                    catalog.enumValues("qcs_channel"),
                    catalog.enumValues("qcs_category"),
                    confidentialIncluded = false
                ).first { it.name == "Absueltas por tipo" }
                .rows
                .toList()
        // las 6 recibidas se reservan igual: lo oculto de la tabla (los ceros de consultas y solicitudes) suma menos de MIN
        assertEquals(listOf<Any?>("Quejas", "Recibidas", SmallCells.HIDDEN), absolved[0])
        assertEquals(listOf<Any?>("Quejas", "Absueltas", NOT_AVAILABLE), absolved[1])
        assertEquals(listOf<Any?>("Consultas", "Absueltas", SmallCells.HIDDEN), absolved[3])
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
