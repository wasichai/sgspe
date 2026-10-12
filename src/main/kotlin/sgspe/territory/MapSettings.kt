package sgspe.territory

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.StrictQuery
import wasichai.core.organization.OrganizationService

// el mapa base (gis.md §6.4, RQ-GIS-013, ASM-048, especificación de F6 §3.3 y §2.2.9): un estilo de MapLibre por URL, el
// de la organización (por su slug, que mantiene el operador como sgspe.ai.enabled-organizations) o el general; sin ninguno,
// «none» y el portal muestra «Sin mapa base», nunca las teselas de OpenStreetMap. attribution va por URL de estilo: en el
// yaml la clave va entre corchetes ("[https://…/estilo.json]") para conservar puntos y barras
@ConfigurationProperties("sgspe.map")
data class MapProperties(
    val baseStyleUrl: String? = null,
    val baseStyleUrlByOrganization: Map<String, String> = emptyMap(),
    val attribution: Map<String, String> = emptyMap()
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MapProperties::class)
class MapConfiguration

// la forma de BasemapSpec de @wasichai/gis 1.0.0: { type: 'style', url } o { type: 'none' }
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Basemap(
    val type: String,
    val url: String? = null
)

data class MapSettingsResponse(
    val basemap: Basemap,
    val attribution: String?
)

object MapSettings {
    fun resolve(
        properties: MapProperties,
        slug: String?
    ): MapSettingsResponse {
        val url =
            slug?.let { properties.baseStyleUrlByOrganization[it] }?.trim()?.ifEmpty { null }
                ?: properties.baseStyleUrl?.trim()?.ifEmpty { null }
                ?: return MapSettingsResponse(Basemap("none"), null)
        return MapSettingsResponse(Basemap("style", url), properties.attribution[url])
    }
}

// GET /api/sgspe/map/settings (api.md §4): autenticado (lo exige la seguridad de wasichai para /api/**), sin parámetros
@RestController
@RequestMapping("/api/sgspe/map/settings")
class MapSettingsController(
    private val organizations: OrganizationService,
    private val properties: MapProperties
) {
    @GetMapping
    suspend fun settings(exchange: ServerWebExchange): MapSettingsResponse {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return MapSettings.resolve(properties, organizations.current().slug)
    }
}
