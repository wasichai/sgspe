package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.territory.GateOutcome
import sgspe.territory.GateRunner
import sgspe.territory.SpatialGate
import wasichai.core.data.RecordCriterion
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// plan 8.2a-bis (Review Focus 1): los conteos del tablero executive corren por la compuerta como la persona, con sus alcances
// de lectura en la tabla de cada objeto, o como la plataforma por selectAsPlatform, sin alcances y nunca con una persona en el
// contexto
class KpiAggregatesTest {
    private val organization = UUID.randomUUID()
    private val officer = AuthenticatedUser(UUID.randomUUID(), organization, "relacionista@sgspe.test", listOf("COMMUNITY_RELATIONS_OFFICER"))
    private val project = UUID.randomUUID()

    private val warning: ObjectDefinition =
        UUID.randomUUID().let { objectId ->
            ObjectDefinition(
                CustomObject(objectId, organization, "early_warning", "Alerta", "Alertas", null, true, "early_warning_tbl", null, null),
                listOf("project", "severity", "locality", "community").mapIndexed { i, field ->
                    CustomField(
                        id = UUID.randomUUID(),
                        objectId = objectId,
                        name = field,
                        label = field,
                        type = FieldType.TEXT,
                        columnName = "c_$field",
                        required = false,
                        unique = false,
                        defaultValue = null,
                        description = null,
                        position = i,
                        enumOptions = null,
                        relationTargetObjectId = null,
                        attributes = emptyMap(),
                        visible = true,
                        editable = true
                    )
                }
            )
        }

    private class Captured {
        var sql: String? = null
        val scoped = mutableListOf<String>()
    }

    private fun aggregates(
        captured: Captured,
        userOrNull: suspend () -> AuthenticatedUser?
    ) = KpiAggregates(
        SpatialGate(
            { officer },
            { { true } },
            { _, _ -> warning },
            { _, definition ->
                captured.scoped += definition.obj.name
                listOf(RecordCriterion { _, bind -> "c_project = ANY(${bind(arrayOf(project))})" })
            },
            { null },
            { "\"data\".\"${it.obj.physicalTable}\"" },
            GateRunner { sql, _, _ ->
                captured.sql = sql
                emptyList()
            },
            userOrNull
        )
    )

    private val query =
        AggregateSql.grouped(
            "warnings",
            AggregateSource("early_warning", setOf("project", "severity")),
            AggregateFilter(listOf(project), emptyList()),
            listOf("severity" to "text")
        ) { emptyList() }

    @Test
    fun `como la persona la tabla lleva su alcance de lectura`() {
        val captured = Captured()
        val outcome = runBlocking { aggregates(captured) { officer }.select(query, platform = null) }
        assertEquals(GateOutcome.Rows(emptyList<GroupCount>()), outcome)
        assertEquals(listOf("early_warning"), captured.scoped)
        // la tabla de la compuerta con la organización y el alcance, y después el filtro de los proyectos
        val table = Regex("""FROM \(SELECT \* FROM "data"\."early_warning_tbl" WHERE organization_id = :g\d+ AND \(c_project = ANY\(:g\d+\)\)\) x WHERE """)
        assertTrue(table.containsMatchIn(captured.sql!!), captured.sql)
    }

    @Test
    fun `como la plataforma no pide alcances y no corre con una persona en el contexto`() {
        val captured = Captured()
        runBlocking { aggregates(captured) { null }.select(query, platform = organization) }
        assertTrue(captured.scoped.isEmpty())
        assertFalse(captured.sql!!.contains("c_project = ANY"), captured.sql)
        val blocked = Captured()
        assertThrows<IllegalStateException> { runBlocking { aggregates(blocked) { officer }.select(query, platform = organization) } }
        assertNull(blocked.sql)
    }
}
