package sgspe.territory

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException

// una capa de la lista blanca de un tenant (security.md §10.2, especificación de F6 §3.3): su nombre en GeoServer
// (<tabla física>__<columna>, resuelto por la metadata de la organización), su objeto, su geometría y sus estilos de
// model/layers.json, el primero por defecto (LYR-02 y LYR-03 comparten locality.location con dos estilos)
data class WmsLayer(
    val name: String,
    val objectName: String,
    val geometry: String,
    val styles: List<String>,
    val title: String
)

sealed interface WmsCall {
    data object Capabilities : WmsCall

    // GetMap o GetLegendGraphic ya revisados en su sintaxis; las capas y los estilos se autorizan contra la lista blanca
    data class Image(
        val request: String,
        val layers: List<String>,
        val styles: List<String>?,
        val params: Map<String, String>
    ) : WmsCall
}

// qué pasa al GeoServer interno (gis.md §6.2, security.md §10.2, RQ-SEC-011): solo GetMap y GetLegendGraphic con parámetros
// en lista blanca, comparados sin caja y sin repetidos; GetCapabilities lo arma SGSPE. GetFeatureInfo, DescribeLayer, los
// estilos y filtros del cliente (SLD, SLD_BODY, CQL_FILTER, FILTER, FEATUREID, ENV, VIEWPARAMS) y todo lo demás son 422,
// porque los atributos de una capa publicada solo saldrían por ahí (especificación de F6 §2.2.2). puro: no lee nada
object WmsRequestFilter {
    const val MAX_SIZE = 2048
    const val GET_MAP = "GetMap"
    const val GET_LEGEND = "GetLegendGraphic"
    private val ALLOWED =
        setOf("SERVICE", "VERSION", "REQUEST", "LAYERS", "LAYER", "STYLES", "STYLE", "BBOX", "SRS", "CRS", "WIDTH", "HEIGHT", "FORMAT", "TRANSPARENT", "TILED")
    private val FORMATS = setOf("image/png", "image/jpeg")
    private val VERSIONS = setOf("1.0.0", "1.1.1", "1.3.0")
    private val SYSTEMS = setOf("EPSG:3857", "EPSG:4326")
    private val PLAIN = Regex("""-?\d+(\.\d+)?([eE][-+]?\d+)?""")
    private val BOOLEANS = setOf("TRUE", "FALSE")

    fun parse(query: Map<String, List<String>>): WmsCall {
        val params = linkedMapOf<String, String>()
        for ((raw, values) in query) {
            val key = raw.uppercase()
            if (key !in ALLOWED) throw parameter(raw, "no se admite en el proxy WMS")
            if (values.size != 1 || key in params) throw parameter(raw, "va una sola vez")
            params[key] = values.single()
        }
        params["SERVICE"]?.let { if (!it.equals("WMS", ignoreCase = true)) throw request("SERVICE", "solo WMS") }
        params["VERSION"]?.let { if (it !in VERSIONS) throw parameter("VERSION", "se admiten ${VERSIONS.joinToString(", ")}") }
        val request = params["REQUEST"] ?: throw missing("REQUEST")
        return when {
            request.equals("GetCapabilities", ignoreCase = true) -> WmsCall.Capabilities
            request.equals(GET_MAP, ignoreCase = true) -> getMap(params)
            request.equals(GET_LEGEND, ignoreCase = true) -> legend(params)
            else -> throw request("REQUEST", "solo GetMap, GetLegendGraphic y GetCapabilities")
        }
    }

