package sgspe.territory

import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull
import org.springframework.web.util.UriComponentsBuilder
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.TenantModel
import sgspe.common.toCodedException
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.Actions
import wasichai.core.common.WasichaiException
import wasichai.gis.GeoServerClient
import wasichai.gis.GeoServerException
import wasichai.gis.GeoServerLayers
import wasichai.gis.GeoServerProperties
import wasichai.gis.GeoServerUrls
import java.time.Duration

// una capa pública de model/layers.json (plan 6.1, especificación de F6 §3.3): la capa LYR, su objeto, su geometría y su
// estilo SLD
data class PublicLayer(
    val layer: String,
    val objectName: String,
    val geometry: String,
    val style: String
)

// model/layers.json viaja en el jar como el modelo (processResources de build.gradle.kts): es la misma lista que publica
// publish_layers.py, así que el proxy nunca sirve una capa que no está en ella
object WmsLayersCatalog {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    val ALL: List<PublicLayer> by lazy {
        val text =
            WmsLayersCatalog::class.java.getResourceAsStream("/model/layers.json")?.use { String(it.readAllBytes()) }
                ?: error("falta model/layers.json en el jar")
        parse(text)
    }

    fun parse(json: String): List<PublicLayer> {
        val root = JSON.readTree(json)
        val list = if (root.isArray) root else root.path("layers")
        return list.values().map {
            PublicLayer(it.path("layer").asString(), it.path("object").asString(), it.path("geometry").asString(), it.path("style").asString())
        }
    }
}

// el proxy (sgspe.territory.wms): plazo de GeoServer, tope de una imagen en memoria y cuánto vale la lista de capas
// publicadas que da la REST de GeoServer (no se pide en cada tesela)
@ConfigurationProperties("sgspe.territory.wms")
data class WmsProperties(
    val timeout: Duration = Duration.ofSeconds(5),
    val maxBytes: Int = 16 * 1024 * 1024,
    val publishedTtl: Duration = Duration.ofSeconds(60)
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WmsProperties::class)
class WmsConfiguration

