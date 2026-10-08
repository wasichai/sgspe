package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// RN-TER-003 y WF-O: los miembros de una versión que no está en draft no cambian. la decisión es pura (qué áreas toca la
// escritura y en qué estado están), así que se prueba sin base con la forma exacta de las escrituras de wasichai: un
// vínculo llega como dos UPDATE, uno por cada registro, con rel:<relación> en los atributos. hoy las relaciones del miembro
// son MANY_TO_ONE y /related/... responde 400, así que la vía real es el atributo area_of_influence (las pruebas de alta,
// cambio, baja y movimiento); las de rel: son defensivas. la prueba de punta a punta,
// con la versión aprobada por su endpoint, es de la Tarea 16 (AreaOfInfluenceWorkflowGuardsApiTest)
class TerritoryWriteGuardTest {
    private val draft = UUID.randomUUID()
    private val approved = UUID.randomUUID()
    private val superseded = UUID.randomUUID()
    private val states = mapOf(draft to "draft", approved to "approved", superseded to "superseded")

    private fun write(
        objectName: String,
        kind: RecordChangeKind,
        recordId: UUID? = UUID.randomUUID(),
        before: Map<String, Any?>? = null,
        attributes: Map<String, Any?>? = null,
        transition: String? = null
    ) = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, recordId, kind, before, attributes, transition, null)

    private fun findings(
        change: RecordWrite,
        after: Map<String, Any?> = change.attributes.orEmpty()
    ): List<Finding> = runBlocking { TerritoryWriteGuard.check(change, after) { states[it] } }

    private fun member(area: UUID) = mapOf("area_of_influence" to area.toString(), "member_condition" to "INCLUIDO")

    @Test
    fun `un miembro se da de alta, cambia y se borra en una version draft`() {
        assertTrue(findings(write("area_of_influence_member", RecordChangeKind.CREATED, attributes = member(draft))).isEmpty())
        val before = member(draft)
        assertTrue(findings(write("area_of_influence_member", RecordChangeKind.UPDATED, before = before, attributes = before), before).isEmpty())
        assertTrue(findings(write("area_of_influence_member", RecordChangeKind.DELETED, before = before), emptyMap()).isEmpty())
    }

    @Test
    fun `altas, cambios y bajas de miembros de una version aprobada o reemplazada son 409 FROZEN_MEMBERS`() {
        for (area in listOf(approved, superseded)) {
            val before = member(area)
            val blocked =
                listOf(
                    findings(write("area_of_influence_member", RecordChangeKind.CREATED, attributes = before)),
                    findings(write("area_of_influence_member", RecordChangeKind.UPDATED, before = before, attributes = before), before),
                    findings(write("area_of_influence_member", RecordChangeKind.DELETED, before = before), emptyMap())
                )
            for (found in blocked) {
                assertEquals(listOf("FROZEN_MEMBERS"), found.map { it.code })
                assertEquals(HttpStatus.CONFLICT, found.single().status)
                assertEquals("area_of_influence", found.single().field)
            }
        }
    }

    @Test
    fun `mover un miembro hacia o desde una version congelada tambien se bloquea`() {
        val fromDraft = member(draft)
        val toApproved = member(approved)
        assertEquals(1, findings(write("area_of_influence_member", RecordChangeKind.UPDATED, before = fromDraft, attributes = toApproved), toApproved).size)
        assertEquals(1, findings(write("area_of_influence_member", RecordChangeKind.UPDATED, before = toApproved, attributes = fromDraft), fromDraft).size)
    }

    @Test
    fun `defensivo - vincular un miembro a una version congelada desde el lado del miembro es 409`() {
        val member = UUID.randomUUID()
        // defensivo: la forma que mandaría wasichai si vinculara el MANY_TO_ONE del miembro con su área (hoy /related/... da 400)
        val link =
            write(
                "area_of_influence_member",
                RecordChangeKind.UPDATED,
                recordId = member,
                before = member(draft),
                attributes = mapOf("rel:aoi_member_area_of_influence" to approved.toString())
            )
        assertEquals(listOf("FROZEN_MEMBERS"), findings(link, member(draft)).map { it.code })
    }

    @Test
    fun `defensivo - cambiar un vinculo de un miembro de una version congelada es 409 por cualquier relacion`() {
        // la localidad, la comunidad o la unidad administrativa de un miembro de una versión aprobada tampoco cambian
        for (relation in listOf("aoi_member_locality", "aoi_member_community", "aoi_member_administrative_unit", "aoi_member_area_of_influence")) {
            for (value in listOf(UUID.randomUUID().toString(), null)) {
                val link =
                    write(
                        "area_of_influence_member",
                        RecordChangeKind.UPDATED,
                        before = member(approved),
                        attributes = mapOf("rel:$relation" to value)
                    )
                assertEquals(listOf("FROZEN_MEMBERS"), findings(link, member(approved)).map { it.code }, relation)
            }
        }
        val draftLink =
            write(
                "area_of_influence_member",
                RecordChangeKind.UPDATED,
                before = member(draft),
                attributes = mapOf("rel:aoi_member_locality" to UUID.randomUUID().toString())
            )
        assertTrue(findings(draftLink, member(draft)).isEmpty())
    }

    @Test
    fun `defensivo - vincular y desvincular un miembro desde el lado del area congelada es 409, y de una en draft pasa`() {
        for (value in listOf(UUID.randomUUID().toString(), null)) {
            val onApproved =
                write(
                    "area_of_influence",
                    RecordChangeKind.UPDATED,
                    recordId = approved,
                    before = emptyMap(),
                    attributes =
                        mapOf("rel:aoi_member_area_of_influence" to value)
                )
            assertEquals(listOf("FROZEN_MEMBERS"), findings(onApproved, emptyMap()).map { it.code })
            val onDraft =
                write(
                    "area_of_influence",
                    RecordChangeKind.UPDATED,
                    recordId = draft,
                    before = emptyMap(),
                    attributes =
                        mapOf("rel:aoi_member_area_of_influence" to value)
                )
            assertTrue(findings(onDraft, emptyMap()).isEmpty())
        }
    }

    @Test
    fun `las demas escrituras de un area aprobada no son de este guarda`() {
        // un cambio de la propia área, su transición o un vínculo que no es de miembros (grupos de interés, proyecto)
        assertTrue(
            findings(
                write(
                    "area_of_influence",
                    RecordChangeKind.UPDATED,
                    recordId = approved,
                    before = mapOf("criteria" to "a"),
                    attributes =
                        mapOf("criteria" to "b")
                )
            ).isEmpty()
        )
        assertTrue(findings(write("area_of_influence", RecordChangeKind.TRANSITIONED, recordId = approved, transition = "supersede")).isEmpty())
        assertTrue(
            findings(
                write(
                    "area_of_influence",
                    RecordChangeKind.UPDATED,
                    recordId = approved,
                    attributes =
                        mapOf("rel:stakeholder_area_of_influence" to UUID.randomUUID().toString())
                )
            ).isEmpty()
        )
        assertTrue(
            findings(write("stakeholder", RecordChangeKind.UPDATED, attributes = mapOf("rel:stakeholder_area_of_influence" to approved.toString()))).isEmpty()
        )
    }

    @Test
    fun `un area que quien llama no puede leer se rechaza con 403 y no se deja pasar`() {
        val before = member(approved)
        val found =
            runBlocking {
                TerritoryWriteGuard.check(write("area_of_influence_member", RecordChangeKind.DELETED, before = before), emptyMap()) {
                    throw ForbiddenException("sin lectura")
                }
            }
        assertEquals(listOf("OBJECT_NOT_READABLE"), found.map { it.code })
        assertEquals(HttpStatus.FORBIDDEN, found.single().status)
    }

    @Test
    fun `un area que no existe no congela nada`() {
        val before = member(UUID.randomUUID())
        assertTrue(findings(write("area_of_influence_member", RecordChangeKind.DELETED, before = before), emptyMap()).isEmpty())
    }
}