    private fun getMap(params: Map<String, String>): WmsCall.Image {
        val layers = (params["LAYERS"] ?: throw missing("LAYERS")).split(',').map(String::trim)
        if (layers.any { it.isEmpty() }) throw parameter("LAYERS", "una capa vacía")
        val styles = params["STYLES"]?.takeIf { it.isNotEmpty() }?.split(',')?.map(String::trim)
        if (styles != null && styles.size != layers.size) throw parameter("STYLES", "un estilo por capa")
        val system = system(params)
        val box = bbox(params["BBOX"] ?: throw missing("BBOX"))
        val out = linkedMapOf("SERVICE" to "WMS", "VERSION" to (params["VERSION"] ?: "1.3.0"), "REQUEST" to GET_MAP)
        out[system.first] = system.second
        out["BBOX"] = box
        out["WIDTH"] = size(params, "WIDTH", required = true)!!
        out["HEIGHT"] = size(params, "HEIGHT", required = true)!!
        out["FORMAT"] = format(params, required = true)!!
        flag(params, "TRANSPARENT")?.let { out["TRANSPARENT"] = it }
        flag(params, "TILED")?.let { out["TILED"] = it }
        // capas repetidas: se queda la primera con su estilo
        val keep = layers.indices.filter { layers.indexOf(layers[it]) == it }
        return WmsCall.Image(GET_MAP, keep.map { layers[it] }, styles?.let { s -> keep.map { s[it] } }, out)
    }

    private fun legend(params: Map<String, String>): WmsCall.Image {
        val layer = params["LAYER"]?.trim()?.ifEmpty { null } ?: throw missing("LAYER")
        val out = linkedMapOf("SERVICE" to "WMS", "VERSION" to (params["VERSION"] ?: "1.1.1"), "REQUEST" to GET_LEGEND)
        out["FORMAT"] = format(params, required = true)!!
        size(params, "WIDTH", required = false)?.let { out["WIDTH"] = it }
        size(params, "HEIGHT", required = false)?.let { out["HEIGHT"] = it }
        flag(params, "TRANSPARENT")?.let { out["TRANSPARENT"] = it }
        return WmsCall.Image(GET_LEGEND, listOf(layer), params["STYLE"]?.trim()?.ifEmpty { null }?.let(::listOf), out)
    }

    // las capas en la lista blanca (si no, 403: otro tenant, no publicada o sin READ; también con la lista vacía) y cada
    // estilo, uno de los de la capa en layers.json o vacío (el primero). la lista blanca acota la cantidad: las capas pedidas
    // van sin repetir (parse), así que las que pasan nunca son más que ella
    fun authorize(
        call: WmsCall.Image,
        whitelist: Map<String, WmsLayer>
    ): Map<String, String> {
        val layers = call.layers.map { whitelist[it] ?: throw ForbiddenException("La capa pedida no está disponible para su organización o no puede leerla") }
        val styles =
            layers.mapIndexed { i, layer ->
                val asked = call.styles?.getOrNull(i)?.ifEmpty { null } ?: return@mapIndexed layer.styles.first()
                if (asked !in layer.styles) throw parameter(if (call.request == GET_MAP) "STYLES" else "STYLE", "solo los estilos de la capa")
                asked
            }
        val out = LinkedHashMap(call.params)
        if (call.request == GET_MAP) {
            out["LAYERS"] = layers.joinToString(",") { it.name }
            out["STYLES"] = styles.joinToString(",")
        } else {
            out["LAYER"] = layers.single().name
            out["STYLE"] = styles.single()
        }
        return out
    }

    private fun system(params: Map<String, String>): Pair<String, String> {
        val srs = params["SRS"]
        val crs = params["CRS"]
        if (srs != null && crs != null) throw parameter("CRS", "SRS o CRS, no los dos")
        val (key, value) = if (crs != null) "CRS" to crs else "SRS" to (srs ?: throw missing("CRS"))
        if (value.uppercase() !in SYSTEMS) throw parameter(key, "se admiten ${SYSTEMS.joinToString(", ")}")
        return key to value.uppercase()
    }

