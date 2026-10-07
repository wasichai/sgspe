package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest
import java.nio.file.Files

// RF-TER-001 (security.md §4.1, paso 5): model/load_ubigeo.py contra la app, con un CSV corto de topónimos públicos;
// la jerarquía queda en parent_unit y se lee filtrando por el campo (UPS-16), y la segunda corrida no escribe
class UbigeoLoadApiTest : F2ApiTest() {
    @Test
    fun `carga departamento, provincia y distritos con su jerarquia y es idempotente`() {
        val csv = Files.createTempFile("ubigeo-", ".csv")
        try {
            Files.writeString(csv, "codigo,departamento,provincia,distrito\n130601,LA LIBERTAD,OTUZCO,OTUZCO\n130602,LA LIBERTAD,OTUZCO,AGALLPAMPA\n")
            val first = runScript(listOf("model/load_ubigeo.py", "--csv", csv.toString()))
            assertEquals(0, first.exit, first.output)
            val second = runScript(listOf("model/load_ubigeo.py", "--csv", csv.toString()))
            assertEquals(0, second.exit, second.output)
            assertTrue(second.output.contains("done: 0 created, 0 updated, 4 skipped"), second.output)
        } finally {
            Files.deleteIfExists(csv)
        }
        val province = tree(send("GET", "/api/objects/administrative_unit/records?ubigeo_code=130600", null, HttpStatus.OK))["content"].values().single()
        assertEquals("PROVINCIA", province["attributes"]["level"].asString())
        val districts = tree(send("GET", "/api/objects/administrative_unit/records?parent_unit=${province["id"].asString()}", null, HttpStatus.OK))["content"]
        assertEquals(setOf("130601", "130602"), districts.values().map { it["attributes"]["ubigeo_code"].asString() }.toSet())
    }
}
