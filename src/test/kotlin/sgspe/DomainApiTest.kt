package sgspe

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import sgspe.common.IfMatch
import tools.jackson.databind.JsonNode

// la base de las pruebas de integración del plan 2.2: F2ApiTest del plan 2.1 (project, assign, member, transition, move,
// fixture…) más las llamadas y los datos de prueba que piden las guardas y los endpoints de sgspe, todos ficticios
// (RQ-SEC-018). projectWithCrs lleva working_crs (EPSG 32717); las personas de prueba trabajan en el proyecto con una
// project_assignment vigente, porque ProjectScopeGuard acota sus escrituras
abstract class DomainApiTest : F2ApiTest() {
    // ---- llamadas que SgspeApiTest no trae ----

    protected fun patch(
        path: String,
        body: Any,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val result =
            client
                .patch()
                .uri(path)
                .headers { h ->
                    if (token != null) h.set(HttpHeaders.AUTHORIZATION, token)
                    headers.forEach(h::set)
                }.bodyValue(body)
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
        return Response(HttpStatus.valueOf(result.status.value()), result.responseBody ?: "", result.responseHeaders)
    }

    // un multipart: archivos (nombre de parte -> nombre de archivo y bytes) y campos de texto
    protected fun multipart(
        path: String,
        files: Map<String, Pair<String, ByteArray>>,
        fields: Map<String, String>,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): ResultBytes {
        val builder = MultipartBodyBuilder()
        files.forEach { (name, file) -> builder.part(name, file.second).filename(file.first) }
        fields.forEach { (name, value) -> builder.part(name, value) }
        val result =
            client
                .post()
                .uri(path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers { h ->
                    if (token != null) h.set(HttpHeaders.AUTHORIZATION, token)
                    headers.forEach(h::set)
                }.body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return ResultBytes(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    protected data class ResultBytes(
        val status: HttpStatus,
        val body: ByteArray,
        val headers: HttpHeaders
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    // ---- registros ----

    protected fun record(
        objectName: String,
        id: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", "/api/objects/$objectName/records/$id", null, HttpStatus.OK, token))

    // un alta por la API genérica con geometrías (GeoJSON EPSG:4326)
    protected fun createWith(
        objectName: String,
        attributes: Map<String, Any?>,
        geometries: Map<String, Any?> = emptyMap(),
        token: String = this.token,
        headers: Map<String, String> = emptyMap()
    ): String =
        tree(
            send("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes, "geometries" to geometries), HttpStatus.CREATED, token, headers)
        )["id"].asString()

    // como el portal: lee el registro y lo reenvía entero con los cambios (el PUT de wasichai reemplaza el registro)
    protected fun edit(
        objectName: String,
        id: String,
        changes: Map<String, Any?>,
        token: String = this.token,
        headers: Map<String, String> = emptyMap(),
        geometries: Map<String, Any?>? = null
    ): Response {
        val current = record(objectName, id, token)["attributes"]
        val attributes = current.properties().associate { (k, v) -> k to json(v) } + changes
        val body = if (geometries == null) mapOf("attributes" to attributes) else mapOf("attributes" to attributes, "geometries" to geometries)
        return exchange("PUT", "/api/objects/$objectName/records/$id", body, token, headers)
    }

    // una transición con cabeceras propias (If-Match); la de F2ApiTest solo lleva el motivo
    protected fun transitionWith(
        objectName: String,
        id: String,
        name: String,
        token: String = this.token,
        headers: Map<String, String> = emptyMap(),
        reason: String? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/$objectName/records/$id/transitions/$name",
            null,
            token,
            headers + listOfNotNull(reason?.let { REASON to reason(it) })
        )

    protected fun link(
        objectName: String,
        id: String,
        relationship: String,
        otherId: String,
        token: String = this.token
    ): Response = exchange("POST", "/api/objects/$objectName/records/$id/related/$relationship", mapOf("otherId" to otherId), token)

    // ---- los comandos de /api/sgspe/** (desde F3) ----

    // la cabecera de motivo de un comando o de una transición
    protected fun withReason(text: String = "motivo de prueba"): Map<String, String> = mapOf(REASON to reason(text))

    // el updatedAt del registro como lo pide If-Match
    protected fun ifMatch(
        objectName: String,
        id: String,
        token: String = this.token
    ): Map<String, String> = mapOf(IfMatch.HEADER to "\"${record(objectName, id, token)["updatedAt"].asString()}\"")

    // el código de una respuesta de error (P15: errors[0].message)
    protected fun code(response: Response): String = tree(response.body)["errors"][0]["message"].asString()

    // ---- datos de prueba ----

    // un proyecto con CRS de trabajo: el área oficial, las importaciones y approve de WF-A lo necesitan
    protected fun projectWithCrs(attributes: Map<String, Any?> = emptyMap()): String = project(mapOf("working_crs" to "EPSG 32717") + attributes)

    // una asignación con campos propios (inactiva, con fechas); la de F2ApiTest es vigente y sin fechas
    protected fun assignWith(
        account: Account,
        projectId: String,
        role: String,
        extra: Map<String, Any?>
    ): String =
        createRecord(
            "project_assignment",
            mapOf("project" to projectId, "member_user_id" to account.id, "member_user_email" to account.email, "role_name" to role, "active" to true) + extra
        )

    protected fun organization(name: String = "Asociación Ejemplo"): String = createRecord("organization", mapOf("name" to name))

    protected fun person(givenNames: String = "Persona Ejemplo"): String = createRecord("person", mapOf("given_names" to givenNames, "sex" to "FEMENINO"))

    // lo que `profile` exige de un grupo de interés (RF-STK-003) además del ámbito: tipo, motivo de inclusión y responsable
    protected fun profileFields(
        owner: Account,
        type: String = "LIDER LOCAL"
    ): Map<String, Any?> =
        mapOf(
            "stakeholder_type" to type,
            "inclusion_reason" to "Líder de la comunidad",
            "owner_user_id" to owner.id,
            "owner_user_email" to owner.email
        )

    protected fun stakeholder(
        project: String,
        attributes: Map<String, Any?> = emptyMap(),
        token: String = this.token
    ): String {
        val actor = if ("person" in attributes) emptyMap() else mapOf("organization" to organization())
        return createRecord("stakeholder", mapOf("project" to project, "stakeholder_scope" to "AIDS") + actor + attributes, token = token)
    }

    protected fun engagement(
        project: String,
        type: String = "REUNION",
        token: String = this.token,
        attributes: Map<String, Any?> = emptyMap()
    ): String = createRecord("engagement", mapOf("project" to project, "engagement_type" to type, "title" to "Reunión de prueba") + attributes, token = token)

    protected fun participant(
        engagement: String,
        token: String = this.token,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "engagement_participant",
            mapOf("engagement" to engagement, "represented_people_count" to 1, "attended" to true) + attributes,
            token = token
        )

    protected fun problemFields(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["field"].asString() }.orEmpty()

    companion object {
        // un valor de JsonNode como lo mandaría el portal
        fun json(node: JsonNode): Any? =
            when {
                node.isNull -> null
                node.isBoolean -> node.asBoolean()
                node.isIntegralNumber -> node.asLong()
                node.isNumber -> node.decimalValue()
                node.isString -> node.asString()
                else -> JSON.convertValue(node, Any::class.java)
            }

        fun point(
            longitude: Double,
            latitude: Double
        ): Map<String, Any?> = mapOf("type" to "Point", "coordinates" to listOf(longitude, latitude))

        // un cuadrado de `side` grados con su esquina en (longitude, latitude)
        fun square(
            longitude: Double,
            latitude: Double,
            side: Double,
            type: String = "Polygon"
        ): Map<String, Any?> {
            val ring =
                listOf(
                    listOf(longitude, latitude),
                    listOf(longitude + side, latitude),
                    listOf(longitude + side, latitude + side),
                    listOf(longitude, latitude + side),
                    listOf(longitude, latitude)
                )
            return if (type ==
                "MultiPolygon"
            ) {
                mapOf("type" to type, "coordinates" to listOf(listOf(ring)))
            } else {
                mapOf("type" to type, "coordinates" to listOf(ring))
            }
        }
    }
}
