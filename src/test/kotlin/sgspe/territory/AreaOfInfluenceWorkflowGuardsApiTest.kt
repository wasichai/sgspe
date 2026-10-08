package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

// los casos de WF-O que dependen de 2.2 (AreaOfInfluenceWorkflowApiTest): approve y supersede por la API genérica se
// rechazan (COMMAND_ONLY); el endpoint aprueba la nueva y reemplaza la anterior en una transacción, con If-Match, motivo, la
// doble llave (UPDATE y APPROVE) y al menos un miembro; los miembros de una versión aprobada o reemplazada no cambian
// (RN-TER-003, FROZEN_MEMBERS): el miembro apunta a su área por un campo RELATION (MANY_TO_ONE), así que el camino real es el
// POST, el PUT o el DELETE del miembro, no el vínculo de wasichai
class AreaOfInfluenceWorkflowGuardsApiTest : DomainApiTest() {
    private fun memberOf(
        area: String,
        locality: String = createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"))
    ): String =
        createRecord(
            "area_of_influence_member",
            mapOf("area_of_influence" to area, "locality" to locality, "member_condition" to "INCLUIDO")
        )

    private fun version(
        project: String,
        number: Int,
        member: Boolean = true
    ): String {
        val area = createRecord("area_of_influence", mapOf("project" to project, "area_type" to "AIDS", "version_number" to number))
        if (member) memberOf(area)
        return area
    }

    private fun firstMember(area: String): String =
        tree(send("GET", "/api/objects/area_of_influence_member/records?area_of_influence=$area", null, HttpStatus.OK))["content"][0]["id"].asString()

    private fun updatedAt(area: String): String = record("area_of_influence", area)["updatedAt"].asString()

    private fun approve(
        area: String,
        token: String = this.token,
        ifMatch: String? = updatedAt(area),
        why: String? = "aprobación del diagnóstico",
        path: String = "/api/sgspe/areas-of-influence/$area/approve",
        body: Any? = null
    ) = exchange(
        "POST",
        path,
        body,
        token,
        listOfNotNull(ifMatch?.let { IfMatch.HEADER to "\"$it\"" }, why?.let { REASON to reason(it) }).toMap()
    )

    // el código de la regla: errors[0].message, y el campo en errors[0].field
    private fun code(body: String): String = tree(body)["errors"][0]["message"].asString()

    private fun errorField(body: String): String = tree(body)["errors"][0]["field"].asString()