// el proxy WMS autenticado (gis.md §6.2, security.md §10.2, RQ-SEC-011, RQ-GIS-012, especificación de F6 §3.3): la lista
// blanca de quien llama son las capas de layers.json que su organización tiene, que GeoServer tiene publicadas con el nombre
// <tabla física>__<columna> de esa organización y sobre cuyo objeto tiene READ. GeoServer se llama solo aquí, con WebClient y
// un plazo corto: caído, lento o con una respuesta que no es imagen (un ServiceException llega como XML con 200), 503
// GEOSERVER_UNAVAILABLE, y el portal sigue con las capas por API (WAS-ADR-0006). nunca se copian cabeceras de GeoServer
@Service
class WmsProxy(
    private val callers: Callers,
    private val permissions: Permissions,
    private val definitions: ObjectDefinitions,
    private val tenantModel: TenantModel,
    private val geoserver: GeoServerClient,
    private val geoserverProperties: GeoServerProperties,
    private val properties: WmsProperties
) {
    // un WebClient propio: Boot 4 no da un WebClient.Builder como bean (como GeoServerClient de wasichai)
    private val client: WebClient =
        WebClient
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize(properties.maxBytes) }
            .build()

    private class Published(
        val names: Set<String>,
        val at: Long
    )

    @Volatile
    private var published: Published? = null

    suspend fun whitelist(): Map<String, WmsLayer> {
        val user = callers.require()
        val names = publishedNames()
        val out = linkedMapOf<String, WmsLayer>()
        // una capa de GeoServer por geometría de objeto; varias entradas de layers.json (LYR-02 y LYR-03) le dan sus estilos
        for ((key, entries) in WmsLayersCatalog.ALL.groupBy { it.objectName to it.geometry }) {
            val (objectName, geometry) = key
            if (!tenantModel.has(user.organizationId, objectName)) continue
            if (!permissions.has(user, Actions.READ, objectName)) continue
            val definition = definitions.current(user.organizationId, objectName)
            val field = definition.fields.firstOrNull { it.name == geometry } ?: continue
            val name = GeoServerLayers.name(definition.obj.physicalTable, field.columnName)
            if (name !in names) continue
            out[name] = WmsLayer(name, objectName, geometry, entries.map { it.style }, "${entries.joinToString(", ") { it.layer }} · ${definition.obj.label}")
        }
        return out
    }

    // la imagen de GeoServer y su tipo; null de cualquier lado es 503
    suspend fun fetch(params: Map<String, String>): Pair<MediaType, ByteArray> {
        if (!geoserverProperties.enabled) throw unavailable()
        // cada valor va como variable de URI y se codifica entero (estricto): la consulta nunca se arma juntando texto
        val builder = UriComponentsBuilder.fromUriString(GeoServerUrls.wms(geoserverProperties.baseUrl, geoserverProperties.workspace))
        val values = linkedMapOf<String, String>()
        params.entries.forEachIndexed { i, (key, value) ->
            builder.queryParam(key, "{v$i}")
            values["v$i"] = value
        }
        val uri = builder.encode().buildAndExpand(values).toUri()
        val result =
            try {
                withTimeoutOrNull(properties.timeout.toMillis()) {
                    client.get().uri(uri).awaitExchangeOrNull { response ->
                        val type = response.headers().contentType().orElse(null)
                        if (response.statusCode().is2xxSuccessful && type != null && IMAGES.any { it.isCompatibleWith(type) }) {
                            type to response.awaitBody<ByteArray>()
                        } else {
                            response.releaseBody().awaitSingleOrNull()
                            null
                        }
                    }
                }
            } catch (_: WebClientException) {
                null
            } catch (_: DataBufferLimitException) {
                // una imagen más grande que sgspe.territory.wms.max-bytes: no se sirve a medias
                null
            }
        return result ?: throw unavailable()
    }

    private suspend fun publishedNames(): Set<String> {
        if (!geoserverProperties.enabled) throw unavailable()
        val now = System.nanoTime()
        published?.let { if (now - it.at < properties.publishedTtl.toNanos()) return it.names }
        val names =
            try {
                withTimeoutOrNull(properties.timeout.toMillis()) { geoserver.publishedLayerNames() }
            } catch (_: GeoServerException) {
                // GeoServerClient vuelve 502 toda falla de transporte o de estado: para el mapa es el mismo «no disponible»
                null
            } ?: throw unavailable()
        published = Published(names, now)
        return names
    }

    private fun unavailable(): WasichaiException =
        Finding(
            "GEOSERVER_UNAVAILABLE",
            "GeoServer no responde: el mapa sigue con las capas por la API",
            "wms",
            status = HttpStatus.SERVICE_UNAVAILABLE
        ).toCodedException()

    companion object {
        private val IMAGES = listOf(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
    }
}

// GET /api/sgspe/territory/wms (api.md §4): la sintaxis primero (401 lo da la seguridad de wasichai; 400 y 422 sin tocar
// GeoServer), después la lista blanca (503 si no se sabe qué está publicado), las capas y los estilos (403, 422) y la imagen
@RestController
@RequestMapping(WmsController.PATH)
class WmsController(
    private val proxy: WmsProxy
) {
    @GetMapping
    suspend fun wms(request: ServerHttpRequest): ResponseEntity<ByteArray> {
        val call = WmsRequestFilter.parse(request.queryParams)
        val whitelist = proxy.whitelist()
        return when (call) {
            WmsCall.Capabilities ->
                ResponseEntity
                    .ok()
                    .contentType(CAPABILITIES)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(WmsCapabilities.xml(whitelist.values, PATH).toByteArray())
            is WmsCall.Image -> {
                val (type, bytes) = proxy.fetch(WmsRequestFilter.authorize(call, whitelist))
                ResponseEntity
                    .ok()
                    .contentType(type)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .body(bytes)
            }
        }
    }

    companion object {
        const val PATH = "/api/sgspe/territory/wms"
        private val CAPABILITIES = MediaType.parseMediaType("text/xml;charset=UTF-8")
    }
}
