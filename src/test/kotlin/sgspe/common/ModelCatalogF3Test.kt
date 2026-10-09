package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// lo que F3 lee del catálogo: los estados de un workflow en el orden de sus transiciones y las relaciones MANY_TO_MANY
class ModelCatalogF3Test {
    private val catalog =
        ModelCatalog.parse(
            """
            {"objects":[{"name":"evidence","fields":[]},{"name":"delivery","fields":[]}],
             "relationships":[{"name":"evidence_delivery","type":"MANY_TO_MANY","source":"evidence","target":"delivery"},
                              {"name":"delivery_commitment","source":"delivery","target":"commitment","fieldName":"commitment"}]}
            """.trimIndent(),
            """{"roles":[{"name":"APPROVER"}]}""",
            mapOf(
                "commitment" to
                    """
                    {"definition":{"transitions":[
                      {"name":"submit","from":"draft","to":"pending_approval","roles":["APPROVER"]},
                      {"name":"approve","from":"pending_approval","to":"approved","roles":["APPROVER"]},
                      {"name":"cancel_draft","from":"draft","to":"cancelled","roles":["APPROVER"]}]}}
                    """.trimIndent()
            )
        )

    @Test
    fun `los estados salen de las transiciones, sin repetir y en orden`() {
        assertEquals(listOf("draft", "pending_approval", "approved", "cancelled"), catalog.states("commitment"))
        assertEquals(emptyList<String>(), catalog.states("task"))
    }

    @Test
    fun `una relacion MANY_TO_MANY se encuentra por su nombre y una MANY_TO_ONE no`() {
        assertEquals("evidence", catalog.manyToMany("evidence_delivery")?.source)
        assertNull(catalog.manyToMany("delivery_commitment"))
    }
}
