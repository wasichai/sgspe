package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import wasichai.test.WasichaiIntegrationTest
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// la base de las pruebas de contrato con la plataforma (security.md §14). antes de la primera prueba de la JVM aprovisiona
// la organización del admin sembrado con los scripts reales de model/, en el orden de security.md §4.1, contra esta misma
// app; después da el token del admin y las llamadas que usan las pruebas. la base la comparten todas las clases de la
// corrida: cada prueba nombra sus objetos con uniqueName. a diferencia de CajaApiTest (caja-backend@dc9e391), no porta los
// scripts a Kotlin ni toca el almacén de wasichai por debajo: ArchitectureTest lo vigila
abstract class SgspeApiTest : WasichaiIntegrationTest() {
    // el del admin sembrado; una llamada toma otro donde la prueba lo necesita
    protected lateinit var token: String

    @Autowired
    private lateinit var environment: Environment

    @BeforeEach
    fun adminAndProvisioning() {
        token = bearer()
        synchronized(LOCK) {
            if (!provisioned) {
                for (script in PROVISIONING) {
                    val run = runScript(script)
                    assertEquals(0, run.exit, "python3 ${script.joinToString(" ")}\n${run.output}")
                }
                provisioned = true
            }
        }
    }

    protected data class Response(
        val status: HttpStatus,
        val body: String,
        val headers: HttpHeaders
    )

    protected data class Account(
        val token: String,
        val email: String,
        val id: String = ""
    )

    protected data class ScriptRun(
        val exit: Int,
        val output: String
    )

    // estado, cuerpo y cabeceras, sin afirmar nada. token null: sin Authorization
    protected fun exchange(
        method: String,
        path: String,
        body: Any? = null,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val spec =
            when (method) {
                "GET" -> client.get().uri(path)
                "DELETE" -> client.delete().uri(path)
                "PUT" -> client.put().uri(path).bodyValue(body!!)
                "POST" -> if (body == null) client.post().uri(path) else client.post().uri(path).bodyValue(body)
                else -> error("método $method")
            }
        val result =
            spec
                .headers { h ->
                    if (token != null) h.set(HttpHeaders.AUTHORIZATION, token)
                    headers.forEach(h::set)
                }.exchange()
                .expectBody(String::class.java)
                .returnResult()
        return Response(HttpStatus.valueOf(result.status.value()), result.responseBody ?: "", result.responseHeaders)
    }

    // el cuerpo de una llamada que tiene que contestar `status`
    protected fun send(
        method: String,
        path: String,
        body: Any?,
        status: HttpStatus,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): String {
        val response = exchange(method, path, body, token, headers)
        assertEquals(status, response.status, "$method $path: ${response.body}")
        return response.body
    }

    protected fun tree(body: String): JsonNode = JSON.readTree(body)

    protected fun field(
        name: String,
        type: String,
        unique: Boolean = false
    ): Map<String, Any> = mapOf("name" to name, "label" to name, "type" to type, "unique" to unique)

    // un objeto de prueba creado por la api de metadata, como admin: F1 no tiene objetos propios
    protected fun createObject(
        name: String,
        fields: List<Map<String, Any>>,
        flags: Map<String, Boolean> = emptyMap(),
        token: String = this.token
    ) {
        send("POST", "/api/objects", mapOf("name" to name, "label" to name, "pluralLabel" to name, "fields" to fields) + flags, HttpStatus.CREATED, token)
    }

    // un registro por la api genérica: su id
    protected fun createRecord(
        objectName: String,
        attributes: Map<String, Any?>,
        headers: Map<String, String> = emptyMap(),
        token: String = this.token
    ): String = tree(send("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), HttpStatus.CREATED, token, headers))["id"].asString()

    protected fun me(token: String = this.token): JsonNode = tree(send("GET", "/api/auth/me", null, HttpStatus.OK, token))

    // un usuario (no ADMIN) con un rol de roles.json, con un correo ficticio @sgspe.test
    protected fun userWithRole(role: String): Account {
        val email = "${uniqueName(role.lowercase().replace('_', '-'))}@sgspe.test"
        val created =
            tree(
                send(
                    "POST",
                    "/api/users",
                    mapOf("email" to email, "displayName" to "Usuario de prueba", "password" to PASSWORD, "roles" to listOf(role)),
                    HttpStatus.CREATED
                )
            )
        return Account(bearer(email, PASSWORD), email, created["id"].asString())
    }

    // un tenant nuevo con su primer administrador (security.md §4.1, paso 1): su token y su correo
    protected fun provisionTenant(): Account {
        val slug = uniqueName("tenant")
        val email = "admin-$slug@sgspe.test"
        send(
            "POST",
            "/api/organizations",
            mapOf(
                "name" to "Organización de prueba $slug",
                "slug" to slug,
                "adminEmail" to email,
                "adminPassword" to PASSWORD,
                "adminDisplayName" to "Administración de prueba"
            ),
            HttpStatus.CREATED
        )
        return Account(bearer(email, PASSWORD), email)
    }

    // corre un script de model/ contra esta app como `account` (el admin sembrado si es null): código de salida y salida
    protected fun runScript(
        args: List<String>,
        account: Account? = null
    ): ScriptRun {
        val email = account?.email ?: WasichaiIntegrationTest.ADMIN_EMAIL
        val password = if (account == null) WasichaiIntegrationTest.ADMIN_PASSWORD else PASSWORD
        val port = environment.getRequiredProperty("local.server.port")
        val command = listOf("python3") + args + listOf("--core", "http://127.0.0.1:$port", "--email", email, "--password", password)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS)) { "${args.first()} no terminó en 120 s" }
        return ScriptRun(process.exitValue(), output)
    }

    companion object {
        // wasichai pide al menos 8 caracteres
        const val PASSWORD = "clave-de-prueba-sgspe"
        const val REASON = "X-Change-Reason"
        val JSON: JsonMapper = JsonMapper.builder().build()

        private val LOCK = Any()

        @Volatile
        private var provisioned = false

        private val PROVISIONING = listOf(listOf("model/apply.py"), listOf("model/apply_roles.py"), listOf("model/apply.py", "--workflows"))

        // el motivo en la forma RFC 8187 que pide wasichai para un texto con tildes (api.md §3.7)
        fun reason(text: String): String = "UTF-8''" + URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
    }
}
