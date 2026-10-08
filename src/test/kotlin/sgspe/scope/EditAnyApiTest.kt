package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Edición de lo propio» (RQ-SEC-008, ASM-069): sin EDIT_ANY no se cambia, transiciona, vincula ni borra
// lo ajeno; con EDIT_ANY sí; FIELD_AGENT agrega un participante a una interacción ajena y sus conteos se actualizan, y
// vincula su captura a un grupo de interés que registró otra persona
class EditAnyApiTest : DomainApiTest() {
    @Test
    fun `sin EDIT_ANY lo ajeno no se cambia, mueve, vincula ni borra, y lo propio si`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = engagement(project, token = officer.token, attributes = mapOf("scheduled_at" to "2026-06-01T15:00:00Z"))
        val denied = edit("engagement", foreign, mapOf("place" to "Local comunal"), agent.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        // P15: el código va en errors[0].message
        assertEquals("NOT_OWN_RECORD", tree(denied.body)["errors"][0]["message"].asString())
        assertEquals(HttpStatus.FORBIDDEN, transition("engagement", foreign, "schedule", agent.token).status)
        assertEquals(HttpStatus.FORBIDDEN, link("engagement", foreign, "engagement_stakeholder", stakeholder(project), agent.token).status)
        val foreignParticipant = participant(foreign, officer.token)
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/engagement_participant/records/$foreignParticipant", null, agent.token).status)
        val own = engagement(project, token = agent.token, attributes = mapOf("scheduled_at" to "2026-06-01T15:00:00Z"))
        assertEquals(HttpStatus.OK, edit("engagement", own, mapOf("place" to "Local comunal"), agent.token).status)
        assertEquals(HttpStatus.OK, transition("engagement", own, "schedule", agent.token).status)
        assertEquals(HttpStatus.OK, edit("engagement", foreign, mapOf("place" to "Plaza"), officer.token).status)
    }

