package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import wasichai.gis.GEOMETRY
import wasichai.gis.SRID_COLUMN
import java.util.UUID

// SpatialGate sin base (especificación de F6 §3.1, ASM-122): READ del objeto principal o 403; un objeto o campo secundario
// ilegible, no disponible sin correr nada; cada tabla como subconsulta con la organización, el filtro de dueño y cada
// criterio de alcance entre paréntesis, con la definición completa; columnas y SRID de la metadata y solo SELECT
class SpatialGateTest {
    private val organization = UUID.randomUUID()
    private val analyst = AuthenticatedUser(UUID.randomUUID(), organization, "analista@sgspe.test", listOf("ANALYST"))

    private class Captured {
        var sql: String? = null
        var bindings: Map<String, Any> = emptyMap()
        val scoped = mutableListOf<ObjectDefinition>()
    }

    // un objeto con campos de texto y, si llevan SRID, geometrías; la columna física es c_<campo>
    private fun definition(
        name: String,
        vararg fields: Pair<String, Int?>
    ): ObjectDefinition {
        val objectId = UUID.randomUUID()
        return ObjectDefinition(
            CustomObject(objectId, organization, name, name, name, null, true, "${name}_tbl", null, null),
            fields.mapIndexed { i, (field, srid) ->
                CustomField(
                    id = UUID.randomUUID(),
                    objectId = objectId,
                    name = field,
                    label = field,
                    type = if (srid == null) FieldType.TEXT else GEOMETRY,
                    columnName = "c_$field",
                    required = false,
                    unique = false,
                    defaultValue = null,
                    description = null,
                    position = i,
                    enumOptions = null,
                    relationTargetObjectId = null,
                    attributes = if (srid == null) emptyMap() else mapOf(SRID_COLUMN to srid),
                    visible = true,
                    editable = true
                )
            }
        )
    }

    private val parcel = definition("parcel", "code" to null, "registry" to null, "boundary" to 32718)
    private val right = definition("right", "parcel" to null, "holder" to null)

    private fun gate(
        readable: Set<String>,
        captured: Captured = Captured(),
        scope: RecordCriterion? = null,
        owner: UUID? = null,
        rows: List<Any> = emptyList()
    ) = SpatialGate(
        { analyst },
        { { path -> path in readable } },
        { _, name -> listOf(parcel, right).first { it.obj.name == name } },
        { _, definition ->
            captured.scoped += definition
            listOfNotNull(scope)
        },
        { owner },
        { "\"data\".\"${it.obj.physicalTable}\"" },
        GateRunner { sql, bindings, _ ->
            captured.sql = sql
            captured.bindings = bindings
            rows
        }
    )

    private val all = setOf("parcel", "parcel.code", "parcel.registry", "parcel.boundary", "right", "right.parcel", "right.holder")

    @Test
    fun `sin READ sobre el objeto principal es 403 y no corre nada`() {
        val captured = Captured()
        val gate = gate(setOf("right", "right.parcel"), captured)
        assertThrows<ForbiddenException> {
            runBlocking { gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT 1" }) { it } }
        }
        assertNull(captured.sql)
    }

    @Test
    fun `un objeto secundario o un campo ilegible es no disponible con lo que falta, sin correr nada`() {
        val captured = Captured()
        val gate = gate(setOf("parcel", "parcel.code", "parcel.boundary"), captured)
        val outcome =
            runBlocking {
                gate.select(
                    listOf(GateRead("parcel", setOf("code", "registry"), principal = true), GateRead("right", setOf("parcel", "holder"))),
                    { "SELECT 1" }
                ) { it }
            }
        assertEquals(GateOutcome.Unavailable(listOf("parcel.registry", "right.holder", "right.parcel")), outcome)
        assertNull(captured.sql)
    }

    @Test
    fun `cada tabla es una subconsulta con la organizacion y el alcance entre parentesis, con la definicion completa`() {
        val captured = Captured()
        val scope = RecordCriterion { definition, bind -> "${definition.fields.size} = ${bind(7)} OR x" }
        val gate = gate(setOf("parcel", "parcel.code", "parcel.boundary"), captured, scope)
        runBlocking {
            gate.select(listOf(GateRead("parcel", setOf("code", "boundary"), principal = true)), { bind ->
                "SELECT p.${column("parcel", SpatialGate.ID)} FROM ${table("parcel")} p WHERE p.${column("parcel", "code")} = ${bind("P-1")}"
            }) { it }
        }
        assertEquals(
            "SELECT p.\"id\" FROM (SELECT * FROM \"data\".\"parcel_tbl\" WHERE organization_id = :g0 AND (3 = :g1 OR x)) p " +
                "WHERE p.\"c_code\" = :g2",
            captured.sql
        )
        assertEquals(mapOf("g0" to organization, "g1" to 7, "g2" to "P-1"), captured.bindings)
        // el alcance recibe la definición completa (tres campos), no la proyección legible (dos)
        assertSame(parcel, captured.scoped.single())
    }

    @Test
    fun `el filtro de duenio de wasichai va despues de la organizacion y la tabla repetida reusa sus parametros`() {
        val captured = Captured()
        val owner = UUID.randomUUID()
        val gate = gate(all, captured, owner = owner)
        runBlocking {
            gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), {
                "SELECT a.${column("parcel", "code")} FROM ${table("parcel")} a JOIN ${table("parcel")} b ON a.\"id\" = b.\"id\""
            }) { it }
        }
        val inner = "(SELECT * FROM \"data\".\"parcel_tbl\" WHERE organization_id = :g0 AND created_by = :g1)"
        assertEquals("SELECT a.\"c_code\" FROM $inner a JOIN $inner b ON a.\"id\" = b.\"id\"", captured.sql)
        assertEquals(mapOf("g0" to organization, "g1" to owner), captured.bindings)
    }

    @Test
    fun `una columna no declarada es un error de programacion y srid sale de la metadata del campo declarado`() {
        val gate = gate(all)
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT ${column("parcel", "registry")}" }) { it } }
        }
        val captured = Captured()
        runBlocking {
            gate(all, captured).select(listOf(GateRead("parcel", setOf("boundary"), principal = true)), {
                "SELECT ST_Transform(x, ${srid("parcel", "boundary")})"
            }) { it }
        }
        assertEquals("SELECT ST_Transform(x, 32718)", captured.sql)
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT ${srid("parcel", "code")}" }) { it } }
        }
    }

    @Test
    fun `solo corre una sentencia SELECT o WITH y devuelve lo que mapea`() {
        val gate = gate(all, rows = listOf("fila"))
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", emptySet(), principal = true)), { "DELETE FROM x" }) { it } }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", emptySet(), principal = true)), { "SELECT 1; DELETE FROM x" }) { it } }
        }
        val outcome = runBlocking { gate.select(listOf(GateRead("parcel", emptySet(), principal = true)), { "WITH a AS (SELECT 1) SELECT * FROM a" }) { it } }
        assertEquals(GateOutcome.Rows(listOf("fila")), outcome)
    }
}
