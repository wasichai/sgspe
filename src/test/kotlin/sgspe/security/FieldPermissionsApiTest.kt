package sgspe.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// security.md §7.9 y §14, «Permisos de campo» (RQ-SEC-010): un campo restringido no llega al registro, a la lista, al
// historial ni a la auditoría de quien no lo lee, y escribirlo se rechaza nombrando el campo; quien lo lee lo ve. las
// exportaciones las prueba el plan 2.2 con su endpoint
class FieldPermissionsApiTest : F2ApiTest() {
    private fun record(
        objectName: String,
        id: String,
        account: Account
    ) = tree(send("GET", "/api/objects/$objectName/records/$id", null, HttpStatus.OK, account.token))["attributes"]

    // el registro tal como sale en la lista, filtrada por un campo legible con un valor único
    private fun listed(
        objectName: String,
        filter: Pair<String, String>,
        account: Account
    ) = tree(send("GET", "/api/objects/$objectName/records?${filter.first}=${filter.second}", null, HttpStatus.OK, account.token))["content"]
        .values()
        .single()["attributes"]

    private fun historyFields(
        objectName: String,
        id: String,
        account: Account
    ) = tree(send("GET", "/api/objects/$objectName/records/$id/history", null, HttpStatus.OK, account.token))
        .values()
        .flatMap { entry -> entry["changes"].values().map { it["field"].asString() } }
        .toSet()

    // un cambio como admin: el historial y la auditoría solo listan campos en un UPDATE (un CREATE no tiene cambios)
    private fun change(
        objectName: String,
        fixture: Fixture,
        changes: Map<String, Any?>
    ) {
        send("PUT", "/api/objects/$objectName/records/${fixture.id}", mapOf("attributes" to fixture.attributes + changes), HttpStatus.OK)
    }

    @Test
    fun `los nombres y el documento de una persona no llegan a quien no los lee`() {
        val kind = uniqueName("DOC-")
        val person =
            fixture(
                "person",
                mapOf(
                    "given_names" to "Persona",
                    "surnames" to "Ejemplo Uno",
                    "id_document_type" to kind,
                    "id_document_number" to "00000000"
                )
            )
        change("person", person, mapOf("given_names" to "Persona Corregida", "surnames" to "Ejemplo Uno Bis", "id_document_number" to "00000001"))
        val readOnly = userWithRole("READ_ONLY")
        val manager = userWithRole("PROJECT_MANAGER")
        val officer = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val restricted = setOf("given_names", "surnames", "id_document_number")

        for (view in listOf(record("person", person.id, readOnly), listed("person", "id_document_type" to kind, readOnly))) {
            assertTrue(restricted.none { view.has(it) }, view.toString())
            assertEquals(kind, view["id_document_type"].asString())
        }
        assertTrue(historyFields("person", person.id, readOnly).intersect(restricted).isEmpty())
        // PROJECT_MANAGER lee los nombres pero no el documento
        val forManager = record("person", person.id, manager)
        assertEquals("Persona Corregida", forManager["given_names"].asString())
        assertFalse(forManager.has("id_document_number"))
        assertEquals(setOf("given_names", "surnames"), historyFields("person", person.id, manager))
        // COMMUNITY_RELATIONS_OFFICER lee los tres
        assertEquals("00000001", record("person", person.id, officer)["id_document_number"].asString())
        assertEquals(restricted, historyFields("person", person.id, officer))
    }

    @Test
    fun `AUDITOR lee todo el tenant y la auditoria pero no los campos restringidos`() {
        val person = fixture("person", mapOf("given_names" to "Persona", "surnames" to "Ejemplo Dos", "preferred_language" to "Español"))
        change("person", person, mapOf("given_names" to "Persona Dos", "preferred_language" to "Quechua"))
        val auditor = userWithRole("AUDITOR")
        assertFalse(record("person", person.id, auditor).has("given_names"))
        val audit = tree(send("GET", "/api/audit?objectName=person&recordId=${person.id}", null, HttpStatus.OK, auditor.token))
        val fields = audit.values().flatMap { entry -> entry["changes"].values().map { it["field"].asString() } }
        assertEquals(listOf("preferred_language"), fields)
    }

    @Test
    fun `los datos de los asistentes solo los ven quienes los capturan`() {
        val participant =
            fixture(
                "engagement_participant",
                mapOf(
                    "participant_name" to "Persona Ejemplo 3",
                    "phone" to "000000000",
                    "email" to "asistente@sgspe.test"
                )
            )
        val analyst = userWithRole("ANALYST")
        val fieldAgent = userWithRole("FIELD_AGENT")
        val forAnalyst = record("engagement_participant", participant.id, analyst)
        assertTrue(listOf("participant_name", "phone", "email").none { forAnalyst.has(it) }, forAnalyst.toString())
        assertEquals("Persona Ejemplo 3", record("engagement_participant", participant.id, fieldAgent)["participant_name"].asString())
    }

