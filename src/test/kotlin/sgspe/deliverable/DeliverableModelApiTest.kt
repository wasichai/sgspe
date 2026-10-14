package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest
import wasichai.core.common.ConflictException

// el modelo de los entregables contra wasichai de verdad (plan 8.1; §4.2 de la especificación de F8): los flags, la API
// genérica cerrada sobre las versiones y las plantillas (403, también al admin), su inmutabilidad también como la
// plataforma (409), el borrador que nadie escribe por la API genérica, issue de comando, la lectura de secciones y versiones
// por el proyecto de su entregable, la plantilla del tenant, la regla «mismo proyecto» del entregable y su tarea de origen
class DeliverableModelApiTest : F8ApiTest() {
    @Test
    fun `los flags de los cuatro objetos`() {
        val flags =
            listOf("deliverable", "deliverable_section", "deliverable_version", "deliverable_template").associateWith { name ->
                val obj = tree(send("GET", "/api/objects/$name", null, HttpStatus.OK))
                Triple(obj["appendOnly"].asBoolean(), obj["apiOnly"].asBoolean(), obj["requiresReason"].asBoolean())
            }
        assertEquals(
            mapOf(
                "deliverable" to Triple(false, false, true),
                "deliverable_section" to Triple(false, false, false),
                "deliverable_version" to Triple(true, true, false),
                "deliverable_template" to Triple(true, true, false)
            ),
            flags
        )
    }

    // Review Focus 1 del plan 8.1: solo el código de sgspe da de alta versiones y plantillas
    @Test
    fun `la API generica no da de alta versiones ni plantillas, ni al admin`() {
        val project = testProject()
        val social = member("SOCIAL_MANAGER")
        val version = sample("deliverable_version") + ("deliverable" to deliverable(project))
        for ((name, attributes) in mapOf("deliverable_version" to version, "deliverable_template" to sample("deliverable_template"))) {
            for (caller in listOf(token, social.token)) {
                val created = exchange("POST", "/api/objects/$name/records", mapOf("attributes" to attributes), caller)
                assertEquals(HttpStatus.FORBIDDEN, created.status, "$name: ${created.body}")
            }
        }
    }

    @Test
    fun `una version y una plantilla no cambian ni se borran, tampoco como la plataforma`() {
        val version = deliverableVersion(deliverable(testProject()))
        val template = deliverableTemplate()
        assertThrows<ConflictException> { platformUpdate("deliverable_version", version, mapOf("revision" to "A")) }
        assertThrows<ConflictException> { platformDelete("deliverable_version", version) }
        assertThrows<ConflictException> { platformUpdate("deliverable_template", template, mapOf("name" to "Otra")) }
        assertThrows<ConflictException> { platformDelete("deliverable_template", template) }
        val stored = record("deliverable_version", version)["attributes"]
        assertEquals("0", stored["revision"].asString())
        assertEquals(sha256(MODEL), stored["model_sha256"].asString())
        // el descriptor del archivo trae el SHA-256 que calculó wasichai-files: el de la versión es el mismo
        assertEquals(stored["pdf_sha256"].asString(), stored["pdf"]["sha256"].asString())
    }

