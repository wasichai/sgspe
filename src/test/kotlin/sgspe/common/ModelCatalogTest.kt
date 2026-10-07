package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// el catálogo lee del modelo lo que wasichai no guarda ("derived": true), las acciones, los ENUM, los roles declarados y
// los roles de cada transición
class ModelCatalogTest {
    private val catalog =
        ModelCatalog.parse(
            model =
                """
                {"enums": {"stakeholder_scope": ["AIDS", "AIIS"]},
                 "objects": [
                   {"name": "stakeholder", "actions": [{"name": "EXPORT", "label": "Exportar"}],
                    "fields": [{"name": "stance", "type": "ENUM", "enum": "stance", "derived": true},
                               {"name": "stakeholder_scope", "type": "ENUM", "enum": "stakeholder_scope"},
                               {"name": "inclusion_reason", "type": "LONG_TEXT"}]},
                   {"name": "engagement", "actions": [{"name": "EDIT_ANY", "label": "Editar lo ajeno"}], "fields": []}
                 ],
                 "relationships": [{"name": "stakeholder_project", "source": "stakeholder", "target": "project", "fieldName": "project"}]}
                """.trimIndent(),
            roles = """{"mappedToAdmin": ["SUPER_ADMIN"], "roles": [{"name": "STAKEHOLDER_MANAGER"}, {"name": "FIELD_AGENT"}]}""",
            workflows =
                mapOf(
                    "stakeholder" to
                        """
                        {"definition": {"transitions": [
                          {"name": "assess", "from": "profiled", "to": "assessed", "roles": ["STAKEHOLDER_MANAGER"]}]}}
                        """.trimIndent()
                )
        )

    @Test
    fun `los derivados salen de la clave derived`() {
        assertEquals(setOf("stance"), catalog.derivedFields("stakeholder"))
        assertEquals(emptySet<String>(), catalog.derivedFields("engagement"))
        assertEquals(emptySet<String>(), catalog.derivedFields("no_existe"))
    }

    @Test
    fun `objetos que declaran una accion, enums y relaciones`() {
        assertEquals(setOf("engagement"), catalog.objectsDeclaring("EDIT_ANY"))
        assertEquals(listOf("AIDS", "AIIS"), catalog.enumOf("stakeholder", "stakeholder_scope"))
        assertEquals("project", catalog.relationField("stakeholder", "project"))
        assertNull(catalog.relationField("stakeholder", "engagement"))
    }

    @Test
    fun `los roles declarados no incluyen los mapeados a ADMIN, y las transiciones traen sus roles`() {
        assertEquals(setOf("STAKEHOLDER_MANAGER", "FIELD_AGENT"), catalog.declaredRoles)
        assertEquals(listOf("STAKEHOLDER_MANAGER"), catalog.transition("stakeholder", "assess")?.roles)
        assertNull(catalog.transition("stakeholder", "profile"))
        assertTrue(catalog.transitions("engagement").isEmpty())
    }

    @Test
    fun `fromClasspath lee los archivos que empaqueta processResources`() {
        val packaged = ModelCatalog.fromClasspath()
        assertTrue("AUDITOR" in packaged.declaredRoles)
        assertTrue("stance" in packaged.derivedFields("stakeholder"))
        assertTrue("engagement" in packaged.objectsDeclaring("EDIT_ANY"))
        assertTrue(packaged.transition("stakeholder", "assess") != null)
    }

    @Test
    fun `un catalogo incompleto falla claro en lugar de quedar vacio`() {
        val roles = """{"roles": [{"name": "FIELD_AGENT"}]}"""
        val model = """{"objects": [{"name": "project", "fields": []}]}"""
        val workflows = mapOf("project" to """{"definition": {"transitions": []}}""")
        assertTrue(
            ModelCatalog
                .parse(model, roles, workflows)
                .requireComplete()
                .declaredRoles
                .isNotEmpty()
        )
        for (incomplete in listOf(
            ModelCatalog.parse("{}", roles, workflows),
            ModelCatalog.parse(model, "{}", workflows),
            ModelCatalog.parse(model, roles, emptyMap())
        )) {
            assertThrows(IllegalStateException::class.java) { incomplete.requireComplete() }
        }
    }

    @Test
    fun `un nombre repetido en el modelo falla en lugar de pisarse`() {
        val roles = """{"roles": []}"""
        val duplicatedObject = """{"objects": [{"name": "project", "fields": []}, {"name": "project", "fields": []}]}"""
        val duplicatedField =
            """{"objects": [{"name": "project", "fields": [{"name": "code", "type": "TEXT"}, {"name": "code", "type": "TEXT"}]}]}"""
        assertThrows(IllegalStateException::class.java) { ModelCatalog.parse(duplicatedObject, roles) }
        assertThrows(IllegalStateException::class.java) { ModelCatalog.parse(duplicatedField, roles) }
    }
}