    @Test
    fun `approve y supersede por la API generica son 403 COMMAND_ONLY`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val first = version(project, 1)
        val genericApprove = transition("area_of_influence", first, "approve", manager.token)
        assertEquals(HttpStatus.FORBIDDEN, genericApprove.status, genericApprove.body)
        assertEquals("COMMAND_ONLY", code(genericApprove.body))
        assertEquals("draft", state("area_of_influence", first))
        // supersede necesita una versión aprobada: se aprueba por el endpoint, que sí puede aplicarla
        assertEquals(HttpStatus.OK, approve(first, manager.token).status)
        val genericSupersede = transition("area_of_influence", first, "supersede", manager.token)
        assertEquals(HttpStatus.FORBIDDEN, genericSupersede.status, genericSupersede.body)
        assertEquals("COMMAND_ONLY", code(genericSupersede.body))
        assertEquals("approved", state("area_of_influence", first))
    }

    @Test
    fun `el endpoint aprueba la nueva version y reemplaza la anterior`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val first = version(project, 1)
        val approved = tree(send("POST", "/api/sgspe/areas-of-influence/$first/approve", null, HttpStatus.OK, manager.token, approveHeaders(first)))
        assertEquals("approved", approved["approved"]["state"].asString())
        assertNull(approved["superseded"]?.takeUnless { it.isNull })
        val second = version(project, 2)
        val replaced = tree(approve(second, manager.token).body)
        assertEquals("approved", replaced["approved"]["state"].asString())
        assertEquals(first, replaced["superseded"]["id"].asString())
        assertEquals("superseded", record("area_of_influence", first)["state"].asString())
        assertEquals("approved", record("area_of_influence", second)["state"].asString())
    }

    @Test
    fun `una version de otro tipo o de otro proyecto no se reemplaza`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val otherProject = projectWithCrs()
        val first = version(project, 1)
        val otherType = createRecord("area_of_influence", mapOf("project" to project, "area_type" to "AIIS", "version_number" to 1))
        memberOf(otherType)
        val elsewhere = version(otherProject, 1)
        assertEquals(HttpStatus.OK, approve(first, manager.token).status)
        assertEquals(HttpStatus.OK, approve(elsewhere).status)
        val replaced = tree(approve(otherType, manager.token).body)
        assertNull(replaced["superseded"]?.takeUnless { it.isNull })
        assertEquals("approved", state("area_of_influence", first))
        assertEquals("approved", state("area_of_influence", elsewhere))
    }

    @Test
    fun `PROJECT_MANAGER, con u y APPROVE, aprueba sin escribir campos de negocio`() {
        val project = projectWithCrs()
        val manager = member("PROJECT_MANAGER", project)
        val area = version(project, 1)
        val response = approve(area, manager.token)
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertEquals("approved", record("area_of_influence", area)["state"].asString())
    }

    @Test
    fun `sin If-Match 428, con uno viejo 412, sin motivo 400, sin miembros 409`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val area = version(project, 1)
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, approve(area, manager.token, ifMatch = null).status)
        assertEquals(HttpStatus.PRECONDITION_FAILED, approve(area, manager.token, ifMatch = "2020-01-01T00:00:00Z").status)
        val noReason = approve(area, manager.token, why = null)
        assertEquals(HttpStatus.BAD_REQUEST, noReason.status)
        assertEquals("reason", errorField(noReason.body))
        assertEquals("draft", state("area_of_influence", area))
        val empty = approve(version(project, 2, member = false), manager.token)
        assertEquals(HttpStatus.CONFLICT, empty.status, empty.body)
        assertEquals("MEMBERS_REQUIRED", code(empty.body))
        assertEquals("area_of_influence_member", errorField(empty.body))
    }

    @Test
    fun `solo se aprueba una version en borrador`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val area = version(project, 1)
        assertEquals(HttpStatus.OK, approve(area, manager.token).status)
        // aprobar otra vez la misma versión, con el If-Match nuevo, choca con su estado
        val again = approve(area, manager.token)
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("STATE_MISMATCH", code(again.body))
        assertEquals("approved", state("area_of_influence", area))
        // y una versión ya reemplazada tampoco vuelve
        val second = version(project, 2)
        assertEquals(HttpStatus.OK, approve(second, manager.token).status)
        assertEquals("superseded", state("area_of_influence", area))
        assertEquals(HttpStatus.CONFLICT, approve(area, manager.token).status)
    }

    @Test
    fun `quien no tiene UPDATE, APPROVE y un rol de la transicion recibe 403 y nada cambia`() {
        val project = projectWithCrs()
        val area = version(project, 1)
        // GIS_ANALYST tiene UPDATE pero no APPROVE; COMMUNITY_RELATIONS_OFFICER y READ_ONLY solo leen
        for (role in listOf("GIS_ANALYST", "COMMUNITY_RELATIONS_OFFICER", "READ_ONLY")) {
            val refused = approve(area, member(role, project).token)
            assertEquals(HttpStatus.FORBIDDEN, refused.status, "$role: ${refused.body}")
        }
        assertEquals("draft", state("area_of_influence", area))
    }

    @Test
    fun `una consulta o una propiedad desconocida se rechaza con su nombre`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val area = version(project, 1)
        val query = approve(area, manager.token, path = "/api/sgspe/areas-of-influence/$area/approve?dryRun=true")
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, query.status, query.body)
        assertEquals("dryRun", errorField(query.body))
        val body = approve(area, manager.token, body = mapOf("version" to 3))
        assertEquals(HttpStatus.BAD_REQUEST, body.status, body.body)
        assertEquals("version", errorField(body.body))
        assertEquals("draft", state("area_of_influence", area))
    }

    @Test
    fun `los miembros de una version aprobada no cambian`() {
        val project = projectWithCrs()
        val area = version(project, 1)
        approve(area)
        val locality = createRecord("locality", mapOf("name" to "Sector Ejemplo", "locality_type" to "SECTOR"))
        val response =
            exchange(
                "POST",
                "/api/objects/area_of_influence_member/records",
                mapOf("attributes" to mapOf("area_of_influence" to area, "locality" to locality, "member_condition" to "INCLUIDO"))
            )
        assertEquals(HttpStatus.CONFLICT, response.status)
        assertEquals(listOf("area_of_influence"), problemFields(response.body))
    }

    @Test
    fun `alta, cambio, traslado y baja de un miembro de una version aprobada o reemplazada son 409 FROZEN_MEMBERS`() {
        val project = projectWithCrs()
        val first = version(project, 1)
        val frozenMember =
            firstMember(first)
        val draft = version(project, 2)
        val draftMember =
            firstMember(draft)
        val other = createRecord("locality", mapOf("name" to "Sector Ejemplo", "locality_type" to "SECTOR"))
        assertEquals(HttpStatus.OK, approve(first).status)

        fun assertFrozen(
            what: String,
            response: Response
        ) {
            assertEquals(HttpStatus.CONFLICT, response.status, "$what: ${response.body}")
            assertEquals("FROZEN_MEMBERS", code(response.body), what)
            assertEquals("area_of_influence", errorField(response.body), what)
        }

        // altas, cambios, traslados y bajas sobre la versión aprobada, y luego sobre la reemplazada
        fun frozenCases(label: String) {
            val create =
                exchange(
                    "POST",
                    "/api/objects/area_of_influence_member/records",
                    mapOf("attributes" to mapOf("area_of_influence" to first, "locality" to other, "member_condition" to "INCLUIDO"))
                )
            assertFrozen("$label: alta", create)
            assertFrozen("$label: cambio", edit("area_of_influence_member", frozenMember, mapOf("rationale" to "otra justificación")))
            assertFrozen("$label: cambio de localidad (el vínculo del miembro)", edit("area_of_influence_member", frozenMember, mapOf("locality" to other)))
            assertFrozen("$label: traslado hacia una versión en borrador", edit("area_of_influence_member", frozenMember, mapOf("area_of_influence" to draft)))
            assertFrozen("$label: traslado desde una versión en borrador", edit("area_of_influence_member", draftMember, mapOf("area_of_influence" to first)))
            assertFrozen("$label: baja", exchange("DELETE", "/api/objects/area_of_influence_member/records/$frozenMember"))
        }
        frozenCases("aprobada")
        val replacement = approve(draft)
        assertEquals(HttpStatus.OK, replacement.status, replacement.body)
        assertEquals("superseded", state("area_of_influence", first))
        frozenCases("reemplazada")
        // el miembro sigue como estaba
        assertEquals(first, record("area_of_influence_member", frozenMember)["attributes"]["area_of_influence"].asString())
    }

    @Test
    fun `aprobar recalcula el area oficial con el working_crs vigente del proyecto`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val boundary = mapOf("boundary" to square(-78.6, -7.9, 0.05, "MultiPolygon"))
        val area = createWith("area_of_influence", mapOf("project" to project, "area_type" to "AIDS", "version_number" to 1), boundary)
        memberOf(area)
        val before = record("area_of_influence", area)["attributes"]["calculated_area_ha"].decimalValue()
        // el proyecto cambia de zona de trabajo después de dibujar el borrador: el listener no lo ve, la aprobación sí
        assertEquals(HttpStatus.OK, edit("project", project, mapOf("working_crs" to "EPSG 32718")).status)
        val approved = approve(area, manager.token)
        assertEquals(HttpStatus.OK, approved.status, approved.body)
        val after = record("area_of_influence", area)["attributes"]["calculated_area_ha"].decimalValue()
        val reference = projectWithCrs(mapOf("working_crs" to "EPSG 32718"))
        val expected =
            createWith("area_of_influence", mapOf("project" to reference, "area_type" to "AIDS", "version_number" to 1), boundary)
                .let { record("area_of_influence", it)["attributes"]["calculated_area_ha"].decimalValue() }
        assertEquals(0, expected.compareTo(after), "el área oficial usa EPSG 32718: $after frente a $expected")
        assertTrue(before.compareTo(after) != 0, "el área no cambió de zona: $before")
        // la respuesta del comando ya trae el área recalculada
        assertEquals(0, after.compareTo(tree(approved.body)["approved"]["attributes"]["calculated_area_ha"].decimalValue()))
    }

    @Test
    fun `los miembros de una version en borrador si cambian`() {
        val project = projectWithCrs()
        val area = version(project, 1)
        val member =
            firstMember(area)
        val response = edit("area_of_influence_member", member, mapOf("rationale" to "criterio ajustado"))
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/area_of_influence_member/records/$member").status)
    }

    @Test
    fun `dos aprobaciones simultaneas del mismo proyecto y tipo dejan una sola version aprobada`() {
        val project = projectWithCrs()
        val first = version(project, 1)
        val second = version(project, 2)
        val ifMatch = listOf(first, second).associateWith { updatedAt(it) }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls =
                listOf(first, second).map { area ->
                    pool.submit<HttpStatus> {
                        start.await()
                        approve(area, ifMatch = ifMatch.getValue(area)).status
                    }
                }
            start.countDown()
            assertEquals(listOf(HttpStatus.OK, HttpStatus.OK), calls.map { it.get() })
        } finally {
            pool.shutdown()
        }
        val states = listOf(first, second).map { record("area_of_influence", it)["state"].asString() }.sorted()
        assertEquals(listOf("approved", "superseded"), states)
        val approved =
            tree(send("GET", "/api/objects/area_of_influence/records?project=$project&area_type=AIDS", null, HttpStatus.OK))["content"]
                .values()
                .count { it["state"].asString() == "approved" }
        assertTrue(approved == 1, "hay $approved versiones aprobadas")
    }

    private fun approveHeaders(area: String) = mapOf(IfMatch.HEADER to "\"${updatedAt(area)}\"", REASON to reason("aprobación del diagnóstico"))
}
