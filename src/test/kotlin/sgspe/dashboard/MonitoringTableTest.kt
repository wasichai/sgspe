package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// el tablero monitoring sin base (dashboards.md §4.9, §5.5 y §10; especificación de F8 §3.1): las 13 filas de la Tabla 9.5
// en su orden, la meta y su origen (decisión 3 del plan 8.2a) y la cifra oficial solo cuando el pedido coincide con la
// medición (decisión 4; Review Focus 3)
class MonitoringTableTest {
    private val start = LocalDate.parse("2026-09-01")
    private val end = LocalDate.parse("2026-09-30")
    private val project = UUID.randomUUID()
    private val filters = DashboardFilters.of(listOf(project), start, end)

    private fun value(
        code: String,
        value: Number? = 4,
        available: Boolean = true,
        denominator: Number? = null
    ) = IndicatorValue(code, code, available, if (available) value else null, denominator = denominator)

    private val values = MonitoringTable.CODES.map { value(it) }

    private fun catalog(vararg targets: Pair<String, BigDecimal?>): Map<String, CatalogIndicator> =
        MonitoringTable.CODES.associateWith { code -> CatalogIndicator("id-$code", code, targets.toMap()[code], null) }

    private fun measurement(
        code: String,
        project: String?,
        start: LocalDate = this.start,
        end: LocalDate = this.end
    ) = MeasurementRow("m-$code-$project-$start", "id-$code", project, start, end, BigDecimal("7"), Instant.parse("2026-10-02T15:00:00Z"))

    @Test
    fun `las 13 filas son las de dashboards md 4 9 en su orden`() {
        val doc =
            File("docs/dashboards.md")
                .readLines()
                .filter { Regex("""^\| \d+ \| """).containsMatchIn(it) }
                .map { line ->
                    line
                        .trim()
                        .trim('|')
                        .split("|")
                        .map { it.trim() }
                }
        assertEquals(13, doc.size)
        assertEquals(
            doc.map { listOf(it[0].toInt(), it[1], it[2], it[4], it[5]) },
            MonitoringTable.ROWS.map { listOf(it.number, it.label, it.code, it.verificationMeans, it.deliverable) }
        )
    }

    // decisión 3 (R1): la meta de #5 es siempre el denominador de KPI-ENG-02, que ya cae al catálogo; aquí solo cambia el origen
    @Test
    fun `la meta de 5 es el denominador de KPI-ENG-02 con el origen del proyecto, del catalogo o ninguno`() {
        val meetings = MonitoringTable.ROWS.single { it.code == "KPI-ENG-02" }
        val withTarget = value("KPI-ENG-02", 5, denominator = 6)
        val two = CatalogIndicator("i", "KPI-ENG-02", BigDecimal("2"), null)
        assertEquals(6 to MonitoringTable.PROJECT, MonitoringTable.target(meetings, withTarget, two, projectDefined = true))
        assertEquals(6 to MonitoringTable.CATALOG, MonitoringTable.target(meetings, withTarget, two, projectDefined = false))
        assertEquals(null to MonitoringTable.NONE, MonitoringTable.target(meetings, withTarget, two.copy(target = null), projectDefined = false))
        assertEquals(null to MonitoringTable.NONE, MonitoringTable.target(meetings, value("KPI-ENG-02", available = false), two, projectDefined = false))
        // el catálogo cargado sin el registro de KPI-ENG-02: el KPI usó la meta de ASM-019 y la fila la dice con su origen; sin
        // catálogo (un tenant de F7 o quien no lo lee), ninguna
        assertEquals(2 to MonitoringTable.DEFAULT, MonitoringTable.target(meetings, value("KPI-ENG-02", 1, denominator = 2), null, false, catalogLoaded = true))
        assertEquals(
            null to MonitoringTable.NONE,
            MonitoringTable.target(meetings, value("KPI-ENG-02", 1, denominator = 2), null, false, catalogLoaded = false)
        )
        assertEquals(6 to MonitoringTable.PROJECT, MonitoringTable.target(meetings, withTarget, null, projectDefined = true, catalogLoaded = true))
        // un proyecto que define la meta pero el KPI sin denominador: sin meta que mostrar, NONE (no PROJECT con null)
        assertEquals(null to MonitoringTable.NONE, MonitoringTable.target(meetings, value("KPI-ENG-02", 1), two, projectDefined = true))
        val other = MonitoringTable.ROWS.first()
        assertEquals(
            BigDecimal("80") to MonitoringTable.CATALOG,
            MonitoringTable.target(other, value(other.code), CatalogIndicator("x", other.code, BigDecimal("80"), null), false)
        )
    }

