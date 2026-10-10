package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// las tablas de las guardas nombran transiciones y campos por texto: si el modelo renombra uno, la guarda dejaría de vigilarlo
// en silencio. esta prueba lo vuelve un fallo de la compilación. los objetos que todavía no están en model.json (F5 y F7)
// se listan aparte y, cuando entren, pasan solos a la regla
class GuardTablesCatalogTest {
    private val catalog = ModelCatalog.fromClasspath()

    // objetos de las tablas que model.json aún no declara; la prueba exige que sigan sin declararse (si no, ya se verifican)
    private val notYetModeled = setOf("social_program", "ai_suggestion")

    @Test
    fun `los objetos pendientes siguen sin estar en el modelo`() {
        assertEquals(emptySet<String>(), notYetModeled.filter { it in catalog.objects }.toSet(), "quítelos de notYetModeled: ya se verifican")
    }

    @Test
    fun `cada transicion con motivo obligatorio existe en el workflow de su objeto`() {
        val missing =
            TransitionReasonGuard.REQUIRED
                .filterKeys { it !in notYetModeled }
                .flatMap { (objectName, names) -> names.filter { catalog.transition(objectName, it) == null }.map { "$objectName.$it" } }
        assertEquals(emptyList<String>(), missing)
        assertEquals(emptySet<String>(), TransitionReasonGuard.REQUIRED.keys - catalog.objects.keys - notYetModeled)
    }

    @Test
    fun `cada transicion de comando existe en el workflow de su objeto`() {
        val missing =
            CommandTransitionGuard.COMMANDS.flatMap { (objectName, commands) ->
                commands.keys.filter { catalog.transition(objectName, it) == null }.map { "$objectName.$it" }
            }
        assertEquals(emptyList<String>(), missing)
    }

    // los campos de un objeto: los declarados y los de sus relaciones MANY_TO_ONE (fieldName)
    private fun fieldsOf(objectName: String): Set<String> =
        catalog
            .objectOrNull(objectName)
            ?.fields
            .orEmpty()
            .keys +
            catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }

    // database-model.md §17: una tarea tiene exactamente uno de sus orígenes, en el orden de la ficha, y cada origen llega
    // con la fase de su objeto. desde F4, AUT-02, AUT-09 y AUT-11 crean tareas con una queja, una alerta o un riesgo de
    // origen: sin ellos en el grupo, ExactlyOneGuard rechazaría la tarea y la automatización terminaría FAILED
    @Test
    fun `el origen de una tarea es uno de los de su ficha que ya estan en el modelo`() {
        val card =
            listOf(
                "commitment",
                "grievance",
                "inquiry",
                "stakeholder_request",
                "social_risk",
                "early_warning",
                "engagement",
                "agreement",
                "social_program",
                "stakeholder",
                "deliverable"
            )
        assertEquals(listOf(card.filter { it in catalog.objects }), ExactlyOneGuard.GROUPS["task"])
    }

    @Test
    fun `cada grupo de exactamente uno nombra campos que existen en su objeto`() {
        assertEquals(emptySet<String>(), ExactlyOneGuard.GROUPS.keys - catalog.objects.keys)
        val missing =
            ExactlyOneGuard.GROUPS.flatMap { (objectName, groups) ->
                val fields = fieldsOf(objectName)
                groups.flatten().filter { it !in fields }.map { "$objectName.$it" }
            }
        assertEquals(emptyList<String>(), missing)
    }
}
