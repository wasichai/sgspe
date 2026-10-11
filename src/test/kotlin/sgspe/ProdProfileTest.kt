package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import wasichai.core.platform.JwtProperties
import wasichai.core.platform.WasichaiLoginProperties
import wasichai.core.platform.WasichaiOrganizationsProperties
import wasichai.core.platform.WasichaiPasswordProperties
import java.time.Duration

// RQ-SEC-016, RQ-SEC-001 y ADR-0020: el perfil de producción no tiene semilla ni secreto por defecto, acorta el token y
// escribe los logs en ECS. lee el yaml tal cual, sin levantar la app
class ProdProfileTest {
    private val prod = YamlPropertySourceLoader().load("prod", ClassPathResource("application-prod.yml")).single()

    private fun value(name: String): String? = prod.getProperty(name)?.toString()

    @Test
    fun `apaga la semilla de desarrollo`() {
        assertEquals("false", value("wasichai.seed.dev"))
    }

    @Test
    fun `exige el secreto JWT sin valor por defecto`() {
        assertEquals("\${WASICHAI_JWT_SECRET}", value("wasichai.security.jwt.secret"))
    }

    // ningún dato de conexión tiene valor por defecto: sin la variable la app no arranca, y nunca cae en el sgspe/sgspe de
    // desarrollo de application.yml. el puerto no es secreto: lleva el estándar de PostgreSQL, no el 5435 del túnel
    @Test
    fun `la base de datos no tiene valores por defecto`() {
        assertEquals("\${WASICHAI_DB_HOST}", value("wasichai.database.host"))
        assertEquals("\${WASICHAI_DB_NAME}", value("wasichai.database.name"))
        assertEquals("\${WASICHAI_DB_USERNAME}", value("wasichai.database.username"))
        assertEquals("\${WASICHAI_DB_PASSWORD}", value("wasichai.database.password"))
        assertEquals("\${WASICHAI_DB_PORT:5432}", value("wasichai.database.port"))
        for (name in listOf("host", "name", "username", "password")) {
            assertFalse(value("wasichai.database.$name")!!.contains(":"), name)
        }
    }

    @Test
    fun `el token dura menos que las 8 horas por defecto`() {
        val ttl = Duration.parse(value("wasichai.security.jwt.ttl"))
        assertTrue(ttl.isPositive && ttl < Duration.ofHours(8), "$ttl")
    }

    // sin claves estáticas rige la cadena por defecto del SDK (rol de instancia); un valor vacío aquí haría fallar a wasichai
    @Test
    fun `no declara claves de S3 ni servidor de correo de desarrollo`() {
        assertEquals(null, value("wasichai.files.s3.access-key"))
        assertEquals(null, value("wasichai.files.s3.secret-key"))
        assertEquals("\${SPRING_MAIL_HOST:}", value("spring.mail.host"))
    }

    // ADR-0014 (decisión D1): las evidencias van a un bucket compatible con S3, que el despliegue nombra; ProdGuard no deja
    // arrancar sin él
    @Test
    fun `las evidencias van a un bucket compatible con S3 sin valor por defecto`() {
        assertEquals("s3", value("wasichai.files.store"))
        assertEquals("\${WASICHAI_FILES_S3_BUCKET:}", value("wasichai.files.s3.bucket"))
    }

    // ADR-0015 y security.md §10.2 (regla 1): GeoServer en producción solo con las credenciales y los hosts del despliegue,
    // nunca los de desarrollo de application.yml ni los por defecto de wasichai. apagado si el despliegue no lo enciende: un
    // backend sin GeoServer funciona y el proxy WMS responde 503 (§7 de la especificación de F6)
    @Test
    fun `GeoServer no hereda credenciales ni hosts de desarrollo`() {
        assertEquals("\${WASICHAI_GIS_GEOSERVER_ENABLED:false}", value("wasichai.gis.geoserver.enabled"))
        val byVariable =
            mapOf(
                "url" to "URL",
                "username" to "USERNAME",
                "password" to "PASSWORD",
                "datastore.host" to "DATASTORE_HOST",
                "datastore.database" to "DATASTORE_DATABASE",
                "datastore.username" to "DATASTORE_USERNAME",
                "datastore.password" to "DATASTORE_PASSWORD"
            )
        for ((name, variable) in byVariable) {
            assertEquals("\${WASICHAI_GIS_GEOSERVER_$variable:}", value("wasichai.gis.geoserver.$name"), name)
        }
        assertEquals("\${WASICHAI_GIS_GEOSERVER_DATASTORE_PORT:5432}", value("wasichai.gis.geoserver.datastore.port"))
        assertEquals("sgspe", value("wasichai.gis.geoserver.workspace"))
    }

    // UPS-09 (WAS-ADR-0059): revocación de tokens, límite de intentos de login y política de contraseñas, apagados por
    // defecto en wasichai y encendidos aquí. se ligan a las clases reales de wasichai (con el mismo prefijo que ellas), así una
    // propiedad mal escrita falla en lugar de ignorarse. security.md §3.2 documenta los valores
    private val binder = Binder(ConfigurationPropertySources.from(prod).filterNotNull())

    private inline fun <reified T : Any> bound(prefix: String): T = binder.bind(prefix, Bindable.of(T::class.java)).get()

    // UPS-10 (WAS-ADR-0055): el ADMIN de un cliente no crea ni borra tenants; ligado a la clase de wasichai, como los de UPS-09
    @Test
    fun `separa el alta de tenants de la administracion de cada uno`() {
        assertTrue(bound<WasichaiOrganizationsProperties>("wasichai.organizations").separateProvisioning)
    }

    @Test
    fun `revoca los tokens`() {
        val jwt = bound<JwtProperties>("wasichai.security.jwt")
        assertTrue(jwt.revocation)
        assertTrue(jwt.revocationCache.isPositive && jwt.revocationCache <= Duration.ofSeconds(10), "${jwt.revocationCache}")
    }

    @Test
    fun `limita los intentos de login`() {
        val login = bound<WasichaiLoginProperties>("wasichai.security.login")
        assertEquals(WasichaiLoginProperties(enabled = true, maxAttempts = 5, accountMaxAttempts = 20, window = Duration.ofMinutes(15)), login)
    }

    @Test
    fun `exige una politica de contrasenas`() {
        val password = bound<WasichaiPasswordProperties>("wasichai.security.password")
        assertEquals(WasichaiPasswordProperties(12, true, true, true, true, true), password)
    }

    // el límite de intentos cuenta por remoteAddress: detrás del proxy solo es la del cliente con esta estrategia
    @Test
    fun `lee la direccion del cliente de las cabeceras del proxy`() {
        assertEquals("framework", value("server.forward-headers-strategy"))
    }

    @Test
    fun `escribe los logs en ECS`() {
        assertEquals("ecs", value("logging.structured.format.console"))
    }

    @Test
    fun `no existe un perfil llamado test`() {
        // un perfil llamado test apaga beans de algunas librerías sin avisar (wasichai docs/modules/testing.md)
        for (name in listOf("application-test.yml", "application-test.yaml", "application-test.properties")) {
            assertFalse(ClassPathResource(name).exists(), name)
        }
    }
}
