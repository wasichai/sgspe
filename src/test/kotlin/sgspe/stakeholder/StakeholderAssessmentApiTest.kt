package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// POST /api/sgspe/stakeholders/{id}/assessments y su vista previa (api.md §4.3, RF-STK-005 a RF-STK-011): el ejemplo de
// api.md, la línea base, la advertencia de divergencia, la inmutabilidad, quién puede evaluar (GAP-024) y quién lee los
// campos restringidos de la evaluación y del grupo de interés (security.md §7.9)
class StakeholderAssessmentApiTest : DomainApiTest() {
    private val indicators =
        mapOf(
            "affect_environment" to 4,
            "affect_livelihood" to 3,
            "affect_property_status" to 2,
            "opportunity_work" to 4,
            "opportunity_development" to 3,
            "opportunity_benefits" to 2,
            "power_legal_action" to 2,
            "power_credibility" to 3,
            "power_permit_control" to 4,
            "power_action_scope" to 2,
            "power_organization_level" to 3,
            "power_convening_capacity" to 3,
            "power_network_membership" to 2,
            "power_group_mobilization" to 3,
            "power_other_pressure" to 2
        )

    // un grupo de interés identificado con lo que exige caracterizarlo: tipo, ámbito, motivo y responsable
    private fun complete(
        project: String,
        manager: Account
    ): String = stakeholder(project, profileFields(manager, "AUTORIDAD LOCAL"))

    // el mismo, ya caracterizado (profile aplicado)
    private fun profiled(
        project: String,
        manager: Account
    ): String {
        val stakeholder = complete(project, manager)
        move("stakeholder", stakeholder, "profile", manager.token)
        return stakeholder
    }

    private fun assess(
        stakeholder: String,
        attributes: Map<String, Any?>,
        token: String,
        why: String? = "evaluación de mayo",
        headers: Map<String, String> = emptyMap()
    ) = exchange(
        "POST",
        "/api/sgspe/stakeholders/$stakeholder/assessments",
        mapOf("attributes" to attributes),
        token,
        listOfNotNull(why?.let { REASON to reason(it) }).toMap() + headers
    )

    private fun warningCodes(body: String): List<String> = tree(body)["warnings"].values().map { it["code"].asString() }

