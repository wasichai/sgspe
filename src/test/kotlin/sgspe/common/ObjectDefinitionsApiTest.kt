package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// la caché del esquema por la API (plan corto del 2026-10-11): con la caché caliente, una lista de una analista no vuelve a
// cargar las definiciones que lee SGSPE (project_assignment y el padre para el alcance, project para READ_ALL_PROJECTS).
// se cuentan las cargas de ObjectDefinitions.recent y no las consultas del log de SQL, que también trae las de wasichai
class ObjectDefinitionsApiTest : F4ApiTest() {
    @Autowired
    private lateinit var definitions: ObjectDefinitions

    @Test
    fun `la segunda lista de una analista no carga definiciones del alcance`() {
        val project = testProject()
        stakeholder(project)
        val analyst = member("ANALYST", project)
        val assessments = "/api/objects/stakeholder_assessment/records?size=50"
        // la primera calienta la caché: project, project_assignment y stakeholder
        send("GET", assessments, null, HttpStatus.OK, analyst.token)
        val before = definitions.misses()
        send("GET", assessments, null, HttpStatus.OK, analyst.token)
        send("GET", "/api/objects/stakeholder/records?size=1", null, HttpStatus.OK, analyst.token)
        assertEquals(before, definitions.misses())
    }
}
