package sgspe.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// security.md §7.9 y §14, «Permisos de campo» (RQ-SEC-010): un campo restringido no llega al registro, a la lista, al
// historial ni a la auditoría de quien no lo lee, y escribirlo se rechaza nombrando el campo; quien lo lee lo ve. las
// exportaciones las prueban los planes 2.2 y 3.2 con sus endpoints
class FieldPermissionsApiTest : F3ApiTest() {
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

    // un usuario con varios roles a la vez (security.md §5.4)
    private fun userWithRoles(roles: List<String>): Account {
        val email = "${uniqueName("dos-roles")}@sgspe.test"
        send(
            "POST",
            "/api/users",
            mapOf("email" to email, "displayName" to "Usuario de prueba", "password" to PASSWORD, "roles" to roles),
            HttpStatus.CREATED
        )
        return Account(bearer(email, PASSWORD), email)
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
    fun `la religion y el proceso electoral no llegan a quien no los lee y escribir la religion se rechaza nombrando el campo`() {
        val profile = fixture("locality_profile", mapOf("religion" to "Texto de prueba", "electoral_process" to "Texto de prueba"))
        val officer = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val social = userWithRole("SOCIAL_MANAGER")
        val forOfficer = record("locality_profile", profile.id, officer)
        assertFalse(forOfficer.has("religion") || forOfficer.has("electoral_process"), forOfficer.toString())
        val forSocial = record("locality_profile", profile.id, social)
        assertEquals("Texto de prueba", forSocial["religion"].asString())
        assertEquals("Texto de prueba", forSocial["electoral_process"].asString())
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
    fun `los receptores de una entrega y la cita de un candidato solo los ven sus lectores`() {
        // delivery y commitment_candidate son apiOnly: los crea la plataforma, como harán los endpoints del plan 3.2
        val commitment = commitment(testProject(), owner = member("COMMUNITY_RELATIONS_OFFICER"), verifier = member("SOCIAL_MANAGER"))
        val delivery =
            platformCreate(
                "delivery",
                mapOf(
                    "commitment" to commitment,
                    "delivery_date" to "2026-11-20",
                    "conformity_given" to true,
                    "recipients" to "Persona Ejemplo 6 y Persona Ejemplo 7",
                    "conformity_by" to "Persona Ejemplo 6"
                )
            )
        val candidate =
            platformCreate(
                "commitment_candidate",
                mapOf(
                    "project" to testProject(),
                    "proposed_text" to "Arreglar el canal",
                    "ai_generated" to false,
                    "evidence_quote" to "[PERSONA_1] pidió el canal"
                )
            )
        val readOnly = userWithRole("READ_ONLY")
        val approver = userWithRole("APPROVER")
        val manager = userWithRole("COMMITMENT_MANAGER")
        val forReadOnly = record("delivery", delivery, readOnly)
        assertFalse(forReadOnly.has("recipients") || forReadOnly.has("conformity_by"), forReadOnly.toString())
        assertEquals("2026-11-20", forReadOnly["delivery_date"].asString())
        assertEquals("Persona Ejemplo 6", record("delivery", delivery, manager)["conformity_by"].asString())
        // la cita la leen quienes descargan evidencias restringidas (DOWNLOAD_RESTRICTED); APPROVER no
        assertFalse(record("commitment_candidate", candidate, approver).has("evidence_quote"))
        assertEquals("[PERSONA_1] pidió el canal", record("commitment_candidate", candidate, manager)["evidence_quote"].asString())
    }

    @Test
    fun `con dos roles gana el mas permisivo`() {
        // security.md §5.4: un usuario con FIELD_AGENT y STAKEHOLDER_MANAGER lee la postura que FIELD_AGENT solo no lee,
        // con el lector primero y con el lector al final: no gana ni el primer rol ni el último
        val stakeholder = adminFixture("stakeholder")
        for (roles in listOf(listOf("FIELD_AGENT", "STAKEHOLDER_MANAGER"), listOf("STAKEHOLDER_MANAGER", "FIELD_AGENT"))) {
            assertTrue(record("stakeholder", stakeholder.id, userWithRoles(roles)).has("stance"), roles.toString())
        }
    }

    @Test
    fun `dos roles que no leen un campo no lo suman`() {
        // FIELD_AGENT y PROJECT_MANAGER no leen la postura ni el documento; los nombres sí los lee cada uno
        val stakeholder = adminFixture("stakeholder")
        val person = fixture("person", mapOf("given_names" to "Persona", "surnames" to "Ejemplo Cinco", "id_document_number" to "00000005"))
        for (roles in listOf(listOf("FIELD_AGENT", "PROJECT_MANAGER"), listOf("PROJECT_MANAGER", "FIELD_AGENT"))) {
            val account = userWithRoles(roles)
            assertFalse(record("stakeholder", stakeholder.id, account).has("stance"), roles.toString())
            val forPerson = record("person", person.id, account)
            assertFalse(forPerson.has("id_document_number"), "$roles $forPerson")
            assertEquals("Persona", forPerson["given_names"].asString(), roles.toString())
        }
    }

    @Test
    fun `u deja cambiar el registro sin escribir sus campos de negocio`() {
        // FIELD_AGENT tiene `u` sobre stakeholder: puede aplicar transiciones y vincular, no editar (security.md §7.1)
        val stakeholder = adminFixture("stakeholder")
        change("stakeholder", stakeholder, mapOf("profile_summary" to "resumen original", "inclusion_reason" to "motivo original"))
        val fieldAgent = member("FIELD_AGENT")
        val path = "/api/objects/stakeholder/records/${stakeholder.id}"
        val before = attributesOf(tree(send("GET", path, null, HttpStatus.OK)))
        assertEquals("resumen original", before["profile_summary"])
        assertEquals("motivo original", before["inclusion_reason"])

        // un PUT sin campos de negocio responde 200 y no anula lo que no manda
        assertEquals(HttpStatus.OK, exchange("PUT", path, mapOf("attributes" to emptyMap<String, Any>()), fieldAgent.token).status)
        assertEquals(before, attributesOf(tree(send("GET", path, null, HttpStatus.OK))))

        // un PUT con un campo de negocio responde 400 que lo nombra y no cambia nada
        val problem =
            tree(
                send(
                    "PUT",
                    path,
                    mapOf("attributes" to mapOf("profile_summary" to "editado")),
                    HttpStatus.BAD_REQUEST,
                    fieldAgent.token
                )
            )
        assertEquals("profile_summary", problem["errors"][0]["field"].asString())
        assertEquals(before, attributesOf(tree(send("GET", path, null, HttpStatus.OK))))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `cada campo restringido de roles json falta en el registro y en la lista de quien no lo lee`() {
        // dirigida por restrictedFields: un no lector con READ sobre el objeto no recibe la clave ni por id ni en la lista,
        // y un lector sí. se salta, con aviso, lo apiOnly (el plan 2.2 prueba sus valores), lo derivado (la API genérica
        // no lo escribe) y la entrada que no tiene un no lector con READ
        val failures = mutableListOf<String>()
        val covered = mutableListOf<String>()
        val skipped = linkedMapOf<String, String>()
        for (entry in Model.restrictedFields) {
            val objectName = entry["object"] as String
            val fields = entry["fields"] as List<String>
            val label = "$objectName.${fields.joinToString(",")}"
            val obj = Model.objects.getValue(objectName)
            val modelFields = (obj["fields"] as List<Map<String, Any?>>).associateBy { it["name"] as String }
            val readers = entry["readers"] as List<String>

            fun reads(role: String) = "READ" in Model.roles.getValue(role)[objectName].orEmpty() || "READ" in Model.roles.getValue(role)["*"].orEmpty()
            val nonReaders = (Model.roles.keys - readers.toSet()).filter(::reads)
            val nonReader = nonReaders.singleOrNull { it == "READ_ONLY" } ?: nonReaders.firstOrNull()
            val reader = readers.firstOrNull(::reads)
            when {
                obj["apiOnly"] == true -> skipped[label] = "el objeto es apiOnly"
                fields.any { modelFields[it]?.get("derived") == true } -> skipped[label] = "el campo es derivado"
                nonReader == null -> skipped[label] = "ningún rol sin permiso del campo lee el objeto"
                reader == null -> failures += "$label: ningún lector del campo lee el objeto"
                else ->
                    try {
                        val relations =
                            Model.relationships
                                .filter {
                                    it["source"] == objectName && it["fieldName"] != null
                                }.associateBy { it["fieldName"] as String }
                        val restricted =
                            Model.restrictedFields
                                .filter { it["object"] == objectName }
                                .flatMap { it["fields"] as List<String> }
                                .toSet()
                        val values = linkedMapOf<String, Any?>()
                        for (field in fields) {
                            val type = modelFields[field]?.get("type")
                            values[field] =
                                when {
                                    field in relations -> fixture(relations.getValue(field)["target"] as String).id
                                    type == "TEXT" || type == "LONG_TEXT" -> "Valor restringido de $field"
                                    type == "EMAIL" -> "restringido@sgspe.test"
                                    else -> error("sin valor de prueba para $label ($type)")
                                }
                        }
                        // quien escribe una relación restringida deja en null las otras de "exactamente uno de"
                        if (fields.any { it in relations }) EXACTLY_ONE[objectName].orEmpty().filter { it !in fields }.forEach { values[it] = null }
                        // un valor único en un texto legible para filtrar la lista; si no hay, una relación del registro
                        val textField =
                            modelFields.values.firstOrNull { it["type"] == "TEXT" && it["derived"] != true && it["name"] !in restricted }?.get(
                                "name"
                            ) as String?
                        if (textField != null) values[textField] = uniqueName("lista-")
                        val created = fixture(objectName, values)
                        val filter =
                            if (textField != null) {
                                textField to values.getValue(textField) as String
                            } else {
                                val relation = relations.keys.first { it !in restricted && created.attributes[it] != null }
                                relation to created.attributes.getValue(relation) as String
                            }
                        val byId = record(objectName, created.id, userWithRole(nonReader))
                        val inList = listed(objectName, filter, userWithRole(nonReader))
                        val forReader = record(objectName, created.id, userWithRole(reader))
                        for (field in fields) {
                            if (byId.has(field)) failures += "$label: $nonReader recibe $field por id"
                            if (inList.has(field)) failures += "$label: $nonReader recibe $field en la lista"
                            if (!forReader.has(field) || forReader[field].isNull) failures += "$label: $reader no recibe $field"
                        }
                        covered += "$label (no lector $nonReader, lector $reader, filtro ${filter.first})"
                    } catch (e: Exception) {
                        failures += "$label: ${e.message?.take(300)}"
                    } catch (e: AssertionError) {
                        failures += "$label: ${e.message?.take(300)}"
                    }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        // la partición queda fija: se saltan `stakeholder.stance` (derivado), `stakeholder_assessment` (apiOnly), cuyos valores
        // prueba el plan 2.2, y `delivery` y `commitment_candidate` (apiOnly), que cubre la prueba de los receptores y la
        // cita; las otras 8 entradas de roles.json se cubren. una entrada nueva que se salte, o una que deje de cubrirse,
        // rompe esta prueba en vez de pasar en silencio
        assertEquals(
            setOf(
                "stakeholder.stance",
                "stakeholder_assessment.stance,stance_reasons,concerns,expectations",
                "delivery.recipients,conformity_by",
                "commitment_candidate.evidence_quote"
            ),
            skipped.keys,
            "entradas saltadas: $skipped"
        )
        assertEquals(8, covered.size, "entradas cubiertas: $covered")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `roles json restringe en wasichai los campos de seccion 7_9 para cada rol que no los lee`() {
        val roles = tree(send("GET", "/api/roles", null, HttpStatus.OK)).values().associateBy { it["name"].asString() }
        for (entry in Model.restrictedFields) {
            val readers = (entry["readers"] as List<String>).toSet()
            for (field in entry["fields"] as List<String>) {
                for (role in Model.roles.keys) {
                    val rule =
                        roles.getValue(role)["fieldPermissions"].values().singleOrNull {
                            it["objectName"].asString() == entry["object"] &&
                                it["fieldName"].asString() == field
                        }
                    if (role in readers) {
                        // un lector no tiene regla o la tiene neutralizada: ninguna que le bloquee la lectura
                        assertTrue(rule == null || rule["read"].asBoolean(), "$role ${entry["object"]}.$field lee y wasichai se lo bloquea: $rule")
                    } else {
                        assertTrue(rule != null && !rule["read"].asBoolean() && !rule["write"].asBoolean(), "$role ${entry["object"]}.$field: $rule")
                    }
                }
            }
        }
    }
}
