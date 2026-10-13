package sgspe

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import sgspe.common.Records
import tools.jackson.databind.JsonNode
import wasichai.core.data.RecordService
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

// la base de las pruebas de integración de F2 (plan 2.1): registros mínimos válidos de los objetos de model/model.json
// (los 60 de F3 desde el plan 3.1, con la subida de sus campos FILE), usuarios con un rol y su asignación al proyecto de
// la clase, y las llamadas de workflow. los registros respetan las guardas de los planes 2.2 y 3.2 (alcance por
// asignación, exactamente uno de, rol de la asignación, sin campos derivados), para que estas pruebas sigan en verde
abstract class F2ApiTest : SgspeApiTest() {
    // un registro creado por la prueba: su id y los atributos con que se creó (los que un PUT vuelve a mandar)
    protected data class Fixture(
        val id: String,
        val attributes: Map<String, Any?>
    )

    private var ownProject: String? = null
    private val adminFixtures = mutableMapOf<String, Fixture>()

    @Autowired
    protected lateinit var platformRecords: Records

    @Autowired
    protected lateinit var platformService: RecordService

    protected fun organizationId(): UUID = UUID.fromString(me()["organizationId"].asString())

    // un registro escrito como la plataforma, con la marca SgspeWrite y su motivo, como lo hacen los endpoints de sgspe: la
    // única vía de alta de un objeto apiOnly en estas pruebas (nunca un campo FILE: la plataforma no adjunta archivos). vive
    // aquí desde el plan 7.1, porque el registro mínimo de ai_suggestion necesita su ai_interaction, que es apiOnly
    protected fun platformCreate(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String = "alta de prueba como la plataforma"
    ): String = runBlocking { platformService.asPlatform(organizationId()) { platformRecords.create(objectName, attributes, reason) } }.id.toString()

    // el proyecto de la prueba: todo registro de proyecto que arma cuelga de él. JUnit crea una instancia por @Test, pero
    // un @TestFactory comparte la suya entre todas sus pruebas dinámicas (WorkflowMatrixApiTest usa un solo proyecto y las
    // cuentas en caché para todos sus casos)
    protected open fun testProject(): String = ownProject ?: project().also { ownProject = it }

    protected fun project(attributes: Map<String, Any?> = emptyMap()): String =
        createRecord("project", mapOf("code" to uniqueName("PRY-"), "name" to "Proyecto de prueba") + attributes)

    // la asignación vigente de `account` al proyecto con `role` (RF-PRJ-005, ASM-070), creada por el admin
    protected fun assign(
        account: Account,
        projectId: String,
        role: String
    ): String =
        createRecord(
            "project_assignment",
            mapOf(
                "project" to projectId,
                "member_user_id" to account.id,
                "member_user_email" to account.email,
                "role_name" to role,
                "active" to true
            )
        )

    // un usuario con `role`, asignado con ese rol al proyecto (por defecto, el de la prueba)
    protected fun member(
        role: String,
        projectId: String = testProject()
    ): Account = userWithRole(role).also { assign(it, projectId, role) }

    // un registro mínimo válido de `objectName`, creado como `account` (el admin si es null)
    protected fun fixture(
        objectName: String,
        overrides: Map<String, Any?> = emptyMap(),
        account: Account? = null
    ): Fixture {
        val attributes = sample(objectName, account) + overrides
        val headers = if (requiresReason(objectName)) mapOf(REASON to reason("alta de prueba")) else emptyMap()
        val id = createRecord(objectName, attributes, headers, account?.token ?: token)
        return Fixture(id, attributes)
    }

    // el registro del admin de cada objeto, uno por prueba; el de `project` es el proyecto de la prueba
    protected open fun adminFixture(objectName: String): Fixture =
        adminFixtures.getOrPut(objectName) {
            if (objectName == "project") {
                Fixture(testProject(), tree(send("GET", "/api/objects/project/records/${testProject()}", null, HttpStatus.OK)).let { attributesOf(it) })
            } else {
                fixture(objectName)
            }
        }

