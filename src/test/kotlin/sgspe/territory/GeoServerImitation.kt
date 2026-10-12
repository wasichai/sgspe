package sgspe.territory

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

// un GeoServer de imitación para WmsProxyApiTest con el HttpServer del JDK (ninguna dependencia nueva, ninguna red): responde
// la REST que usa GeoServerClient de wasichai (el datastore y la lista de featuretypes) y el WMS del workspace con un PNG de
// 1×1. `published` son los nombres que dice tener publicados; `down` responde 503 a todo; `delayMillis` demora cada respuesta
// (el plazo del proxy); `wmsDown` y `wmsDelayMillis` hacen lo mismo solo con /wms, para que la REST conteste y el proxy
// llegue a pedir la imagen; `wmsContentType` cambia el tipo del WMS (un ServiceException real llega como XML con 200).
// cada respuesta lleva SECRET_HEADER, que el proxy nunca debe copiar
class GeoServerImitation private constructor(
    private val server: HttpServer
) : AutoCloseable {
    val url: String get() = "http://127.0.0.1:${server.address.port}/geoserver"
    val published: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val wmsQueries: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var down = false

    @Volatile
    var delayMillis = 0L

    @Volatile
    var wmsDown = false

    @Volatile
    var wmsDelayMillis = 0L

    @Volatile
    var wmsContentType = "image/png"

    fun reset() {
        published.clear()
        wmsQueries.clear()
        down = false
        delayMillis = 0
        wmsDown = false
        wmsDelayMillis = 0
        wmsContentType = "image/png"
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val path = exchange.requestURI.path
            when {
                down -> respond(exchange, 503, "text/plain", "caído".toByteArray())
                path.endsWith("/featuretypes") -> respond(exchange, 200, "application/json", featureTypes().toByteArray())
                path.contains("/rest/workspaces/") && path.contains("/datastores/") -> respond(exchange, 200, "application/json", "{}".toByteArray())
                path.endsWith("/wms") -> {
                    wmsQueries += exchange.requestURI.rawQuery.orEmpty()
                    if (wmsDelayMillis > 0) Thread.sleep(wmsDelayMillis)
                    if (wmsDown) {
                        respond(exchange, 503, "text/plain", "caído".toByteArray())
                        return
                    }
                    val body = if (wmsContentType.startsWith("image/")) PNG else "<ServiceException>tabla t_secreta</ServiceException>".toByteArray()
                    respond(exchange, 200, wmsContentType, body)
                }
                else -> respond(exchange, 404, "text/plain", "no existe".toByteArray())
            }
        } finally {
            exchange.close()
        }
    }

    // GeoServer responde {"featureTypes":""} cuando el datastore no tiene nada
    private fun featureTypes(): String =
        if (published.isEmpty()) {
            """{"featureTypes":""}"""
        } else {
            published.joinToString(",", """{"featureTypes":{"featureType":[""", "]}}") { """{"name":"$it"}""" }
        }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        type: String,
        body: ByteArray
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.responseHeaders.add(SECRET_HEADER, "no-debe-salir")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() = server.stop(0)

    companion object {
        const val SECRET_HEADER = "X-Imitacion-GeoServer"
        val PNG: ByteArray = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")

        fun start(): GeoServerImitation {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val imitation = GeoServerImitation(server)
            server.createContext("/geoserver") { imitation.handle(it) }
            server.executor = Executors.newCachedThreadPool()
            server.start()
            return imitation
        }
    }
}