    // Review Focus 3
    @Test
    fun `la cifra oficial casa solo con el mismo alcance, periodo y sin otros filtros`() {
        val code = "KPI-COM-08"
        val ofProject = listOf(measurement(code, project.toString()))
        val ofTenant = listOf(measurement(code, null))
        val indicator = CatalogIndicator("id-$code", code, null, null)
        val one = MonitoringTable.scope(listOf(project), filters, tenantWide = false)
        assertEquals(
            Official("m-$code-$project-$start", "2026-10-02T15:00:00Z", BigDecimal("7")),
            MonitoringTable.official(indicator, value(code), ofProject, one)
        )
        // otro periodo, otro alcance, un filtro de territorio, dos proyectos o el tenant sin leerlo entero: ninguna
        assertNull(
            MonitoringTable.official(indicator, value(code), listOf(measurement(code, project.toString(), start.minusMonths(1), end.minusMonths(1))), one)
        )
        assertNull(MonitoringTable.official(indicator, value(code), ofTenant, one))
        assertNull(MonitoringTable.scope(listOf(project), filters.copy(locality = UUID.randomUUID()), tenantWide = true))
        assertNull(MonitoringTable.scope(listOf(project), filters.copy(scope = "AIDS"), tenantWide = true))
        assertNull(MonitoringTable.scope(listOf(project, UUID.randomUUID()), filters, tenantWide = true))
        assertNull(MonitoringTable.scope(emptyList(), filters, tenantWide = false))
        val tenant = MonitoringTable.scope(emptyList(), filters, tenantWide = true)
        assertEquals("m-$code-null-$start", MonitoringTable.official(indicator, value(code), ofTenant, tenant)?.measurement)
        assertNull(MonitoringTable.official(indicator, value(code), ofProject, tenant))
    }

    // Review Focus 3: #2 y #3 a quien no lee stance, también con el periodo cerrado
    @Test
    fun `sin el KPI disponible no hay cifra oficial`() {
        val code = "KPI-STK-05"
        val scope = MonitoringTable.scope(listOf(project), filters, tenantWide = false)
        val hidden = value(code, available = false)
        assertNull(MonitoringTable.official(CatalogIndicator("id-$code", code, null, null), hidden, listOf(measurement(code, project.toString())), scope))
        val rows =
            MonitoringTable.rows(
                MonitoringTable.CODES.map {
                    if (it ==
                        code
                    ) {
                        hidden
                    } else {
                        value(it)
                    }
                },
                catalog(),
                listOf(measurement(code, project.toString())),
                scope,
                false
            )
        val row = rows.single { it.code == code }
        assertEquals(false to null, row.available to row.value)
        assertNull(row.official)
        // revisión 4 (R4): a quien se le ocultan QCS confidenciales, una fila que las lee no muestra la cifra oficial
        val qcs = "KPI-GRV-07"
        val hiddenQcs = IndicatorValue(qcs, qcs, true, 50, confidentialHidden = true)
        assertNull(MonitoringTable.official(CatalogIndicator("id-$qcs", qcs, null, null), hiddenQcs, listOf(measurement(qcs, project.toString())), scope))
    }

    // Review Focus 5: un tenant de F7 o quien no lee indicator
    @Test
    fun `sin catalogo, sin metas ni cifras oficiales`() {
        val scope = MonitoringTable.scope(listOf(project), filters, tenantWide = false)
        val rows = MonitoringTable.rows(values, emptyMap(), emptyList(), scope, projectDefined = false)
        assertEquals((1..13).toList(), rows.map { it.number })
        assertEquals(setOf(MonitoringTable.NONE), rows.map { it.targetSource }.toSet())
        assertEquals(listOf<Official?>(null), rows.map { it.official }.distinct())
        assertEquals(MonitoringTable.ROWS.map { it.verificationMeans }, rows.map { it.verificationMeans })
        assertEquals(listOf<String?>(null), rows.map { it.indicatorId }.distinct())
    }

    // el id del indicator de cada fila, para que el portal no lo busque por código; null si el catálogo no lo trae
    @Test
    fun `cada fila lleva el id de su indicator del catalogo`() {
        val partial = catalog().filterKeys { it != "KPI-ENG-02" }
        val rows = MonitoringTable.rows(values, partial, emptyList(), null, projectDefined = false).associateBy { it.code }
        assertEquals("id-KPI-STK-04", rows.getValue("KPI-STK-04").indicatorId)
        assertNull(rows.getValue("KPI-ENG-02").indicatorId)
    }

    @Test
    fun `el catalogo pone la meta y el medio de verificacion editado`() {
        val edited =
            catalog("KPI-ENG-02" to BigDecimal("2")) +
                ("KPI-STK-04" to CatalogIndicator("id-KPI-STK-04", "KPI-STK-04", BigDecimal("30"), "Padrón de contactos"))
        val meetings = MonitoringTable.CODES.map { if (it == "KPI-ENG-02") value(it, 1, denominator = 2) else value(it) }
        val rows = MonitoringTable.rows(meetings, edited, emptyList(), null, projectDefined = false).associateBy { it.code }
        assertEquals(2 to MonitoringTable.CATALOG, rows.getValue("KPI-ENG-02").target to rows.getValue("KPI-ENG-02").targetSource)
        assertEquals(BigDecimal("30") to "Padrón de contactos", rows.getValue("KPI-STK-04").target to rows.getValue("KPI-STK-04").verificationMeans)
    }

    @Test
    fun `con el catalogo sin KPI-ENG-02 la fila 5 dice la meta de ASM-019`() {
        val partial = catalog().filterKeys { it != "KPI-ENG-02" }
        val meetings = MonitoringTable.CODES.map { if (it == "KPI-ENG-02") value(it, 1, denominator = 2) else value(it) }
        val row = MonitoringTable.rows(meetings, partial, emptyList(), null, projectDefined = false).single { it.code == "KPI-ENG-02" }
        assertEquals(2 to MonitoringTable.DEFAULT, row.target to row.targetSource)
    }
}