    // atributos mínimos de `objectName` (sus archivos los prepara `account`, quien lo crea: solo quien sube adjunta): sus requeridos, los miembros de sus únicos, sus relaciones requeridas y la de
    // "exactamente uno de"; nunca un derivado ni un campo de la plataforma (SampleFields)
    @Suppress("UNCHECKED_CAST")
    protected fun sample(
        objectName: String,
        account: Account? = null
    ): Map<String, Any?> {
        val obj = Model.objects.getValue(objectName)
        val n = SEQUENCE.incrementAndGet()
        val inSets = (obj["uniqueConstraints"] as List<List<String>>?).orEmpty().flatten().toSet()
        val fixed = FIXED[objectName].orEmpty()
        val attributes = linkedMapOf<String, Any?>()
        var radix = n
        for (field in obj["fields"] as List<Map<String, Any?>>) {
            val name = field["name"] as String
            val type = field["type"] as String
            val required = field["required"] == true
            val unique = field["unique"] == true
            if (SampleFields.skipped(objectName, field)) continue
            when {
                name in fixed -> attributes[name] = fixed[name]
                type == "ENUM" && name in inSets -> {
                    val options = Model.enums.getValue(field["enum"] as String)
                    attributes[name] = options[radix % options.size]
                    radix /= options.size
                }
                type == "INTEGER" && name in inSets -> attributes[name] = n
                !(required || unique || name in inSets) -> Unit
                name.endsWith("_user_id") -> {
                    val user = userWithRole("READ_ONLY")
                    attributes[name] = user.id
                    attributes[name.removeSuffix("_id") + "_email"] = user.email
                }
                name.endsWith("_user_email") -> Unit
                type == "TEXT" -> attributes[name] = if (unique || name in inSets) uniqueName("${name.take(6)}-") else "Prueba $name"
                type == "LONG_TEXT" -> attributes[name] = "Texto de prueba"
                type == "INTEGER" || type == "DECIMAL" -> attributes[name] = 1
                type == "BOOLEAN" -> attributes[name] = true
                type == "DATE" -> attributes[name] = "2026-10-07"
                type == "DATETIME" -> attributes[name] = "2026-10-07T15:00:00Z"
                type == "ENUM" -> attributes[name] = Model.enums.getValue(field["enum"] as String).first()
                type == "FILE" -> attributes[name] = stagedFile(objectName, name, account)
                else -> error("sin valor de prueba para $objectName.$name ($type)")
            }
        }
        val chosen = EXACTLY_ONE[objectName].orEmpty()
        for (rel in Model.relationships) {
            val fieldName = rel["fieldName"] as String? ?: continue
            if (rel["source"] != objectName || !(rel["required"] == true || fieldName in chosen)) continue
            val target = rel["target"] as String
            attributes[fieldName] =
                when {
                    target == "project" -> testProject()
                    // desde F7, ai_suggestion cuelga de su ai_interaction, que es apiOnly: la crea la plataforma
                    Model.objects.getValue(target)["apiOnly"] == true -> platformCreate(target, sample(target))
                    else -> fixture(target).id
                }
        }
        return attributes
    }

    protected fun requiresReason(objectName: String): Boolean = Model.objects.getValue(objectName)["requiresReason"] == true

