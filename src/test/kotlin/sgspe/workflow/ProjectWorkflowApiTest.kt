package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// WF-A (workflows.md): el ciclo completo, los 403 y 409 más representativos y el listado de cada rol. la matriz de las
// nueve transiciones (cada rol permitido, un rol denegado con su razón y un estado equivocado) está en WorkflowMatrixApiTest,
// incluidas `return_to_draft` y `close_suspended`, que solo ella recorre. los casos que dependen de ProjectWriteGuard y
// TransitionReasonGuard (`approve` sin working_crs o sin parte CLIENTE, `resume` verifica, doble llave, `stage` sin motivo y
// motivo obligatorio de `return_to_draft`, `suspend` y `cancel_*`) los agrega el plan 2.2 en ProjectWorkflowGuardsApiTest
class ProjectWorkflowApiTest : F2ApiTest() {
    // un proyecto que cumple las precondiciones de `active` (ASM-062), para que approve y resume pasen también con
    // ProjectWriteGuard
    private fun activatable(): String {
        val project = project(mapOf("working_crs" to "EPSG 32718"))
        createRecord("project_party", mapOf("project" to project, "organization" to fixture("organization").id, "party_role" to "CLIENTE"))
        return project
    }

    @Test
    fun `el proyecto nace en draft y recorre revision, activacion, suspension, reanudacion y cierre`() {
        val project = activatable()
        val admin = member("PROJECT_ADMIN", project)
        val manager = member("PROJECT_MANAGER", project)
        assertEquals("draft", state("project", project))

        move("project", project, "submit_for_review", manager.token)
        move("project", project, "approve", admin.token)
        assertEquals("active", state("project", project))
        move("project", project, "suspend", manager.token, "lluvias en la zona")
        move("project", project, "resume", manager.token)
        move("project", project, "close", admin.token)
        assertEquals("closed", state("project", project))
        assertEquals(emptyMap<String, Pair<Boolean, String?>>(), available("project", project, admin.token))

        val history = tree(send("GET", "/api/objects/project/records/$project/history", null, HttpStatus.OK)).values()
        assertTrue(history.any { it["reason"]?.asString() == "lluvias en la zona" }, history.toString())
    }

    @Test
    fun `un rol fuera de la transicion recibe 403 y desde otro estado responde 409`() {
        val project = activatable()
        val manager = member("PROJECT_MANAGER", project)
        val gis = member("GIS_ANALYST", project)
        val stakeholders = member("STAKEHOLDER_MANAGER", project)
        val admin = member("PROJECT_ADMIN", project)

        // GIS_ANALYST tiene `u` sobre project, pero ningún rol de WF-A
        assertEquals(HttpStatus.FORBIDDEN, transition("project", project, "submit_for_review", gis.token).status)
        // STAKEHOLDER_MANAGER solo lee el proyecto
        assertEquals(HttpStatus.FORBIDDEN, transition("project", project, "submit_for_review", stakeholders.token).status)
        // approve sale de in_review
        assertEquals(HttpStatus.CONFLICT, transition("project", project, "approve", admin.token).status)
        // cancelar es solo de PROJECT_ADMIN
        assertEquals(HttpStatus.FORBIDDEN, transition("project", project, "cancel_draft", manager.token, "duplicado").status)
        assertEquals(HttpStatus.NOT_FOUND, transition("project", project, "activate", manager.token).status)
        assertEquals("draft", state("project", project))
    }

    @Test
    fun `el listado explica a cada rol que puede y que no`() {
        val project = activatable()
        val manager = member("PROJECT_MANAGER", project)
        val analyst = member("ANALYST", project)

        val forManager = available("project", project, manager.token)
        assertEquals(setOf("submit_for_review", "cancel_draft"), forManager.keys)
        assertEquals(true to null, forManager["submit_for_review"])
        assertEquals(false to "requires role PROJECT_ADMIN", forManager["cancel_draft"])
        val forAnalyst = available("project", project, analyst.token)
        assertTrue(forAnalyst.isNotEmpty(), "el listado de ANALYST no puede venir vacío")
        assertTrue(forAnalyst.values.all { it == (false to "requires permission UPDATE") }, forAnalyst.toString())
    }

    @Test
    fun `PROJECT_ADMIN anula un borrador y un proyecto en revision`() {
        val admin = userWithRole("PROJECT_ADMIN")
        val draft = activatable()
        assign(admin, draft, "PROJECT_ADMIN")
        move("project", draft, "cancel_draft", admin.token, "registro duplicado")
        assertEquals("cancelled", state("project", draft))

        val inReview = activatable()
        assign(admin, inReview, "PROJECT_ADMIN")
        move("project", inReview, "submit_for_review", admin.token)
        move("project", inReview, "cancel_in_review", admin.token, "el prospecto se descartó")
        assertEquals("cancelled", state("project", inReview))
    }
}