    // ASM-061 y ASM-133: el borrador lo escribe solo JOB-05, como la plataforma
    @Test
    fun `un entregable pide motivo y nadie escribe su borrador por la API generica`() {
        val project = testProject()
        val social = member("SOCIAL_MANAGER")
        val attributes = mapOf("project" to project, "document_code" to uniqueName("ENT-"), "title" to "Informe", "deliverable_type" to FIELD_REPORT)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("POST", "/api/objects/deliverable/records", mapOf("attributes" to attributes), social.token).status)
        val id = deliverable(project, creator = social)
        for (field in listOf("draft_generation_status" to "LISTO", "draft_revision" to "A", "draft_model_sha256" to "0".repeat(64))) {
            val edited = edit("deliverable", id, mapOf(field), social.token, withReason("borrador a mano"))
            assertEquals(HttpStatus.BAD_REQUEST, edited.status, edited.body)
            assertEquals(listOf(field.first), problemFields(edited.body), edited.body)
        }
        platformUpdate("deliverable", id, mapOf("draft_generation_status" to "EN COLA"))
        assertEquals("EN COLA", record("deliverable", id)["attributes"]["draft_generation_status"].asString())
    }

    // Review Focus 3 del plan 8.1: issue de WF-H solo llega por POST /api/sgspe/deliverables/{id}/issue (plan 8.2b)
    @Test
    fun `emitir por la API generica es 403 COMMAND_ONLY y el entregable sigue aprobado`() {
        val manager = member("SOCIAL_MANAGER")
        val id = approvedDeliverable(testProject(), manager)
        val refused = transition("deliverable", id, "issue", manager.token, "emisión a mano")
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("COMMAND_ONLY", code(refused))
        assertEquals("approved", state("deliverable", id))
    }

    @Test
    fun `las secciones y las versiones se leen por el proyecto de su entregable`() {
        val social = member("SOCIAL_MANAGER")
        val mine = deliverable(testProject())
        val theirs = deliverable(project())
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/deliverable_section/records/${section(mine)}", token = social.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/deliverable_section/records/${section(theirs)}", token = social.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/deliverable_version/records/${deliverableVersion(mine)}", token = social.token).status)
        assertEquals(
            HttpStatus.NOT_FOUND,
            exchange("GET", "/api/objects/deliverable_version/records/${deliverableVersion(theirs)}", token = social.token).status
        )
    }

    // security.md §7.8: la plantilla la leen los roles que leen deliverable, en todo el tenant; PROJECT_ADMIN y FIELD_AGENT no
    @Test
    fun `la plantilla es del tenant y la leen quienes leen entregables`() {
        val template = deliverableTemplate()
        val path = "/api/objects/deliverable_template/records/$template"
        assertEquals(HttpStatus.OK, exchange("GET", path, token = member("READ_ONLY", project()).token).status)
        assertEquals(HttpStatus.OK, exchange("GET", path, token = member("ANALYST").token).status)
        for (role in listOf("PROJECT_ADMIN", "FIELD_AGENT")) {
            assertEquals(HttpStatus.FORBIDDEN, exchange("GET", path, token = member(role).token).status, role)
        }
    }

    // Review Focus 4 del plan 8.1 (Decisión 4): un entregable no cita el estudio ni el área de otro proyecto, tampoco quien
    // lee todo el tenant
    @Test
    fun `un entregable no cita el estudio ni el area de otro proyecto`() {
        val other = project()
        for ((field, target) in listOf(
            "study" to fixture("study", mapOf("project" to other)).id,
            "area_of_influence" to fixture("area_of_influence", mapOf("project" to other)).id
        )) {
            val attributes =
                mapOf(
                    "project" to testProject(),
                    "document_code" to uniqueName("ENT-"),
                    "title" to "Informe",
                    "deliverable_type" to SOCIAL_DIAGNOSIS,
                    field to target
                )
            val refused = exchange("POST", "/api/objects/deliverable/records", mapOf("attributes" to attributes), headers = withReason("alta de prueba"))
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("PROJECT_MISMATCH", code(refused))
            assertEquals(listOf(field), problemFields(refused.body))
        }
        val own = fixture("study", mapOf("project" to testProject())).id
        assertEquals("draft", state("deliverable", deliverable(testProject(), SOCIAL_DIAGNOSIS, mapOf("study" to own))))
    }

    @Test
    fun `una tarea puede tener un entregable de origen, y solo uno`() {
        val id = deliverable(testProject())
        val task = createRecord("task", mapOf("project" to testProject(), "title" to "Revisar el borrador", "deliverable" to id))
        assertEquals(id, record("task", task)["attributes"]["deliverable"].asString())
        val both =
            exchange(
                "POST",
                "/api/objects/task/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "x", "deliverable" to id, "engagement" to engagement(testProject())))
            )
        assertEquals(HttpStatus.BAD_REQUEST, both.status, both.body)
        assertEquals("EXACTLY_ONE", code(both))
    }
}