    @Test
    fun `FIELD_AGENT agrega un participante a una interaccion ajena y los conteos se actualizan`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = engagement(project, token = officer.token)
        participant(foreign, agent.token, mapOf("represented_people_count" to 12, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        val counts = record("engagement", foreign)["attributes"]
        assertEquals(12, counts["attendees_total_count"].asInt())
        assertEquals(12, counts["attendees_female_count"].asInt())
        assertEquals(12, counts["attendees_direct_area_count"].asInt())
    }

    // P11: la escritura de los conteos la hace quien llama, con la marca, en una interacción que no creó. EditAnyGuard no
    // la bloquea ni al cambiar ni al borrar el participante propio (si la bloqueara, los conteos quedarían viejos hasta JOB-02)
    @Test
    fun `FIELD_AGENT cambia y borra su participante en una interaccion ajena y los conteos lo siguen`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = engagement(project, token = officer.token)
        val own = participant(foreign, agent.token, mapOf("represented_people_count" to 12, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        participant(foreign, officer.token, mapOf("represented_people_count" to 3, "sex" to "MASCULINO", "participant_scope" to "AIDS"))
        assertEquals(15, record("engagement", foreign)["attributes"]["attendees_total_count"].asInt())
        val changed = edit("engagement_participant", own, mapOf("represented_people_count" to 5), agent.token)
        assertEquals(HttpStatus.OK, changed.status, changed.body)
        val afterChange = record("engagement", foreign)["attributes"]
        assertEquals(8, afterChange["attendees_total_count"].asInt())
        assertEquals(5, afterChange["attendees_female_count"].asInt())
        val deleted = exchange("DELETE", "/api/objects/engagement_participant/records/$own", null, agent.token)
        assertEquals(HttpStatus.NO_CONTENT, deleted.status, deleted.body)
        val afterDelete = record("engagement", foreign)["attributes"]
        assertEquals(3, afterDelete["attendees_total_count"].asInt())
        assertEquals(0, afterDelete["attendees_female_count"].asInt())
    }

    // P11 con geometría: la interacción ajena lleva geometries.location (la captura rápida la manda). WriteView.changed no la
    // ve cambiar en la escritura de los conteos (la geometría no viaja en los atributos que ven las guardas), así que
    // EditAnyGuard no la bloquea y los conteos siguen al participante propio
    @Test
    fun `FIELD_AGENT cambia su participante en una interaccion ajena con ubicacion y los conteos lo siguen`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign =
            createWith(
                "engagement",
                mapOf("project" to project, "engagement_type" to "REUNION", "title" to "Reunión en la plaza"),
                geometries = mapOf("location" to point(-78.5695, -7.8995)),
                token = officer.token
            )
        val own = participant(foreign, agent.token, mapOf("represented_people_count" to 12, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        assertEquals(12, record("engagement", foreign)["attributes"]["attendees_total_count"].asInt())
        val changed = edit("engagement_participant", own, mapOf("represented_people_count" to 5), agent.token)
        assertEquals(HttpStatus.OK, changed.status, changed.body)
        assertEquals(5, record("engagement", foreign)["attributes"]["attendees_total_count"].asInt())
        val deleted = exchange("DELETE", "/api/objects/engagement_participant/records/$own", null, agent.token)
        assertEquals(HttpStatus.NO_CONTENT, deleted.status, deleted.body)
        assertEquals(0, record("engagement", foreign)["attributes"]["attendees_total_count"].asInt())
        // la ubicación de la interacción sobrevive a la escritura de los conteos
        val location = record("engagement", foreign)["geometries"]["location"]
        assertNotNull(location)
        assertEquals("Point", location["type"].asString())
    }

    @Test
    fun `sin EDIT_ANY no se desvincula lo ajeno, y lo propio si, desde cualquiera de los dos lados`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val theirs = stakeholder(project)
        val foreign = engagement(project, token = officer.token)
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", foreign, "engagement_stakeholder", theirs, officer.token).status)
        val fromEngagement = unlink("engagement", foreign, "engagement_stakeholder", theirs, agent.token)
        assertEquals(HttpStatus.FORBIDDEN, fromEngagement.status, fromEngagement.body)
        assertEquals("NOT_OWN_RECORD", tree(fromEngagement.body)["errors"][0]["message"].asString())
        val fromStakeholder = unlink("stakeholder", theirs, "engagement_stakeholder", foreign, agent.token)
        assertEquals(HttpStatus.FORBIDDEN, fromStakeholder.status, fromStakeholder.body)
        assertEquals("NOT_OWN_RECORD", tree(fromStakeholder.body)["errors"][0]["message"].asString())
        // lo propio: vincula y desvincula su captura
        val own = engagement(project, token = agent.token)
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", own, "engagement_stakeholder", theirs, agent.token).status)
        val removed = unlink("engagement", own, "engagement_stakeholder", theirs, agent.token)
        assertEquals(HttpStatus.NO_CONTENT, removed.status, removed.body)
        // la propietaria de la ajena sí puede (EDIT_ANY)
        assertEquals(HttpStatus.NO_CONTENT, unlink("engagement", foreign, "engagement_stakeholder", theirs, officer.token).status)
    }

    // el vínculo muchos a muchos se ve desde los dos extremos: EditAnyGuard juzga el de la interacción aunque quien llama
    // parta del grupo de interés (que no declara EDIT_ANY)
    @Test
    fun `desde el grupo de interes tampoco se vincula a una interaccion ajena, y a una propia si`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val theirs = stakeholder(project)
        val foreign = engagement(project, token = officer.token)
        val denied = link("stakeholder", theirs, "engagement_stakeholder", foreign, agent.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals("NOT_OWN_RECORD", tree(denied.body)["errors"][0]["message"].asString())
        val own = engagement(project, token = agent.token)
        val allowed = link("stakeholder", theirs, "engagement_stakeholder", own, agent.token)
        assertEquals(HttpStatus.NO_CONTENT, allowed.status, allowed.body)
    }

    // FIELD_AGENT no tiene DELETE sobre engagement (roles.json): el 403 de lo ajeno no distingue la guarda del permiso, y
    // el de lo propio lo confirma. quien sí borra lo ajeno es quien tiene DELETE y EDIT_ANY, que ningún rol reúne hoy
    @Test
    fun `FIELD_AGENT no borra una interaccion ajena`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = engagement(project, token = officer.token)
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/engagement/records/$foreign", null, agent.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/engagement/records/$foreign", null, agent.token).status)
    }

    @Test
    fun `FIELD_AGENT cambia y borra su observacion de campo y no la ajena`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de la oficial"), token = officer.token)
        val foreign = createRecord("field_observation", mapOf("field_visit" to visit, "observation" to "Puente caído"), token = officer.token)
        val changed = edit("field_observation", foreign, mapOf("observation" to "Puente restaurado"), agent.token)
        assertEquals(HttpStatus.FORBIDDEN, changed.status, changed.body)
        assertEquals("NOT_OWN_RECORD", tree(changed.body)["errors"][0]["message"].asString())
        val denied = exchange("DELETE", "/api/objects/field_observation/records/$foreign", null, agent.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals("NOT_OWN_RECORD", tree(denied.body)["errors"][0]["message"].asString())
        val own = createRecord("field_observation", mapOf("field_visit" to visit, "observation" to "Camino en mal estado"), token = agent.token)
        assertEquals(HttpStatus.OK, edit("field_observation", own, mapOf("observation" to "Camino bloqueado"), agent.token).status)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/field_observation/records/$own", null, agent.token).status)
        // quien tiene EDIT_ANY cambia y borra la ajena
        assertEquals(HttpStatus.OK, edit("field_observation", foreign, mapOf("observation" to "Puente restaurado"), officer.token).status)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/field_observation/records/$foreign", null, officer.token).status)
    }

    @Test
    fun `FIELD_AGENT vincula su captura a un grupo de interes que registro otra persona`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val theirs = stakeholder(project)
        val own = engagement(project, token = agent.token)
        val response = link("engagement", own, "engagement_stakeholder", theirs, agent.token)
        assertEquals(HttpStatus.NO_CONTENT, response.status, response.body)
    }

    @Test
    fun `FIELD_AGENT mueve sus visitas y no las ajenas, y agrega observaciones a cualquiera`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val own = createRecord("field_visit", mapOf("project" to project, "title" to "Visita propia"), token = agent.token)
        val foreign = createRecord("field_visit", mapOf("project" to project, "title" to "Visita ajena"), token = officer.token)
        move("field_visit", own, "start", agent.token)
        assertEquals(HttpStatus.FORBIDDEN, transition("field_visit", foreign, "start", agent.token).status)
        createRecord("field_observation", mapOf("field_visit" to foreign, "observation" to "Camino en mal estado"), token = agent.token)
    }

    @Test
    fun `FIELD_AGENT no cambia un participante ajeno y si el suyo`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val engagement = engagement(project, token = officer.token)
        val foreign = participant(engagement, officer.token)
        assertEquals(HttpStatus.FORBIDDEN, edit("engagement_participant", foreign, mapOf("represented_group" to "Ronda"), agent.token).status)
        val own = participant(engagement, agent.token)
        assertEquals(HttpStatus.OK, edit("engagement_participant", own, mapOf("represented_group" to "Ronda"), agent.token).status)
    }

    private fun unlink(
        objectName: String,
        id: String,
        relationship: String,
        otherId: String,
        token: String
    ): Response = exchange("DELETE", "/api/objects/$objectName/records/$id/related/$relationship/$otherId", null, token)
}
