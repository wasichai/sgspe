package sgspe.prod

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import java.time.Duration

/**
 * Guarda de producción (ADR-0020, RQ-SEC-016, RQ-SEC-001). Con el perfil `prod` activo, el arranque falla si la
 * semilla de desarrollo está encendida, si el secreto JWT falta o es el de desarrollo, si el token dura 8 horas o más, o
 * si las evidencias no van a un bucket compatible con S3 (ADR-0014: el disco de una réplica no lo ven las demás).
 *
 * Es un `EnvironmentPostProcessor`: corre cuando la configuración ya está cargada (application-prod.yml, variables de
 * entorno, argumentos) y antes de crear el contexto. Flyway pertenece al contexto, así que la guarda corre antes de
 * cualquier migración: en wasichai 0.3.2 la semilla es una migración, y una vez aplicada el usuario de desarrollo queda
 * en la base. Por eso no es un bean con `@PostConstruct`, que llegaría después de las migraciones.
 */
class ProdGuard :
    EnvironmentPostProcessor,
    Ordered {
    // después de ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE + 10), para leer el valor final de cada propiedad
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication
    ) {
        if (PROFILE !in environment.activeProfiles) return
        val problems = listOfNotNull(seed(environment), secret(environment), ttl(environment), files(environment))
        check(problems.isEmpty()) {
            "el perfil prod no arranca con una configuración de desarrollo (ADR-0020):\n" + problems.joinToString("\n") { "- $it" }
        }
    }

    private fun seed(environment: ConfigurableEnvironment): String? {
        val on = Binder.get(environment).bind(SEED, Boolean::class.javaObjectType).orElse(false) == true
        return if (on) "$SEED=true crea el usuario de desarrollo en la base; apáguela (WASICHAI_SEED_DEV=false o sin definir)" else null
    }

    private fun secret(environment: ConfigurableEnvironment): String? {
        val value =
            try {
                environment.getProperty(SECRET)
            } catch (_: IllegalArgumentException) {
                // el marcador ${WASICHAI_JWT_SECRET} de application-prod.yml sin variable que lo resuelva
                null
            }
        return when {
            value.isNullOrBlank() || value.startsWith("\${") -> "falta $SECRET: defina WASICHAI_JWT_SECRET (32 bytes o más)"
            value == DEV_SECRET -> "$SECRET es el secreto de desarrollo, que es público: defina WASICHAI_JWT_SECRET con uno propio"
            else -> null
        }
    }

    private fun ttl(environment: ConfigurableEnvironment): String? {
        val value = Binder.get(environment).bind(TTL, Duration::class.java).orElse(null)
        return when {
            value == null -> "falta $TTL: sin ella rige el valor por defecto de wasichai (8 h); defínala por debajo de 8 h"
            value >= MAX_TTL -> "$TTL=$value no es menor que 8 h; acórtela (WASICHAI_SECURITY_JWT_TTL)"
            else -> null
        }
    }

    private fun files(environment: ConfigurableEnvironment): String? {
        val store = environment.getProperty(STORE)?.trim()?.lowercase()
        val bucket = environment.getProperty(BUCKET)?.trim()
        return when {
            store != "s3" -> "$STORE=${store ?: "local"} guarda las evidencias en el disco de una réplica; use s3 (WASICHAI_FILES_STORE=s3)"
            bucket.isNullOrEmpty() -> "falta $BUCKET: defina WASICHAI_FILES_S3_BUCKET con el bucket de las evidencias"
            else -> null
        }
    }

    companion object {
        const val PROFILE = "prod"

        // el valor por defecto de wasichai.security.jwt.secret en application.yml; ProdGuardTest comprueba que coinciden
        const val DEV_SECRET = "sgspe-dev-only-secret-change-me-0123456789"
        private const val SEED = "wasichai.seed.dev"
        private const val SECRET = "wasichai.security.jwt.secret"
        private const val TTL = "wasichai.security.jwt.ttl"
        private const val STORE = "wasichai.files.store"
        private const val BUCKET = "wasichai.files.s3.bucket"
        private val MAX_TTL: Duration = Duration.ofHours(8)
    }
}
