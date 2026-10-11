package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.commitment.Commitments

// las reglas de la entrega de un aporte (especificación de F5 §3.5), puras
class ContributionDeliveryTest {
    @Test
    fun `la entrega cambia solo con el aporte en procured`() {
        assertNull(ContributionDelivery.frozen("procured"))
        for (state in listOf("requested", "approved", "delivered", "closed", "cancelled", null)) {
            assertEquals("DELIVERY_FROZEN" to "social_contribution", ContributionDelivery.frozen(state)?.let { it.code to it.field }, "$state")
        }
    }

    @Test
    fun `una segunda entrega y el monto entregado tienen su codigo y su campo`() {
        assertEquals(
            Triple("DELIVERY_EXISTS", "social_contribution", HttpStatus.CONFLICT),
            ContributionDelivery.exists().let { Triple(it.code, it.field, it.status) }
        )
        assertEquals(
            Triple("FIELD_NOT_ALLOWED", "delivered_amount", HttpStatus.BAD_REQUEST),
            ContributionDelivery.amountNotAllowed().let {
                Triple(it.code, it.field, it.status)
            }
        )
        assertEquals("attributes.delivered_amount", ContributionDelivery.amountNotAllowed("attributes.delivered_amount").field)
    }

    // sgspe.commitment no depende de sgspe.investment: nombra el aporte con su propia constante, que debe ser la misma
    @Test
    fun `el candado del aporte es el mismo en los dos paquetes`() {
        assertEquals(Contributions.CONTRIBUTION, Commitments.SOCIAL_CONTRIBUTION)
    }
}
