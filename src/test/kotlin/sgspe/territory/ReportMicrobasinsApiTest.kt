package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F6QueryApiTest
import kotlin.random.Random

// #80: la microcuenca de cada localidad de 4.2 de T1, como la plataforma por SpatialGate: la environmental_feature
// MICROCUENCA cuyo boundary cubre la location de la localidad. todo ficticio (RQ-SEC-018)
class ReportMicrobasinsApiTest : F6QueryApiTest() {
    @Autowired
    private lateinit var features: ReportMapFeatures

    // una franja propia lejos de las demás pruebas: la base es compartida
    private fun band(): Double = -(200_000.0 + Random.nextInt(0, 800) * 1_000.0)

    @Test
    fun `una localidad dentro de la microcuenca da su nombre y la de fuera no aparece`() {
        val y = band()
        val name = uniqueName("Microcuenca Ejemplo ")
        runBlocking { environmentalFeature(name, "MICROCUENCA", boundary = rect(0.0, y, 4_000.0, y + 4_000, multi = true)) }
        // otro tipo de elemento sobre la misma localidad no cuenta
        runBlocking { environmentalFeature(uniqueName("Zona Ejemplo "), "ZONA DE PROTECCION", boundary = rect(0.0, y, 4_000.0, y + 4_000, multi = true)) }
        val inside = localityWith("Caserío Ejemplo Dentro", location = at(2_000.0, y + 2_000))
        val outside = localityWith("Caserío Ejemplo Fuera", location = at(6_000.0, y + 2_000))
        val bare = localityWith("Caserío Ejemplo Sin Punto")
        val found = runBlocking { features.microbasins(organizationId(), listOf(inside, outside, bare)) }
        assertEquals(mapOf(inside to name), found)
        assertEquals(emptyMap<Any, Any>(), runBlocking { features.microbasins(organizationId(), emptyList()) })
    }

    @Test
    fun `con una persona en el contexto no corre`() {
        val org = organizationId()
        val locality = localityWith("Caserío Ejemplo", location = at(0.0, band()))
        assertThrows<IllegalStateException> { asUser(Account(token, "")) { features.microbasins(org, listOf(locality)) } }
    }
}
