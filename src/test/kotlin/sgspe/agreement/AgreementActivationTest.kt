package sgspe.agreement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// lo que exige llegar a active en WF-I (RF-AGR-001 a RF-AGR-003), sin base
class AgreementActivationTest {
    private val dated = mapOf("signed_date" to "2026-09-01", "effective_date" to "2026-09-15")

    private fun codes(
        attributes: Map<String, Any?>,
        parcels: Int = 0,
        assembly: String? = null
    ) = AgreementActivation.findings(attributes, parcels, assembly).map { it.code to it.field }

    @Test
    fun `un acta de acuerdos solo exige sus fechas`() {
        assertEquals(emptyList<Pair<String, String?>>(), codes(dated + ("agreement_type" to "ACTA DE ACUERDOS")))
        assertEquals(listOf("FIELD_REQUIRED" to "effective_date"), codes(mapOf("signed_date" to "2026-09-01", "agreement_type" to "CONVENIO")))
    }

    @Test
    fun `una servidumbre exige la comunidad titular y sus predios`() {
        val easement = dated + ("agreement_type" to "SERVIDUMBRE")
        assertEquals(listOf("FIELD_REQUIRED" to "community", "LAND_PARCEL_REQUIRED" to "agreement_land_parcel"), codes(easement))
        assertEquals(emptyList<Pair<String, String?>>(), codes(easement + ("community" to "c"), parcels = 2))
    }

    @Test
    fun `una autorizacion de asamblea apunta a una ASAMBLEA`() {
        val authorization = dated + ("agreement_type" to "AUTORIZACION DE ASAMBLEA")
        assertEquals(listOf("ASSEMBLY_REQUIRED" to "assembly_engagement"), codes(authorization, assembly = "REUNION"))
        assertEquals(listOf("ASSEMBLY_REQUIRED" to "assembly_engagement"), codes(authorization))
        assertEquals(emptyList<Pair<String, String?>>(), codes(authorization, assembly = "ASAMBLEA"))
    }
}
