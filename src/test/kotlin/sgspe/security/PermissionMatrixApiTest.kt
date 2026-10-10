package sgspe.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest
import sgspe.scope.ProjectScope
import java.util.concurrent.ConcurrentHashMap

// security.md §14, «Matriz de permisos» (RQ-SEC-004, RQ-SEC-017): parametrizada desde model/roles.json, cada rol lee,
// crea, cambia y borra cada objeto de model.json según sus permisos, y recibe 403 donde no los tiene. un objeto apiOnly
// rechaza el alta por la API genérica a todos los roles y al admin del tenant. con `u`, la matriz comprueba solo que el
// PUT con los derivados (sin campos de negocio) responde 200; que `u` rechace los campos de negocio lo prueba
// FieldPermissionsApiTest (Tarea 14). cada rol escribe lo que crea él mismo, en el proyecto al que está asignado, y
// devuelve los derivados con su valor, para que la prueba valga también con las guardas del plan 2.2. límites conocidos:
// no ejerce APPROVE, EXPORT ni EDIT_ANY, ni UPDATE y DELETE de los objetos apiOnly (no hay registro que cambiar). una
// corrida local puede recortarse a unos roles con -Dmatrix.roles=FIELD_AGENT,GIS_ANALYST (docs/develop/README.md §6)
class PermissionMatrixApiTest : F2ApiTest() {
    // los 15 roles comparten el proyecto y el registro del admin de cada objeto: por el túnel, armarlos una vez por rol
    // triplica la duración
    override fun testProject(): String = SHARED.shared.getOrPut(PROJECT) { Fixture(super.testProject(), emptyMap()) }.id

    override fun adminFixture(objectName: String): Fixture = SHARED.shared.getOrPut(objectName) { super.adminFixture(objectName) }

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

        // un armado de la prueba que falla se informa como una diferencia más, sin perder las ya juntadas
        fun <T> attempt(
            what: String,
            block: () -> T
        ): T? =
            try {
                block()
            } catch (e: Throwable) {
                failures += "$what: no se pudo armar, ${e.message?.take(300)}"
                null
            }