    @Test
    fun `la partida del predio y el titular persona solo los ven SOCIAL_MANAGER, COMMUNITY_RELATIONS_OFFICER y GIS_ANALYST`() {
        val parcel = fixture("land_parcel", mapOf("registry_record" to "PARTIDA-DE-PRUEBA"))
        val person = fixture("person")
        val right = fixture("land_right", mapOf("land_parcel" to parcel.id, "holder_person" to person.id, "holder_organization" to null))
        val readOnly = userWithRole("READ_ONLY")
        val gis = userWithRole("GIS_ANALYST")
        assertFalse(record("land_parcel", parcel.id, readOnly).has("registry_record"))
        assertFalse(record("land_right", right.id, readOnly).has("holder_person"))
        assertEquals("PARTIDA-DE-PRUEBA", record("land_parcel", parcel.id, gis)["registry_record"].asString())
        assertEquals(person.id, record("land_right", right.id, gis)["holder_person"].asString())
    }

    @Test
    fun `la religion y el proceso electoral del perfil solo los ven y escriben SOCIAL_MANAGER y STAKEHOLDER_MANAGER`() {
        val profile = fixture("locality_profile", mapOf("religion" to "Texto de prueba", "electoral_process" to "Texto de prueba"))
        val officer = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val social = userWithRole("SOCIAL_MANAGER")
        assertFalse(record("locality_profile", profile.id, officer).has("religion"))
        assertTrue(record("locality_profile", profile.id, social).has("religion"))
        // COMMUNITY_RELATIONS_OFFICER crea perfiles (CRU) pero no escribe la religión: 400 que nombra el campo
        val problem =
            tree(
                send(
                    "POST",
                    "/api/objects/locality_profile/records",
                    mapOf("attributes" to sample("locality_profile") + mapOf("religion" to "x")),
                    HttpStatus.BAD_REQUEST,
                    officer.token
                )
            )
        assertEquals("religion", problem["errors"][0]["field"].asString())
    }

    @Test
    fun `la postura del grupo de interes no llega a quien no la lee aunque este vacia`() {
        val stakeholder = adminFixture("stakeholder")
        val fieldAgent = userWithRole("FIELD_AGENT")
        val manager = userWithRole("STAKEHOLDER_MANAGER")
        assertFalse(record("stakeholder", stakeholder.id, fieldAgent).has("stance"))
        assertTrue(record("stakeholder", stakeholder.id, manager).has("stance"))
    }

    @Test
    fun `con dos roles gana el mas permisivo`() {
        // security.md §5.4: un usuario con FIELD_AGENT y STAKEHOLDER_MANAGER lee la postura que FIELD_AGENT solo no lee
        val stakeholder = adminFixture("stakeholder")
        val email = "${uniqueName("dos-roles")}@sgspe.test"
        send(
            "POST",
            "/api/users",
            mapOf("email" to email, "displayName" to "Usuario de prueba", "password" to PASSWORD, "roles" to listOf("FIELD_AGENT", "STAKEHOLDER_MANAGER")),
            HttpStatus.CREATED
        )
        val both = Account(bearer(email, PASSWORD), email)
        assertTrue(record("stakeholder", stakeholder.id, both).has("stance"))
    }

    @Test
    fun `u deja cambiar el registro sin escribir sus campos de negocio`() {
        // FIELD_AGENT tiene `u` sobre stakeholder: puede aplicar transiciones y vincular, no editar (security.md §7.1)
        val stakeholder = adminFixture("stakeholder")
        val fieldAgent = member("FIELD_AGENT")
        assertEquals(
            HttpStatus.OK,
            exchange("PUT", "/api/objects/stakeholder/records/${stakeholder.id}", mapOf("attributes" to emptyMap<String, Any>()), fieldAgent.token).status
        )
        val problem =
            tree(
                send(
                    "PUT",
                    "/api/objects/stakeholder/records/${stakeholder.id}",
                    mapOf("attributes" to mapOf("profile_summary" to "editado")),
                    HttpStatus.BAD_REQUEST,
                    fieldAgent.token
                )
            )
        assertEquals("profile_summary", problem["errors"][0]["field"].asString())
    }

    @Test
    fun `roles json restringe en wasichai los campos de seccion 7_9 para cada rol que no los lee`() {
        val roles = tree(send("GET", "/api/roles", null, HttpStatus.OK)).values().associateBy { it["name"].asString() }
        for (entry in Model.restrictedFields) {
            @Suppress("UNCHECKED_CAST")
            val readers = entry["readers"] as List<String>
            @Suppress("UNCHECKED_CAST")
            for (field in entry["fields"] as List<String>) {
                for (role in Model.roles.keys - readers.toSet()) {
                    val rule =
                        roles.getValue(role)["fieldPermissions"].values().singleOrNull {
                            it["objectName"].asString() == entry["object"] &&
                                it["fieldName"].asString() == field
                        }
                    assertTrue(rule != null && !rule["read"].asBoolean() && !rule["write"].asBoolean(), "$role ${entry["object"]}.$field: $rule")
                }
            }
        }
    }
}