    private fun assessments(stakeholder: String): Int =
        tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK))["totalElements"].asInt()

    @Test
    fun `el ejemplo de api md lleva al grupo de interes a assessed con sus valores vigentes`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        createRecord(
            "prioritization_rule",
            mapOf(
                "project" to project,
                "influence_group" to "PARA MONITOREO",
                "stance" to "EN CONTRA PERO ABIERTO A ESCUCHAR",
                "stakeholder_scope" to "AIDS",
                "information_priority" to "ALTA"
            )
        )
        val stakeholder = profiled(project, manager)
        val attributes =
            indicators +
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "stance" to "EN CONTRA PERO ABIERTO A ESCUCHAR",
                    "open_to_dialogue" to true,
                    "trust_score" to 2
                )
        val response = assess(stakeholder, attributes, manager.token)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        val assessment = body["assessment"]["attributes"]
        assertEquals(3.0, assessment["interest_index"].asDouble())
        assertEquals(2.67, assessment["power_index"].asDouble())
        assertEquals(5.67, assessment["influence_score"].asDouble())
        assertEquals("PARA MONITOREO", assessment["influence_group"].asString())
        assertEquals(manager.email, assessment["assessor_user_email"].asString())
        assertTrue(
            response.headers.location
                .toString()
                .endsWith(body["assessment"]["id"].asString())
        )
        val current = body["stakeholder"]
        assertEquals("assessed", current["state"].asString())
        assertEquals("ALTA", current["attributes"]["information_priority"].asString())
        assertEquals("EN CONTRA PERO ABIERTO A ESCUCHAR", current["attributes"]["stance"].asString())
        assertEquals("2026-05-12", current["attributes"]["last_assessment_date"].asString())
        assertEquals(2, current["attributes"]["trust_score"].asInt())
    }

    @Test
    fun `la segunda toma la primera como linea base y avisa la divergencia de postura`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val first =
            tree(
                assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token).body
            )
        val second =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENCUESTA", "stance" to "NEUTRAL"), manager.token).body)
        assertEquals(first["assessment"]["id"].asString(), second["assessment"]["attributes"]["baseline_assessment"].asString())
        assertTrue(second["assessment"]["attributes"]["stance_changed"].asBoolean())
        assertEquals(listOf("STANCE_DIVERGENCE", "NO_PRIORITY_RULE"), second["warnings"].values().map { it["code"].asString() })
        val before = record("stakeholder", stakeholder)["updatedAt"].asString()
        val backdated =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-04-01", "basis" to "ENTREVISTA", "stance" to "A FAVOR"), manager.token).body)
        assertTrue(backdated["warnings"].values().any { it["code"].asString() == "BACKDATED_ASSESSMENT" })
        val after = record("stakeholder", stakeholder)
        assertEquals("NEUTRAL", after["attributes"]["stance"].asString())
        assertEquals("2026-05-12", after["attributes"]["last_assessment_date"].asString())
        // la evaluación atrasada no escribe nada en el grupo de interés (P11)
        assertEquals(before, after["updatedAt"].asString())
    }

    @Test
    fun `sin motivo, con un indicador fuera de rango o con un ajuste sin justificar, 400 y nada se guarda`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val base = indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA")
        assertEquals(listOf("reason"), problemFields(assess(stakeholder, base, manager.token, why = null).body))
        assertEquals(listOf("attributes.power_credibility"), problemFields(assess(stakeholder, base + ("power_credibility" to 7), manager.token).body))
        assertEquals(listOf("attributes.override_reason"), problemFields(assess(stakeholder, base + ("influence_group" to "ESTRATEGICO"), manager.token).body))
        assertEquals("profiled", record("stakeholder", stakeholder)["state"].asString())
        assertEquals(0, assessments(stakeholder))
    }

    @Test
    fun `el ajuste manual justificado se marca, y un campo calculado o un If-Match viejo se rechazan`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val base = indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA")
        val overridden =
            assess(
                stakeholder,
                base + mapOf("group_overridden" to true, "influence_group" to "ESTRATEGICO", "override_reason" to "lidera la mesa de diálogo"),
                manager.token
            )
        assertEquals(HttpStatus.CREATED, overridden.status, overridden.body)
        val saved = tree(overridden.body)
        assertEquals("ESTRATEGICO", saved["assessment"]["attributes"]["influence_group"].asString())
        assertTrue(saved["assessment"]["attributes"]["group_overridden"].asBoolean())
        assertEquals("ESTRATEGICO", saved["stakeholder"]["attributes"]["influence_group"].asString())
        assertEquals(listOf("attributes.interest_index"), problemFields(assess(stakeholder, base + ("interest_index" to 5), manager.token).body))
        val stale = assess(stakeholder, base, manager.token, headers = mapOf("If-Match" to "\"2020-01-01T00:00:00Z\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        assertEquals("updatedAt", tree(stale.body)["errors"][0]["field"].asString())
        assertEquals(1, assessments(stakeholder))
    }

    @Test
    fun `una evaluacion sin postura ni confianza conserva los valores vigentes y la linea base salta las que no tienen postura`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val first =
            tree(
                assess(
                    stakeholder,
                    indicators + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA", "stance" to "EN CONTRA", "trust_score" to 2),
                    manager.token
                ).body
            )
        // sin postura ni confianza: el grupo de interés conserva las vigentes, y lo demás sí sale de esta evaluación
        val silent =
            tree(
                assess(
                    stakeholder,
                    indicators + mapOf("assessment_date" to "2026-05-05", "basis" to "ENCUESTA", "override_reason" to "sin ajuste"),
                    manager.token
                ).body
            )
        assertFalse(silent["assessment"]["attributes"]["stance_changed"].asBoolean())
        // la justificación no se guarda si el grupo no quedó ajustado
        assertTrue(silent["assessment"]["attributes"]["override_reason"] == null || silent["assessment"]["attributes"]["override_reason"].isNull)
        val kept = silent["stakeholder"]["attributes"]
        assertEquals("EN CONTRA", kept["stance"].asString())
        assertEquals(2, kept["trust_score"].asInt())
        assertEquals("2026-05-05", kept["last_assessment_date"].asString())
        // la línea base de la postura es la primera, no la que no la informó
        val third =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-10", "basis" to "ENTREVISTA", "stance" to "NEUTRAL"), manager.token).body)
        assertEquals(silent["assessment"]["id"].asString(), third["assessment"]["attributes"]["baseline_assessment"].asString())
        assertTrue(third["assessment"]["attributes"]["stance_changed"].asBoolean())
        assertTrue(third["warnings"].values().any { it["code"].asString() == "STANCE_DIVERGENCE" })
        assertEquals("NEUTRAL", third["stakeholder"]["attributes"]["stance"].asString())
        assertEquals(2, third["stakeholder"]["attributes"]["trust_score"].asInt())
        assertTrue(first["warnings"].values().none { it["code"].asString() == "STANCE_DIVERGENCE" })
        // P11: repetir lo vigente no escribe nada en el grupo de interés
        val before = record("stakeholder", stakeholder)
        val repeated =
            assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-10", "basis" to "ENTREVISTA", "stance" to "NEUTRAL"), manager.token)
        assertEquals(HttpStatus.CREATED, repeated.status, repeated.body)
        assertEquals(before["updatedAt"].asString(), record("stakeholder", stakeholder)["updatedAt"].asString())
    }

    @Test
    fun `una evaluacion atrasada que cambia la postura vigente recalcula la prioridad`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        listOf("EN CONTRA" to "ALTA", "NEUTRAL" to "MEDIA").forEach { (stance, priority) ->
            createRecord(
                "prioritization_rule",
                mapOf(
                    "project" to project,
                    "influence_group" to "PARA MONITOREO",
                    "stance" to stance,
                    "stakeholder_scope" to "AIDS",
                    "information_priority" to priority
                )
            )
        }
        val stakeholder = profiled(project, manager)
        assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token)
        val latest = tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-10", "basis" to "ENCUESTA"), manager.token).body)
        assertEquals("EN CONTRA", latest["stakeholder"]["attributes"]["stance"].asString())
        assertEquals("ALTA", latest["stakeholder"]["attributes"]["information_priority"].asString())
        // cargada después, pero fechada entre las dos: la última postura informada pasa a ser la suya
        val backdated =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-05", "basis" to "ENTREVISTA", "stance" to "NEUTRAL"), manager.token).body)
        assertTrue(backdated["warnings"].values().any { it["code"].asString() == "BACKDATED_ASSESSMENT" })
        val current = backdated["stakeholder"]["attributes"]
        assertEquals("NEUTRAL", current["stance"].asString())
        assertEquals("MEDIA", current["information_priority"].asString())
        assertEquals("2026-05-10", current["last_assessment_date"].asString())
    }

    // NO_PRIORITY_RULE de una atrasada habla de la prioridad que queda en el grupo de interés (grupo y postura vigentes), no
    // de la que saldría con los datos de la atrasada
    @Test
    fun `NO_PRIORITY_RULE de una evaluacion atrasada mira la prioridad vigente y no la de la atrasada`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        createRecord(
            "prioritization_rule",
            mapOf(
                "project" to project,
                "influence_group" to "PARA MONITOREO",
                "stance" to "EN CONTRA",
                "stakeholder_scope" to "AIDS",
                "information_priority" to "ALTA"
            )
        )
        val low = indicators.mapValues { 1 }
        // vigente con regla (PARA MONITOREO y EN CONTRA); la atrasada calcula otro grupo (VULNERABLE) que no tiene regla
        val withRule = profiled(project, manager)
        assess(withRule, indicators + mapOf("assessment_date" to "2026-05-10", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token)
        val backdatedLow = assess(withRule, low + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA"), manager.token)
        assertEquals(listOf("BACKDATED_ASSESSMENT"), warningCodes(backdatedLow.body))
        assertEquals("ALTA", record("stakeholder", withRule)["attributes"]["information_priority"].asString())
        // vigente sin regla (VULNERABLE); la atrasada calcula PARA MONITOREO y EN CONTRA, que sí la tiene
        val withoutRule = profiled(project, manager)
        assess(withoutRule, low + mapOf("assessment_date" to "2026-05-10", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token)
        val backdatedRule =
            assess(withoutRule, indicators + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token)
        assertEquals(listOf("BACKDATED_ASSESSMENT", "NO_PRIORITY_RULE"), warningCodes(backdatedRule.body))
    }

    @Test
    fun `una postura distinta fuera de la ventana no avisa divergencia`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        assess(stakeholder, indicators + mapOf("assessment_date" to "2026-03-01", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token)
        val later =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA", "stance" to "NEUTRAL"), manager.token).body)
        assertTrue(later["assessment"]["attributes"]["stance_changed"].asBoolean())
        assertEquals(listOf("NO_PRIORITY_RULE"), later["warnings"].values().map { it["code"].asString() })
    }

    @Test
    fun `un fallo dentro del comando revierte el profile y no deja evaluacion`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = complete(project, manager)
        val base = indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA", "influence_group" to "ESTRATEGICO")
        val response = assess(stakeholder, base, manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("identified", record("stakeholder", stakeholder)["state"].asString())
        assertEquals(0, assessments(stakeholder))
    }

    @Test
    fun `un parametro de consulta desconocido es 422 en el registro y en la vista previa`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val body = mapOf("attributes" to indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"))
        val headers = mapOf(REASON to reason("evaluación"))
        assertEquals(
            HttpStatus.UNPROCESSABLE_CONTENT,
            exchange("POST", "/api/sgspe/stakeholders/$stakeholder/assessments?dry=1", body, manager.token, headers).status
        )
        assertEquals(
            HttpStatus.UNPROCESSABLE_CONTENT,
            exchange("POST", "/api/sgspe/stakeholders/$stakeholder/assessments/preview?dry=1", body, manager.token).status
        )
        assertEquals(0, assessments(stakeholder))
    }

    @Test
    fun `un grupo de interes identificado se caracteriza antes, y uno retirado no se evalua`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val incomplete = stakeholder(project)
        val rejected = assess(incomplete, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token)
        assertEquals(HttpStatus.CONFLICT, rejected.status, rejected.body)
        // P15: profile pasa por Records.transition, así que StakeholderWriteGuard corre y su código llega
        assertEquals("PROFILE_INCOMPLETE", tree(rejected.body)["errors"][0]["message"].asString())
        assertEquals("identified", record("stakeholder", incomplete)["state"].asString())
        assertEquals(0, assessments(incomplete))
        val identified = complete(project, manager)
        val assessed = assess(identified, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token)
        assertEquals(HttpStatus.CREATED, assessed.status, assessed.body)
        assertEquals("assessed", record("stakeholder", identified)["state"].asString())
        val ready = profiled(project, manager)
        move("stakeholder", ready, "retire_profiled", manager.token, "ya no participa")
        val retired = assess(ready, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token)
        assertEquals(HttpStatus.CONFLICT, retired.status, retired.body)
        assertEquals("STATE_MISMATCH", tree(retired.body)["errors"][0]["message"].asString())
    }

    @Test
    fun `COMMUNITY_RELATIONS_OFFICER no evalua y la evaluacion no se cambia por ninguna puerta`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val stakeholder = profiled(project, manager)
        assertEquals(
            HttpStatus.FORBIDDEN,
            assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), officer.token).status
        )
        val id =
            tree(
                assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token).body
            )["assessment"]["id"].asString()
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("PUT", "/api/objects/stakeholder_assessment/records/$id", mapOf("attributes" to mapOf("trust_score" to 5))).status
        )
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange(
                "POST",
                "/api/objects/stakeholder_assessment/records",
                mapOf("attributes" to mapOf("stakeholder" to stakeholder))
            ).status
        )
    }

    @Test
    fun `la vista previa calcula sin guardar nada`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val preview =
            tree(send("POST", "/api/sgspe/stakeholders/$stakeholder/assessments/preview", mapOf("attributes" to indicators), HttpStatus.OK, manager.token))
        assertEquals("PARA MONITOREO", preview["computed"]["influence_group"].asString())
        assertFalse(preview["computed"]["group_overridden"].asBoolean())
        assertEquals(3.0, preview["computed"]["interest_index"].asDouble())
        assertEquals("profiled", record("stakeholder", stakeholder)["state"].asString())
        assertEquals(0, assessments(stakeholder))
    }

    @Test
    fun `Idempotency-Key se acepta y en F2 todavia no deduplica`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val headers = mapOf(REASON to reason("evaluación"), "Idempotency-Key" to "clave-de-prueba-1")
        val body = mapOf("attributes" to indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"))
        repeat(2) { send("POST", "/api/sgspe/stakeholders/$stakeholder/assessments", body, HttpStatus.CREATED, manager.token, headers) }
        assertEquals(2, assessments(stakeholder))
    }

    // security.md §7.9 (P8, I7): lo que 2.1 dejó a este plan. FieldPermissionsApiTest se salta stakeholder.stance (derivado) y
    // los campos restringidos de la evaluación (apiOnly), que solo se llenan por el endpoint
    @Test
    fun `quien no lee la postura ni los campos restringidos de la evaluacion no recibe esas claves`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val analyst = member("ANALYST", project)
        val agent = member("FIELD_AGENT", project)
        val stakeholder = profiled(project, manager)
        val restricted = listOf("stance", "stance_reasons", "concerns", "expectations")
        val attributes =
            InfluenceClassifier.INDICATORS.associateWith { 4 } +
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "stance" to "EN CONTRA",
                    "stance_reasons" to "Teme por el agua del caserío",
                    "concerns" to "Calidad del agua",
                    "expectations" to "Trabajo local"
                )
        val id = tree(assess(stakeholder, attributes, manager.token).body)["assessment"]["id"].asString()
        // quien los lee, los recibe
        val owned = record("stakeholder_assessment", id, manager.token)["attributes"]
        restricted.forEach { assertTrue(owned.has(it), "STAKEHOLDER_MANAGER debe leer $it") }
        assertEquals("Calidad del agua", owned["concerns"].asString())
        assertEquals("EN CONTRA", record("stakeholder", stakeholder, manager.token)["attributes"]["stance"].asString())
        // quien no, no recibe las claves por id ni en la lista, aunque el valor exista
        val byId = record("stakeholder_assessment", id, analyst.token)["attributes"]
        restricted.forEach { assertFalse(byId.has(it), "ANALYST no debe recibir $it por id") }
        assertEquals(4.0, byId["interest_index"].asDouble())
        val listed =
            tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK, analyst.token))["content"]
        assertEquals(1, listed.size())
        restricted.forEach { assertFalse(listed[0]["attributes"].has(it), "ANALYST no debe recibir $it en la lista") }
        // la postura del grupo de interés, que ya tiene valor, tampoco le llega a quien no la lee
        listOf(analyst, agent).forEach {
            assertFalse(record("stakeholder", stakeholder, it.token)["attributes"].has("stance"), "no debe recibir stance de stakeholder")
        }
        val stakeholders =
            tree(send("GET", "/api/objects/stakeholder/records?project=$project", null, HttpStatus.OK, agent.token))["content"]
        assertTrue(stakeholders.size() >= 1)
        stakeholders.values().forEach { assertFalse(it["attributes"].has("stance")) }
    }
}
