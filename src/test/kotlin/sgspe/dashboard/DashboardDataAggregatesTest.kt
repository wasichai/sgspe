package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.territory.GateOutcome
import sgspe.territory.GateRunner
import sgspe.territory.SpatialGate
import wasichai.core.common.ValidationException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.time.LocalDate
import java.util.UUID

// plan 8.2a-bis: los conteos de DashboardData por la compuerta, sin base. un campo que quien lee no puede leer no se declara
// ni se nombra (sale NULL, como en Records); un campo por el que se filtra en la base tiene que leerse (el mismo 400 de
// Criteria); sin proyectos no corre nada; cada consulta corre una vez por pedido; con la plataforma va por selectAsPlatform
class DashboardDataAggregatesTest {
    private val organization = UUID.randomUUID()
    private val person = AuthenticatedUser(UUID.randomUUID(), organization, "analista@sgspe.test", listOf("ANALYST"))
    private val catalog = ModelCatalog.fromClasspath()
    private val sql = mutableListOf<String>()

    // la definición de cada objeto con los campos del modelo y los de sus relaciones, columna c_<campo>
    private fun definition(objectName: String): ObjectDefinition {
        val objectId = UUID.randomUUID()
        val relations = catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }
        return ObjectDefinition(
            CustomObject(objectId, organization, objectName, objectName, objectName, null, true, "${objectName}_tbl", null, null),
            (
                catalog.objects
                    .getValue(objectName)
                    .fields.keys + relations
            ).distinct().mapIndexed { i, name ->
                CustomField(UUID.randomUUID(), objectId, name, name, FieldType.TEXT, "c_$name", false, false, null, null, i, null, null, emptyMap(), true, true)
            }
        )
    }

    private fun aggregates(user: AuthenticatedUser?) =
        KpiAggregates(
            SpatialGate(
                { person },
                { { true } },
                { _, name -> definition(name) },
                { _, _ -> emptyList() },
                { null },
                { "\"data\".\"${it.obj.physicalTable}\"" },
                GateRunner { text, _, _ ->
                    sql += text
                    emptyList()
                },
                { user }
            )
        )

    private fun data(
        unreadable: Set<String> = emptySet(),
        projects: List<UUID> = listOf(UUID.randomUUID()),
        excludeConfidential: Boolean = false,
        platform: UUID? = null
    ) = DashboardData(
        mock(Records::class.java),
        DashboardFilters.of(projects, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
        { it !in unreadable },
        LocalDate.parse("2026-10-14"),
        platform = platform,
        excludeConfidential = excludeConfidential,
        aggregates = aggregates(if (platform == null) person else null)
    )

    @Test
    fun `un campo ilegible no se nombra y uno por el que se filtra es el 400 de Criteria`() {
        runBlocking { data(unreadable = setOf("engagement.engagement_type")).heldCounts() }
        assertFalse(sql.single().contains("c_engagement_type"), sql.single())
        assertTrue(sql.single().contains("CAST(NULL AS text) AS type"), sql.single())
        val error = assertThrows<ValidationException> { runBlocking { data(unreadable = setOf("engagement.held_at")).heldCounts() } }
        assertEquals("Filtro inválido", error.message)
    }

    @Test
    fun `excludeConfidential deja fuera las QCS confidenciales en los tres objetos`() {
        runBlocking { data(excludeConfidential = true).attentionCounts() }
        assertEquals(3, Regex("""x\."c_confidential" IS NOT TRUE""").findAll(sql.single()).count(), sql.single())
        sql.clear()
        runBlocking { data().attentionCounts() }
        assertFalse(sql.single().contains("IS NOT TRUE"), sql.single())
    }

    @Test
    fun `sin proyectos no corre nada y cada consulta corre una vez por pedido`() {
        assertEquals(GateOutcome.Rows(emptyList<GroupCount>()), runBlocking { data(projects = emptyList()).warningCounts() })
        assertTrue(sql.isEmpty())
        val once = data()
        runBlocking {
            once.attentionCounts()
            once.attentionCounts()
            once.highRiskCounts()
        }
        assertEquals(2, sql.size)
    }

    @Test
    fun `con la plataforma los conteos van por selectAsPlatform`() {
        runBlocking { data(platform = organization).openCommitmentCounts() }
        assertTrue(sql.single().contains("organization_id = :g"), sql.single())
        // con una persona en el contexto, la vía de la plataforma no corre
        val leaked =
            DashboardData(
                mock(Records::class.java),
                DashboardFilters.of(listOf(UUID.randomUUID()), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
                { true },
                LocalDate.parse("2026-10-14"),
                platform = organization,
                aggregates = aggregates(person)
            )
        assertThrows<IllegalStateException> { runBlocking { leaked.openCommitmentCounts() } }
    }
}
