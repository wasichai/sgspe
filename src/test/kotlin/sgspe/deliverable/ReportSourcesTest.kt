package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog

// especificación de F8 §3.2 y deliverables.md §7.3 (RQ-REP-014): ningún tipo declara leer el documento de identidad, un
// teléfono ni un correo de una persona, y lo que declara existe en el modelo. ReportSources entrega solo lo declarado
class ReportSourcesTest {
    private val types: List<DeliverableType> = listOf(FieldReportType())
    private val catalog = ModelCatalog.fromClasspath()
    private val forbidden = Regex("document|phone|telefono|celular|email|correo|id_document")

    @Test
    fun `ningun tipo declara documento de identidad, telefono ni correo`() {
        for (type in types) {
            val offenders = type.reads.flatMap { (objectName, fields) -> fields.filter { forbidden.containsMatchIn(it) }.map { "$objectName.$it" } }
            assertEquals(emptyList<String>(), offenders, type.name)
        }
    }

    @Test
    fun `cada lectura declarada es un campo o una relacion del modelo`() {
        for (type in types) {
            for ((objectName, fields) in type.reads) {
                val obj = catalog.objectOrNull(objectName)
                assertTrue(obj != null, "$objectName no está en el modelo")
                val known = obj!!.fields.keys + catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }
                assertEquals(emptyList<String>(), (fields - known).sorted(), "$objectName en ${type.name}")
            }
        }
    }

    @Test
    fun `las secciones del tipo tienen claves unicas y los tipos del catalogo`() {
        for (type in types) {
            assertEquals(
                type.sections.size,
                type.sections
                    .map { it.key }
                    .toSet()
                    .size
            )
            assertTrue(type.sections.all { it.kind in catalog.enumValues("deliverable_section_kind") })
            assertTrue(type.name in catalog.enumValues("deliverable_type"))
        }
    }
}
