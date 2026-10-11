package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.ModelCatalog
import java.math.BigDecimal

// las reglas de datos del aporte y sus ítems (especificación de F5 §3.3, elecciones 7 y 11), sin base
class ContributionDataTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `los items cambian solo en requested`() {
        assertNull(ContributionData.itemsFrozen("requested"))
        for (state in listOf("approved", "procured", "delivered", "closed", "rejected", "cancelled", null)) {
            assertEquals("ITEMS_FROZEN" to "social_contribution", ContributionData.itemsFrozen(state)?.let { it.code to it.field }, "$state")
        }
        assertEquals(HttpStatus.CONFLICT, ContributionData.itemMoved().status)
        assertEquals("social_contribution", ContributionData.itemMoved().field)
    }

    @Test
    fun `fuera de requested los terminos aprobados no cambian y los demas campos si`() {
        val changed = setOf("currency", "occasion", "agreement")
        assertEquals(emptyList<String>(), ContributionData.frozenTerms("requested", changed).map { it.field })
        val frozen = ContributionData.frozenTerms("approved", changed)
        assertEquals(listOf("currency", "agreement"), frozen.map { it.field })
        assertTrue(frozen.all { it.code == "CONTRIBUTION_FROZEN" && it.status == HttpStatus.CONFLICT })
        assertEquals(emptyList<String>(), ContributionData.frozenTerms("approved", setOf("occasion", "beneficiaries_count")).map { it.field })
    }

    @Test
    fun `un conteo es un entero de 0 en adelante, vacio pasa y uno que no cambia no se juzga`() {
        fun count(value: Any?) =
            InvestmentCounts.nonNegativeInteger("beneficiaries_count", mapOf("beneficiaries_count" to value), setOf("beneficiaries_count"))?.code
        for (valid in listOf(null, "", 0, 40, "12.00")) assertNull(count(valid), "$valid")
        for (wrong in listOf(-1, "2.5", "muchos")) assertEquals("OUT_OF_RANGE", count(wrong), "$wrong")
        assertNull(InvestmentCounts.nonNegativeInteger("beneficiaries_count", mapOf("beneficiaries_count" to -1), setOf("occasion")))
        val finding = InvestmentCounts.nonNegativeInteger("beneficiaries_count", mapOf("beneficiaries_count" to -1), setOf("beneficiaries_count"))!!
        assertEquals(HttpStatus.BAD_REQUEST, finding.status)
        assertEquals("beneficiaries_count", finding.field)
    }

    @Test
    fun `el total es la suma de line_amount de los items`() {
        val items = listOf(mapOf("line_amount" to 300), mapOf("line_amount" to "150.50"), mapOf<String, Any?>("line_amount" to null))
        assertEquals(0, BigDecimal("450.5").compareTo(ContributionData.total(items)))
        assertEquals(0, BigDecimal.ZERO.compareTo(ContributionData.total(emptyList())))
    }

    // las tablas nombran campos, estados y transiciones por texto: si el modelo renombra uno, la regla dejaría de vigilarlo
    @Test
    fun `los nombres de Contributions existen en el modelo y en WF-P`() {
        val fields =
            catalog.objectOrNull(Contributions.CONTRIBUTION)!!.fields.keys +
                catalog.relationships.filter { it.source == Contributions.CONTRIBUTION }.mapNotNull { it.fieldName }
        assertEquals(emptyList<String>(), Contributions.FROZEN_TERMS.filter { it !in fields })
        assertTrue(Contributions.TOTAL in catalog.derivedFields(Contributions.CONTRIBUTION))
        val states = catalog.states(Contributions.CONTRIBUTION).toSet()
        assertEquals(emptySet<String>(), Contributions.COMMITTED + Contributions.EXECUTED + Contributions.REQUESTED - states)
        val transitions = catalog.transitions(Contributions.CONTRIBUTION).map { it.name }.toSet()
        assertEquals(
            emptySet<String>(),
            Contributions.APPROVALS + Contributions.CANCELS + Contributions.REGISTER_DELIVERY + Contributions.CLOSE - transitions
        )
        assertEquals(listOf("BIEN", "SERVICIO", "DINERO", "MANO DE OBRA"), catalog.enumOf(Contributions.ITEM, Contributions.ITEM_KIND))
    }
}
