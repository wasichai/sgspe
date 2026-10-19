package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import java.time.LocalDate

// GET /api/sgspe/codes/next (decisión 6 de la revisión de UX): el siguiente código del objeto en el proyecto, contando solo los de
// ese proyecto y del año de Lima; un objeto sin prefijo es 400, un proyecto ajeno 404 y un parámetro desconocido 422
class NextCodesApiTest : F3DomainApiTest() {
    private val year = LocalDate.now(LIMA).year

    @Test
    fun `propone el siguiente codigo del proyecto y el anio`() {
        val project = project()
        val other = project()
        commitment(project, mapOf("code" to "COM-$year-004"))
        commitment(project, mapOf("code" to "COM-$year-011"))
        commitment(project, mapOf("code" to "COM-${year - 1}-090"))
        commitment(other, mapOf("code" to "COM-$year-050"))
        val propuesta = tree(send("GET", "/api/sgspe/codes/next?object=commitment&project=$project", null, HttpStatus.OK))
        assertEquals("COM-$year-012", propuesta["code"].asString())
        val vacia = tree(send("GET", "/api/sgspe/codes/next?object=agreement&project=$project", null, HttpStatus.OK))
        assertEquals("ACU-$year-001", vacia["code"].asString())
    }

    @Test
    fun `un objeto sin prefijo es 400, un proyecto que no lee 404 y otro parametro 422`() {
        val project = project()
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/codes/next?object=person&project=$project").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/codes/next?object=commitment&project=no-es-un-id").status)
        val ajeno = provisionTenant()
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/codes/next?object=commitment&project=$project", null, ajeno.token).status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/codes/next?object=commitment&project=$project&year=2020").status)
    }
}
