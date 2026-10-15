package sgspe.deliverable

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRelation
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// decisión 6 del plan 8.2b: la base tiene los mismos bytes en cada armado (su SHA-256 va en el modelo congelado)
class BaseTemplateTest {
    @Test
    fun `la base tiene los mismos bytes en cada armado`() {
        val first = BaseTemplate.buildForTest()
        Thread.sleep(2100)
        val second = BaseTemplate.buildForTest()
        assertArrayEquals(first, second)
        assertArrayEquals(first, BaseTemplate.bytes)
    }

    @Test
    fun `los estilos requeridos se buscan por nombre sin distinguir mayusculas`() {
        val names =
            XWPFDocument(ByteArrayInputStream(BaseTemplate.bytes.let { toDocx(it) }))
                .style.styleList
                .mapNotNull { it.name?.getVal()?.lowercase() }
                .toSet()
        TemplateStyles.REQUIRED.forEach { assertTrue(it.lowercase() in names, it) }
        assertEquals(
            TemplateStyles.REQUIRED.size,
            TemplateStyles.REQUIRED
                .map { it.lowercase() }
                .toSet()
                .size
        )
    }

    private fun toDocx(template: ByteArray): ByteArray {
        val pkg = OPCPackage.open(ByteArrayInputStream(template))
        pkg.replaceContentType(XWPFRelation.TEMPLATE.contentType, XWPFRelation.DOCUMENT.contentType)
        return ByteArrayOutputStream().also { pkg.save(it) }.toByteArray()
    }
}