    // una subida preparada para el campo FILE `field` (WAS-ADR-0061), como `account` (el admin si es null): su id, que
    // adjunta el archivo en el alta de quien lo subió dentro de la mitad de wasichai.files.cleanup.delay
    protected fun stagedFile(
        objectName: String,
        field: String,
        account: Account? = null
    ): String {
        val parts = MultipartBodyBuilder().apply { part("file", PDF).filename("prueba.pdf") }.build()
        val body =
            client
                .post()
                .uri("/api/objects/$objectName/files/$field")
                .header(HttpHeaders.AUTHORIZATION, account?.token ?: token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        return tree(body)["id"].asString()
    }

    // los campos con "derived": true de un objeto: un PUT los devuelve con su valor, porque wasichai vacía lo que no recibe
    @Suppress("UNCHECKED_CAST")
    protected fun derivedFields(objectName: String): Set<String> =
        (Model.objects.getValue(objectName)["fields"] as List<Map<String, Any?>>)
            .filter { it["derived"] == true }
            .map { it["name"] as String }
            .toSet()

    protected fun attributesOf(record: JsonNode): Map<String, Any?> = JSON.convertValue(record["attributes"], Map::class.java).mapKeys { it.key as String }

    // aplica una transición; con `reason`, la cabecera X-Change-Reason
    protected fun transition(
        objectName: String,
        id: String,
        name: String,
        token: String = this.token,
        reason: String? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/$objectName/records/$id/transitions/$name",
            null,
            token,
            if (reason == null) emptyMap() else mapOf(REASON to reason(reason))
        )

    // aplica una transición que tiene que responder 200
    protected fun move(
        objectName: String,
        id: String,
        name: String,
        token: String = this.token,
        reason: String? = null
    ) {
        val response = transition(objectName, id, name, token, reason)
        if (response.status != HttpStatus.OK) throw AssertionError("$objectName $id $name: ${response.status} ${response.body}")
    }

    protected fun state(
        objectName: String,
        id: String
    ): String = tree(send("GET", "/api/objects/$objectName/records/$id", null, HttpStatus.OK))["state"].asString()

    // el listado de transiciones de un registro para quien llama: nombre → (allowed, reason)
    protected fun available(
        objectName: String,
        id: String,
        token: String = this.token
    ): Map<String, Pair<Boolean, String?>> =
        tree(send("GET", "/api/objects/$objectName/records/$id/transitions", null, HttpStatus.OK, token))
            .values()
            .associate { it["name"].asString() to (it["allowed"].asBoolean() to it["reason"]?.takeUnless { r -> r.isNull }?.asString()) }

    // model/model.json y model/roles.json, leídos una vez por JVM
    @Suppress("UNCHECKED_CAST")
    protected object Model {
        private val json = JSON.readValue(File("model/model.json"), Map::class.java) as Map<String, Any?>
        val objects: Map<String, Map<String, Any?>> = (json["objects"] as List<Map<String, Any?>>).associateBy { it["name"] as String }
        val relationships: List<Map<String, Any?>> = json["relationships"] as List<Map<String, Any?>>
        val enums: Map<String, List<String>> = json["enums"] as Map<String, List<String>>
        private val rolesFile = JSON.readValue(File("model/roles.json"), Map::class.java) as Map<String, Any?>
        val roles: Map<String, Map<String, List<String>>> =
            (rolesFile["roles"] as List<Map<String, Any?>>).associate { it["name"] as String to it["permisos"] as Map<String, List<String>> }
        val restrictedFields: List<Map<String, Any?>> = rolesFile["restrictedFields"] as List<Map<String, Any?>>
    }

    companion object {
        private val SEQUENCE = AtomicInteger()

        // un PDF mínimo: wasichai-files reconoce el tipo por su número mágico (application/pdf)
        val PDF: ByteArray = "%PDF-1.4\n%archivo de prueba de sgspe\n".toByteArray()

        // el destino que eligen las pruebas en los objetos con "exactamente uno de" (database-model.md, ExactlyOneGuard):
        // la organización, que no tiene campos restringidos; desde F3, el compromiso de una entrega y la interacción de una
        // tarea, que leen todos los roles que crean tareas (security.md §7.5)
        val EXACTLY_ONE =
            mapOf(
                "area_of_influence_member" to listOf("locality"),
                "land_right" to listOf("holder_organization"),
                "contact_method" to listOf("organization"),
                "stakeholder" to listOf("organization"),
                "actor_relationship" to listOf("from_organization", "to_organization"),
                "network_node" to listOf("organization"),
                "delivery" to listOf("commitment"),
                "agreement_party" to listOf("organization"),
                "task" to listOf("engagement")
            )

        // valores que la regla genérica no puede dar: un rol declarado (AssignmentRoleGuard, plan 2.2), un cargo sin persona,
        // que solo puede estar VACANTE o PREVISTO (StakeholderWriteGuard, RF-STK-002), un candidato a compromiso que
        // registra una persona (RF-COM-010: la IA llega en F7), el umbral de una regla de alerta, opcional en el modelo pero
        // exigido en el alta (WarningWriteGuard, plan 4.2b: 400 OUT_OF_RANGE sin él) y el año de un presupuesto de programa,
        // que el plan 5.2b acota de 2000 a 2100 (cada presupuesto mínimo cuelga de su propio programa: no repite el único)
        val FIXED =
            mapOf(
                "project_assignment" to mapOf("role_name" to "READ_ONLY", "active" to true),
                "position" to mapOf("position_status" to "VACANTE"),
                "commitment_candidate" to mapOf("ai_generated" to false),
                "early_warning_rule" to mapOf("threshold" to 3),
                "program_budget" to mapOf("budget_year" to 2026)
            )
    }
}
