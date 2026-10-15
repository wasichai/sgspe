package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.Callers
import sgspe.common.ModelCatalog
import sgspe.common.ObjectDefinitions
import sgspe.common.Records
import sgspe.common.TenantModel
import sgspe.territory.ComponentLand
import sgspe.territory.GateRunner
import sgspe.territory.SpatialGate
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

// plan 8.2a-bis (decisión 8): sgspe.dashboards.aggregates decide el camino de dashboard(), value() e indicator(): en true los
// KPI movidos cuentan en la base (corre SQL por la compuerta); en false leen filas con Records y la compuerta no corre. la
// propiedad se lee al arrancar, así que volver atrás es cambiar la configuración y reiniciar
class DashboardsAggregatesSwitchTest {
    private val organization = UUID.randomUUID()
    private val project = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()
    private val sql = mutableListOf<String>()
    private val reads = mutableListOf<String>()

    private fun definition(objectName: String): ObjectDefinition {
        val objectId = UUID.randomUUID()
        val relations = catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }
        val fields =
            catalog.objects[objectName]
                ?.fields
                ?.keys
                .orEmpty() + relations
        return ObjectDefinition(
            CustomObject(objectId, organization, objectName, objectName, objectName, null, true, "${objectName}_tbl", null, null),
            fields.distinct().mapIndexed { i, name ->
                CustomField(UUID.randomUUID(), objectId, name, name, FieldType.TEXT, "c_$name", false, false, null, null, i, null, null, emptyMap(), true, true)
            }
        )
    }

    // Records simulado: cada lectura de filas vuelve vacía y se anota
    private val records: Records =
        mock(Records::class.java) { call ->
            when (call.method.name) {
                "all" -> emptyList<Any>().also { reads += call.arguments[0] as String }
                else -> null
            }
        }

    private fun dashboards(aggregates: Boolean): Dashboards {
        val gate =
            SpatialGate(
                { error("sin persona") },
                { { true } },
                { _, name -> definition(name) },
                { _, _ -> emptyList() },
                { null },
                { "\"data\".\"${it.obj.physicalTable}\"" },
                GateRunner { text, _, _ ->
                    sql += text
                    emptyList()
                },
                { null }
            )
        val readers =
            KpiReaders(
                { null },
                { error("sin persona") },
                { _, block -> block() },
                { { true } },
                { _, name -> definition(name) },
                { false },
                { null },
                { listOf(project) }
            )
        return Dashboards(
            records,
            mock(MetadataService::class.java),
            mock(Callers::class.java),
            catalog,
            Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneOffset.UTC),
            mock(ComponentLand::class.java),
            readers,
            DashboardsProperties(aggregates = aggregates),
            TenantModel(mock(ObjectDefinitions::class.java) { throw NotFoundException("sin el modelo de F8") }),
            KpiAggregates(gate)
        )
    }

    private val params =
        LinkedMultiValueMap<String, String>().apply {
            add("project", project.toString())
            add("periodStart", "2026-09-01")
            add("periodEnd", "2026-09-30")
        }

    private fun runAll(dashboards: Dashboards) =
        runBlocking {
            val reader = KpiReader.Platform(organization)
            dashboards.dashboard(Kpis.EXECUTIVE, params, reader)
            dashboards.value("KPI-GRV-02", listOf(project), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), reader)
            dashboards.indicator("KPI-ENG-01", params, reader)
        }

    @Test
    fun `en true los KPI movidos cuentan en la base y no leen sus filas`() {
        runAll(dashboards(aggregates = true))
        assertTrue(sql.isNotEmpty())
        assertTrue(listOf("grievance", "engagement", "social_risk", "early_warning").none { it in reads }, reads.toString())
    }

    @Test
    fun `en false todo vuelve a la lectura con Records y la compuerta no corre`() {
        runAll(dashboards(aggregates = false))
        assertEquals(emptyList<String>(), sql)
        assertTrue(listOf("grievance", "engagement", "social_risk", "early_warning").all { it in reads }, reads.toString())
    }
}
