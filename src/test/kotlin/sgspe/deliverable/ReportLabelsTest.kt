package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper

// #104: los rótulos que imprime el T1 (y el estado de la Tabla 4 del T2) salen en español, no como el valor crudo
class ReportLabelsTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `una opcion sale en forma de oracion con tildes`() {
        assertEquals("En separación", ReportLabels.option("EN SEPARACION"))
        assertEquals("Para monitoreo", ReportLabels.option("PARA MONITOREO"))
        assertEquals("ONG", ReportLabels.option("ONG"))
        assertEquals("Comunidad campesina", ReportLabels.option("COMUNIDAD CAMPESINA"))
        assertEquals("A favor", ReportLabels.option("A FAVOR"))
        assertEquals("Estratégico", ReportLabels.option("ESTRATEGICO"))
        assertEquals("Exploración inicial", ReportLabels.option("EXPLORACION INICIAL"))
        assertEquals("Negó", ReportLabels.option("NEGO"))
        assertEquals("Señales", ReportLabels.option("SEÑALES"))
        assertEquals("EPSG 32718", ReportLabels.option("EPSG 32718"))
    }

    @Test
    fun `un valor que no esta todo en mayusculas o es nulo sale igual`() {
        assertNull(ReportLabels.option(null))
        assertEquals("Puesto de salud", ReportLabels.option("Puesto de salud"))
        assertEquals("ESTRATEGICO y más", ReportLabels.option("ESTRATEGICO y más"))
    }

    @Test
    fun `una palabra desconocida sale en minusculas`() {
        assertEquals("Zzzz de salud", ReportLabels.option("ZZZZ DE SALUD"))
    }

    @Test
    fun `un estado sale con el rotulo de su workflow`() {
        assertEquals("Aprobado", ReportLabels.state("commitment", "approved"))
        assertEquals("Pendiente de aprobación", ReportLabels.state("commitment", "pending_approval"))
        assertEquals("inventado", ReportLabels.state("commitment", "inventado"))
        assertEquals("approved", ReportLabels.state("no_existe", "approved"))
        assertNull(ReportLabels.state("commitment", null))
    }

    // la grilla de un conteo por estado (ADR-0031, ASM-088): los estados del workflow en su orden declarado
    @Test
    fun `los estados de un workflow salen en su orden declarado y los de uno que no existe vacios`() {
        val states = ReportLabels.states("commitment")
        assertEquals(listOf("draft", "pending_approval", "approved"), states.take(3))
        assertEquals("cancelled", states.last())
        assertEquals(emptyList<String>(), ReportLabels.states("no_existe"))
    }

    @Test
    fun `un rol sale con el rotulo de roles json`() {
        assertEquals("Administrador de proyectos", ReportLabels.role("PROJECT_ADMIN"))
        val label = json.readTree(ClassPathResource("model/roles.json").inputStream).path("roles").first { it.path("name").asString() == "COMMITMENT_MANAGER" }
        assertEquals(label.path("label").asString(), ReportLabels.role("COMMITMENT_MANAGER"))
        assertEquals("ROL_X", ReportLabels.role("ROL_X"))
        assertNull(ReportLabels.role(null))
    }

    @Test
    fun `todas las palabras de todos los enums del modelo estan clasificadas una sola vez`() {
        val dictionary = json.readTree(ClassPathResource("sgspe/deliverable/option-words.json").inputStream)
        val lists =
            listOf(
                dictionary.path("acronyms").values().map { it.asString() },
                dictionary.path("accents").propertyNames().toList(),
                dictionary.path("plain").values().map { it.asString() }
            )
        val all = lists.flatten()
        assertEquals(all.toSet().size, all.size, "palabras repetidas: ${all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}")
        val words =
            json
                .readTree(ClassPathResource("model/model.json").inputStream)
                .path("enums")
                .values()
                .flatMap { values -> values.values().flatMap { it.asString().split(' ') } }
                .filter { word -> word.any { it.isLetter() } }
                .toSet()
        val missing = words - all.toSet()
        assertTrue(missing.isEmpty(), "palabras de enums sin clasificar en option-words.json: $missing")
    }
}
