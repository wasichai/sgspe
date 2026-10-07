package sgspe.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// security.md §14, «Matriz de permisos» (RQ-SEC-004, RQ-SEC-017): parametrizada desde model/roles.json, cada rol lee,
// crea, cambia y borra cada objeto de model.json según sus permisos, y recibe 403 donde no los tiene. `u` cambia el
// registro sin campos de negocio. un objeto apiOnly rechaza el alta por la API genérica a todos. cada rol escribe lo que
// crea él mismo, en el proyecto al que está asignado, y devuelve los derivados con su valor, para que la prueba valga
// también con las guardas del plan 2.2
class PermissionMatrixApiTest : F2ApiTest() {
    // los 15 roles comparten el proyecto y el registro del admin de cada objeto: por el túnel, armarlos una vez por rol
    // triplica la duración
    override fun testProject(): String = SHARED.project ?: super.testProject().also { SHARED.project = it }

    override fun adminFixture(objectName: String): Fixture = SHARED.fixtures.getOrPut(objectName) { super.adminFixture(objectName) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roles")
    fun `cada objeto responde a cada accion como dice roles json`(role: String) {
        val account = member(role)
        val grants = Model.roles.getValue(role)
        val readsTenant = grants["*"]?.contains("READ") == true
        val failures = mutableListOf<String>()

        fun expect(
            what: String,
            wanted: HttpStatus,
            response: Response
        ) {
            if (response.status != wanted) failures += "$what: $wanted esperado, ${response.status} ${response.body.take(300)}"
        }

        for ((name, obj) in Model.objects) {
            val actions = grants[name].orEmpty()
            val headers = if (requiresReason(name)) mapOf(REASON to reason("matriz de permisos")) else emptyMap()
            val records = "/api/objects/$name/records"
            expect(
                "$name READ",
                if ("READ" in actions ||
                    readsTenant
                ) {
                    HttpStatus.OK
                } else {
                    HttpStatus.FORBIDDEN
                },
                exchange("GET", "$records?size=1", token = account.token)
            )
            if (obj["apiOnly"] == true) {
                expect(
                    "$name CREATE (apiOnly)",
                    HttpStatus.FORBIDDEN,
                    exchange("POST", records, mapOf("attributes" to emptyMap<String, Any>()), account.token, headers)
                )
                continue
            }
            var own: Fixture? = null
            if ("CREATE" in actions) {
                val attributes = sample(name)
                val created = exchange("POST", records, mapOf("attributes" to attributes), account.token, headers)
                expect("$name CREATE", HttpStatus.CREATED, created)
                if (created.status == HttpStatus.CREATED) own = Fixture(tree(created.body)["id"].asString(), attributes)
            } else {
                expect("$name CREATE", HttpStatus.FORBIDDEN, exchange("POST", records, mapOf("attributes" to emptyMap<String, Any>()), account.token, headers))
            }
            // el proyecto que se cambia es el de la prueba, al que el rol está asignado
            val target = if (name == "project") adminFixture("project") else own ?: adminFixture(name)
            // un PUT reemplaza el registro: se devuelve lo que el rol lee (con UPDATE) o solo los derivados (con `u`), como
            // el portal; los campos que el rol no lee no llegan y wasichai los conserva
            val current =
                if ("READ" in actions ||
                    readsTenant
                ) {
                    attributesOf(tree(send("GET", "$records/${target.id}", null, HttpStatus.OK, account.token)))
                } else {
                    emptyMap()
                }
            val body =
                when {
                    "UPDATE" in actions -> current
                    "u" in actions -> current.filterKeys { it in derivedFields(name) }
                    else -> emptyMap()
                }
            val updated = if ("UPDATE" in actions || "u" in actions) HttpStatus.OK else HttpStatus.FORBIDDEN
            expect("$name UPDATE", updated, exchange("PUT", "$records/${target.id}", mapOf("attributes" to body), account.token, headers))
            val victim = own ?: target
            expect(
                "$name DELETE",
                if ("DELETE" in
                    actions
                ) {
                    HttpStatus.NO_CONTENT
                } else {
                    HttpStatus.FORBIDDEN
                },
                exchange("DELETE", "$records/${victim.id}", token = account.token, headers = headers)
            )
        }
        assertEquals(emptyList<String>(), failures, "$role:\n${failures.joinToString("\n")}")
    }

    companion object {
        @JvmStatic
        fun roles(): List<String> = Model.roles.keys.toList()

        private object SHARED {
            var project: String? = null
            val fixtures = mutableMapOf<String, Fixture>()
        }
    }
}
