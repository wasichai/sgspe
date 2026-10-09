package sgspe.prod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.Ordered
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

// RQ-SEC-016, RQ-SEC-001 y ADR-0020: la lógica de ProdGuard sobre un entorno armado a mano. ProdStartupTest prueba lo mismo
// con la app entera
class ProdGuardTest {
    private val secure =
        mapOf(
            "wasichai.seed.dev" to "false",
            "wasichai.security.jwt.secret" to "un-secreto-de-produccion-de-mas-de-32-bytes",
            "wasichai.security.jwt.ttl" to "PT4H",
            "wasichai.files.store" to "s3",
            "wasichai.files.s3.bucket" to "sgspe-evidencias"
        )

    private fun check(
        values: Map<String, String>,
        profiles: List<String> = listOf("prod")
    ) {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(MapPropertySource("prueba", values))
        environment.setActiveProfiles(*profiles.toTypedArray())
        ProdGuard().postProcessEnvironment(environment, SpringApplication())
    }

    private fun failure(values: Map<String, String>): String = assertThrows(IllegalStateException::class.java) { check(values) }.message!!

    @Test
    fun `una configuracion de produccion correcta pasa`() {
        check(secure)
    }

    @Test
    fun `un token de menos de 8 horas pasa, en cualquier notacion`() {
        check(secure + ("wasichai.security.jwt.ttl" to "PT7H59M"))
        check(secure + ("wasichai.security.jwt.ttl" to "7h"))
    }

    @Test
    fun `la semilla encendida falla y nombra la propiedad y la variable`() {
        val message = failure(secure + ("wasichai.seed.dev" to "true"))
        assertTrue(message.contains("wasichai.seed.dev") && message.contains("WASICHAI_SEED_DEV"), message)
    }

    @Test
    fun `el secreto de desarrollo falla y nombra la propiedad y la variable`() {
        val message = failure(secure + ("wasichai.security.jwt.secret" to ProdGuard.DEV_SECRET))
        assertTrue(message.contains("wasichai.security.jwt.secret") && message.contains("WASICHAI_JWT_SECRET"), message)
        assertTrue(!message.contains(ProdGuard.DEV_SECRET), "el mensaje no repite el secreto")
    }

    @Test
    fun `un secreto ausente o vacio falla`() {
        for (values in listOf(secure - "wasichai.security.jwt.secret", secure + ("wasichai.security.jwt.secret" to " "))) {
            val message = failure(values)
            assertTrue(message.contains("wasichai.security.jwt.secret") && message.contains("WASICHAI_JWT_SECRET"), message)
        }
    }

    @Test
    fun `un secreto sin resolver falla con el mismo mensaje y no con un error de binding`() {
        val message = failure(secure + ("wasichai.security.jwt.secret" to "\${WASICHAI_JWT_SECRET}"))
        assertTrue(message.contains("WASICHAI_JWT_SECRET"), message)
    }

    @Test
    fun `un token de 8 horas o mas, o ausente, falla y nombra la propiedad y la variable`() {
        for (ttl in listOf("PT8H", "PT8H1S", "P1D", "9h")) {
            val message = failure(secure + ("wasichai.security.jwt.ttl" to ttl))
            assertTrue(message.contains("wasichai.security.jwt.ttl") && message.contains("WASICHAI_SECURITY_JWT_TTL"), message)
        }
        assertTrue(failure(secure - "wasichai.security.jwt.ttl").contains("wasichai.security.jwt.ttl"))
    }

    @Test
    fun `varias causas se informan juntas`() {
        val message =
            failure(mapOf("wasichai.seed.dev" to "true", "wasichai.security.jwt.secret" to ProdGuard.DEV_SECRET, "wasichai.security.jwt.ttl" to "PT8H"))
        for (name in listOf("wasichai.seed.dev", "wasichai.security.jwt.secret", "wasichai.security.jwt.ttl")) {
            assertTrue(message.contains(name), "$name en $message")
        }
    }

    // ADR-0014: en producción las evidencias van a un bucket compatible con S3
    @Test
    fun `el almacenamiento local o un bucket vacio fallan y nombran la variable`() {
        for (values in listOf(secure - "wasichai.files.store", secure + ("wasichai.files.store" to "local"))) {
            val message = failure(values)
            assertTrue(message.contains("wasichai.files.store") && message.contains("WASICHAI_FILES_STORE=s3"), message)
        }
        for (values in listOf(secure - "wasichai.files.s3.bucket", secure + ("wasichai.files.s3.bucket" to " "))) {
            val message = failure(values)
            assertTrue(message.contains("wasichai.files.s3.bucket") && message.contains("WASICHAI_FILES_S3_BUCKET"), message)
        }
    }

    // la guarda es de producción: el desarrollo y las pruebas siguen con la semilla y el secreto por defecto
    @Test
    fun `sin el perfil prod no comprueba nada`() {
        check(mapOf("wasichai.seed.dev" to "true", "wasichai.security.jwt.secret" to ProdGuard.DEV_SECRET), listOf("dev"))
        check(emptyMap(), emptyList())
    }

    // la constante de la guarda es la misma que lleva application.yml como valor por defecto
    @Test
    fun `el secreto de desarrollo de la guarda es el de application yml`() {
        val dev = YamlPropertySourceLoader().load("dev", ClassPathResource("application.yml")).single()
        val declared = dev.getProperty("wasichai.security.jwt.secret").toString()
        assertEquals("\${WASICHAI_JWT_SECRET:${ProdGuard.DEV_SECRET}}", declared)
    }

    // corre después de ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE + 10), para ver el valor final de cada propiedad
    @Test
    fun `corre despues de cargar la configuracion`() {
        assertEquals(Ordered.LOWEST_PRECEDENCE, ProdGuard().order)
    }
}