        for ((name, obj) in Model.objects) {
            val actions = grants[name].orEmpty()
            val reads = "READ" in actions || readsTenant
            val headers = if (requiresReason(name)) mapOf(REASON to reason("matriz de permisos")) else emptyMap()
            val records = "/api/objects/$name/records"
            expect("$name READ", if (reads) HttpStatus.OK else HttpStatus.FORBIDDEN, exchange("GET", "$records?size=1", token = account.token))
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
                val attributes = attempt("$name CREATE") { sample(name) }
                if (attributes != null) {
                    val created = exchange("POST", records, mapOf("attributes" to attributes), account.token, headers)
                    expect("$name CREATE", HttpStatus.CREATED, created)
                    if (created.status == HttpStatus.CREATED) own = Fixture(tree(created.body)["id"].asString(), attributes)
                }
            } else {
                expect("$name CREATE", HttpStatus.FORBIDDEN, exchange("POST", records, mapOf("attributes" to emptyMap<String, Any>()), account.token, headers))
            }
            // el proyecto que se cambia es el de la prueba, al que el rol está asignado
            val target = if (name == "project") attempt("$name UPDATE") { adminFixture("project") } else own ?: attempt("$name UPDATE") { adminFixture(name) }
            val canUpdate = "UPDATE" in actions || "u" in actions
            if (target != null) {
                // un PUT reemplaza el registro: se devuelve lo que el rol lee (con UPDATE) o solo los derivados (con `u`), como
                // el portal; los campos que el rol no lee no llegan y wasichai los conserva
                var current: Map<String, Any?>? = emptyMap()
                if (reads) {
                    val read = exchange("GET", "$records/${target.id}", token = account.token)
                    expect("$name READ (registro)", HttpStatus.OK, read)
                    current = if (read.status == HttpStatus.OK) attributesOf(tree(read.body)) else null
                }
                // sin lo que el rol lee, el PUT de quien puede cambiar no tiene cuerpo válido y se omite: ya quedó informado
                if (current != null || !canUpdate) {
                    val body =
                        when {
                            "UPDATE" in actions -> current.orEmpty()
                            "u" in actions -> current.orEmpty().filterKeys { it in derivedFields(name) }
                            else -> emptyMap()
                        }
                    expect(
                        "$name UPDATE",
                        if (canUpdate) HttpStatus.OK else HttpStatus.FORBIDDEN,
                        exchange("PUT", "$records/${target.id}", mapOf("attributes" to body), account.token, headers)
                    )
                }
            }
            val victim = own ?: target
            if (victim != null) {
                expect(
                    "$name DELETE",
                    if ("DELETE" in actions) HttpStatus.NO_CONTENT else HttpStatus.FORBIDDEN,
                    exchange("DELETE", "$records/${victim.id}", token = account.token, headers = headers)
                )
            }
        }
        assertEquals(emptyList<String>(), failures, "$role:\n${failures.joinToString("\n")}")
    }

    // la columna «otro proyecto» (ASM-106 y ASM-108, security.md §14): un registro de un proyecto al que el rol no está asignado
    // no se lee (404) y el alta con ese proyecto es el 400 de wasichai sobre `project` (registro inexistente); quien tiene
    // READ_ALL_PROJECTS sobre project lo lee (200) y no da de alta en él sin asignación (403 NOT_ASSIGNED de ProjectScopeGuard).
    // se prueban el proyecto y las raíces cuyo registro mínimo solo nombra maestros del tenant (OTHER_PROJECT_ROOTS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("roles")
    fun `ningun rol lee ni da de alta en un proyecto al que no esta asignado`(role: String) {
        val account = member(role)
        val grants = Model.roles.getValue(role)
        val readsTenant = grants["*"]?.contains("READ") == true
        val readsAll = "READ_ALL_PROJECTS" in grants["project"].orEmpty()
        val other = SHARED.shared.getOrPut(OTHER) { Fixture(project(), emptyMap()) }.id
        val failures = mutableListOf<String>()

        fun expect(
            what: String,
            wanted: HttpStatus,
            response: Response,
            field: String? = null
        ) {
            if (response.status != wanted) {
                failures += "$what: $wanted esperado, ${response.status} ${response.body.take(300)}"
            } else if (field != null && field !in problemFields(response.body)) {
                failures += "$what: el error no nombra $field: ${response.body.take(300)}"
            }
        }
        if ("READ" in grants["project"].orEmpty() || readsTenant) {
            expect(
                "project READ (otro proyecto)",
                if (readsAll) HttpStatus.OK else HttpStatus.NOT_FOUND,
                exchange("GET", "/api/objects/project/records/$other", token = account.token)
            )
        }
        for (name in OTHER_PROJECT_ROOTS) {
            val actions = grants[name].orEmpty()
            val headers = if (requiresReason(name)) mapOf(REASON to reason("matriz de permisos")) else emptyMap()
            val records = "/api/objects/$name/records"
            if ("READ" in actions || readsTenant) {
                val foreign =
                    try {
                        SHARED.shared.getOrPut("$OTHER:$name") { fixture(name, mapOf("project" to other)) }
                    } catch (e: Throwable) {
                        failures += "$name: no se pudo armar el registro del otro proyecto, ${e.message?.take(300)}"
                        null
                    }
                if (foreign != null) {
                    expect(
                        "$name READ (otro proyecto)",
                        if (readsAll) HttpStatus.OK else HttpStatus.NOT_FOUND,
                        exchange("GET", "$records/${foreign.id}", token = account.token)
                    )
                }
            }
            if ("CREATE" in actions) {
                val created = exchange("POST", records, mapOf("attributes" to sample(name) + ("project" to other)), account.token, headers)
                if (readsAll) {
                    expect("$name CREATE (otro proyecto)", HttpStatus.FORBIDDEN, created, "project")
                } else {
                    expect("$name CREATE (otro proyecto)", HttpStatus.BAD_REQUEST, created, "project")
                }
            }
        }
        assertEquals(emptyList<String>(), failures, "$role:\n${failures.joinToString("\n")}")
    }

    private fun problemFields(body: String): List<String> =
        runCatching { tree(body)["errors"].values().map { it["field"].asString() } }.getOrDefault(emptyList())

    // el admin del tenant tiene acceso a todo por otro camino: un objeto apiOnly le rechaza el alta aunque el cuerpo sea
    // válido (con `sample`, un 400 no puede tapar el 403)
    @Test
    fun `un objeto apiOnly rechaza el alta valida tambien al admin`() {
        val failures = mutableListOf<String>()
        for (name in API_ONLY) {
            val headers = if (requiresReason(name)) mapOf(REASON to reason("matriz de permisos")) else emptyMap()
            val response = exchange("POST", "/api/objects/$name/records", mapOf("attributes" to sample(name)), headers = headers)
            val denied = response.status == HttpStatus.FORBIDDEN
            if (!denied) failures += "$name CREATE (admin): 403 esperado, ${response.status} ${response.body.take(300)}"
        }
        assertEquals(emptyList<String>(), failures, failures.joinToString("\n"))
    }

    companion object {
        private const val PROJECT = "\u0000project"
        private const val OTHER = "\u0000other"

        // las raíces con `project` obligatorio cuyo registro mínimo solo nombra maestros del tenant: se pueden armar en otro
        // proyecto sin un padre de ese proyecto (ProjectScope, ASM-106)
        private val OTHER_PROJECT_ROOTS: List<String> get() =
            ProjectScope.ROOTS
                .filter { name ->
                    val obj = Model.objects.getValue(name)
                    obj["apiOnly"] != true && name !in EXACTLY_ONE &&
                        Model.relationships.any { it["source"] == name && it["fieldName"] == "project" && it["required"] == true } &&
                        Model.relationships
                            .filter { it["source"] == name && it["required"] == true && it["fieldName"] != null && it["fieldName"] != "project" }
                            .all { it["target"] in ProjectScope.TENANT }
                }.sorted()

        // los objetos de model.json con "apiOnly": true
        private val API_ONLY: List<String> get() =
            Model.objects
                .filterValues { it["apiOnly"] == true }
                .keys
                .toList()

        // -Dmatrix.roles=A,B recorta la corrida local; sin ella, los roles de roles.json
        @JvmStatic
        fun roles(): List<String> {
            // un rol con DELETE sin CREATE borraría el registro compartido del admin y los demás roles fallarían en cascada
            val deletesWithoutCreate =
                Model.roles.flatMap { (role, grants) ->
                    grants
                        .filter { (name, actions) -> Model.objects[name]?.get("apiOnly") != true && "DELETE" in actions && "CREATE" !in actions }
                        .keys
                        .map { "$role/$it" }
                }
            check(deletesWithoutCreate.isEmpty()) {
                "roles.json da DELETE sin CREATE: borrarían el registro compartido del admin: $deletesWithoutCreate"
            }
            val wanted =
                System
                    .getProperty("matrix.roles")
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            val unknown = wanted - Model.roles.keys
            check(unknown.isEmpty()) { "matrix.roles nombra roles que roles.json no tiene: $unknown" }
            return if (wanted.isEmpty()) Model.roles.keys.toList() else wanted
        }

        private object SHARED {
            val shared = ConcurrentHashMap<String, Fixture>()
        }
    }
}
