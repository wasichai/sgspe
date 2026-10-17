package sgspe.ai

import com.embabel.agent.config.models.anthropic.AnthropicModelLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.DefaultResourceLoader

// application.yml tal como se publica (ASM-128, ASM-129, ASM-131, ai-architecture.md §10 y §11), sin las variables de entorno ni
// las propiedades de sistema de quien corre la prueba
class AiPropertiesTest {
    private val environment =
        StandardEnvironment().apply {
            propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
            propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
            YamlPropertySourceLoader().load("application", ClassPathResource("application.yml")).forEach { propertySources.addLast(it) }
        }
    private val properties = Binder.get(environment).bind("sgspe.ai", AiProperties::class.java).get()

    @Test
    fun `la IA viene apagada para toda organizacion`() {
        assertEquals(emptyList<String>(), properties.enabledOrganizations.filter { it.isNotBlank() })
        assertFalse(properties.enabled("demo"))
        assertEquals("anthropic", properties.provider)
    }

    @Test
    fun `el modelo de cada caso y el del asistente`() {
        for (case in listOf("AI-01", "AI-02", "AI-03", "AI-04", "AI-06")) assertEquals("claude-opus-4-8", properties.modelFor(case), case)
        for (case in listOf("AI-07", "AI-08")) assertEquals("claude-haiku-5-5", properties.modelFor(case), case)
        // AI-05 usa wasichai.agent.model (ASM-129)
        assertThrows<IllegalStateException> { properties.modelFor("AI-05") }
        assertEquals("claude-opus-4-8", environment.getProperty("wasichai.agent.model"))
    }

    @Test
    fun `cada modelo configurado esta en el catalogo de Embabel`() {
        val catalog = ClassPathResource("models/anthropic-models.yml").inputStream.use { String(it.readAllBytes(), Charsets.UTF_8) }
        val known = modelIds(catalog)
        val configured = properties.models.values.toSet() + environment.getProperty("wasichai.agent.model").orEmpty()
        assertTrue(known.isNotEmpty(), "no se leyó el catálogo de Embabel")
        assertEquals(emptySet<String>(), configured - known, "modelos fuera del catálogo de Embabel 1.5.3: $known")
    }

    @Test
    fun `el catalogo propio tapa al de Embabel y le suma solo Haiku 5_5`() {
        // models/anthropic-models.yml de SGSPE va antes que el del jar de Embabel en el classpath y lo reemplaza entero: tiene que
        // conservar cada modelo del catálogo de Embabel 1.5.3. Opus 5.5 y Sonnet 5.5 no entran: Embabel 1.5.3 manda siempre
        // thinking «disabled» y esos modelos lo rechazan con 400 (ASM-129)
        val catalogs = javaClass.classLoader.getResources("models/anthropic-models.yml").toList()
        assertTrue(catalogs.size >= 2, "falta el catálogo propio o el de Embabel: $catalogs")
        assertFalse(catalogs.first().toString().startsWith("jar:"), "el catálogo que lee Embabel no es el de SGSPE: ${catalogs.first()}")
        val ids = { url: java.net.URL -> url.openStream().use { modelIds(String(it.readAllBytes(), Charsets.UTF_8)) } }
        val bundled = catalogs.drop(1).first { it.toString().contains("embabel-agent-anthropic-autoconfigure") }
        assertEquals(ids(bundled) + "claude-haiku-5-5", ids(catalogs.first()))
        val loaded =
            AnthropicModelLoader(DefaultResourceLoader())
                .loadAutoConfigMetadata()
                .models
                .map { it.modelId }
                .toSet()
        assertEquals(ids(catalogs.first()), loaded)
    }

    private fun modelIds(catalog: String) = Regex("""model_id:\s*["']([^"']+)["']""").findAll(catalog).map { it.groupValues[1] }.toSet()

    @Test
    fun `la version del prompt de cada caso y los limites de ASM-131`() {
        for (case in listOf("AI-01", "AI-02", "AI-03", "AI-04", "AI-05", "AI-08")) assertEquals("1", properties.promptVersion(case), case)
        assertEquals(
            AiLimits(
                inputChars = 200_000,
                outputTokens = 8_192,
                actions = 8,
                questionChars = 4_000,
                fileBytes = 26_214_400,
                proposals = 50,
                maxConcurrentRuns = 3
            ),
            properties.limits
        )
    }

    @Test
    fun `una accion fallida no se reintenta dentro de la peticion`() {
        // Embabel reintenta hasta 5 veces con esperas de 10 s a 60 s: una pregunta tardaría minutos en fallar (ASM-131)
        assertEquals("1", environment.getProperty("embabel.agent.platform.action-qos.default.max-attempts"))
    }

    @Test
    fun `el slug de una organizacion activada no distingue mayusculas`() {
        val on = AiProperties(enabledOrganizations = listOf(" Demo "))
        assertTrue(on.enabled("demo"))
        assertTrue(on.enabled("DEMO"))
        assertFalse(on.enabled("demo2"))
        assertFalse(on.enabled(" "))
    }
}