    // solo decimales llanos (sin espacios, sufijos ni hexadecimales); se reenvía la forma canónica de los números
    private fun bbox(raw: String): String {
        val parts = raw.trim().split(',').map { it.trim() }
        val numbers = parts.map { if (PLAIN.matches(it)) it.toDoubleOrNull() else null }
        if (numbers.size != 4 || numbers.any { it == null || !it.isFinite() } || numbers[0]!! >= numbers[2]!! || numbers[1]!! >= numbers[3]!!) {
            throw ValidationException("BBOX inválido", "BBOX", "cuatro números minX,minY,maxX,maxY con el mínimo menor que el máximo")
        }
        return numbers.joinToString(",")
    }

    private fun size(
        params: Map<String, String>,
        key: String,
        required: Boolean
    ): String? {
        val raw = params[key] ?: return if (required) throw missing(key) else null
        val value = raw.toIntOrNull()
        if (value == null || value < 1 || value > MAX_SIZE) throw parameter(key, "de 1 a $MAX_SIZE píxeles")
        return value.toString()
    }

    private fun format(
        params: Map<String, String>,
        required: Boolean
    ): String? {
        val raw = params["FORMAT"] ?: return if (required) throw missing("FORMAT") else null
        if (raw.lowercase() !in FORMATS) throw parameter("FORMAT", "se admiten ${FORMATS.joinToString(", ")}")
        return raw.lowercase()
    }

    private fun flag(
        params: Map<String, String>,
        key: String
    ): String? {
        val raw = params[key] ?: return null
        if (raw.uppercase() !in BOOLEANS) throw parameter(key, "TRUE o FALSE")
        return raw.uppercase()
    }

    private fun parameter(
        field: String,
        reason: String
    ): WasichaiException =
        Finding("WMS_PARAMETER_NOT_ALLOWED", "Parámetro WMS no admitido: $reason", safe(field), status = HttpStatus.valueOf(422)).toCodedException()

    // el campo del 422 puede venir del cliente: 64 caracteres y sin controles
    private fun safe(field: String): String = field.take(64).map { if (it.isISOControl()) '?' else it }.joinToString("")

    private fun request(
        field: String,
        reason: String
    ): WasichaiException = Finding("WMS_REQUEST_NOT_ALLOWED", "Petición WMS no admitida: $reason", field, status = HttpStatus.valueOf(422)).toCodedException()

    private fun missing(field: String): WasichaiException = ValidationException("Falta un parámetro WMS", field, "es obligatorio")
}

// el GetCapabilities del proxy (security.md §10.2): solo las capas de la lista blanca de quien llama, con su estilo; nunca el
// documento de GeoServer, que listaría las capas de todos los tenants. WMS 1.3.0 mínimo
object WmsCapabilities {
    fun xml(
        layers: Collection<WmsLayer>,
        onlineResource: String
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<WMS_Capabilities version="1.3.0" xmlns="http://www.opengis.net/wms" xmlns:xlink="http://www.w3.org/1999/xlink">""")
            append("<Service><Name>WMS</Name><Title>SGSPE</Title>")
            append("""<OnlineResource xlink:type="simple" xlink:href="${escape(onlineResource)}"/></Service>""")
            append("<Capability><Request>")
            append("<GetCapabilities><Format>text/xml</Format>${dcp(onlineResource)}</GetCapabilities>")
            append("<GetMap><Format>image/png</Format><Format>image/jpeg</Format>${dcp(onlineResource)}</GetMap>")
            append("</Request><Exception><Format>XML</Format></Exception>")
            append("<Layer><Title>SGSPE</Title><CRS>EPSG:3857</CRS><CRS>EPSG:4326</CRS>")
            for (layer in layers) {
                append("""<Layer queryable="0"><Name>${escape(layer.name)}</Name><Title>${escape(layer.title)}</Title>""")
                for (style in layer.styles) append("<Style><Name>${escape(style)}</Name><Title>${escape(style)}</Title></Style>")
                append("</Layer>")
            }
            append("</Layer></Capability></WMS_Capabilities>")
        }

    private fun dcp(url: String) = """<DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="${escape(url)}"/></Get></HTTP></DCPType>"""

    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
