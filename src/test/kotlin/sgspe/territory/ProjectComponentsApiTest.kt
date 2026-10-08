package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch
import java.util.concurrent.CompletableFuture

// POST /api/sgspe/project-components y su PATCH (api.md §4.2, RQ-GIS-010): versión nueva si cambian las coordenadas, 409
// si no, nunca una sobrescritura; el PATCH no toca coordenadas; la API genérica no escribe componentes (apiOnly)
class ProjectComponentsApiTest : DomainApiTest() {
    private fun register(
        project: String,
        token: String,
        location: Map<String, Any?>,
        code: String = "TST-PLT-1",
        extra: Map<String, Any?> = emptyMap()
    ) = exchange(
        "POST",
        "/api/sgspe/project-components",
        mapOf(
            "attributes" to mapOf("project" to project, "code" to code, "component_type" to "PLATAFORMA") + extra,
            "geometries" to mapOf("location" to location)
        ),
        token
    )

    private fun problemCodes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    @Test
    fun `alta, version nueva con su distancia, y 409 con las mismas coordenadas`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val first = register(project, analyst.token, point(-78.2, -7.9))
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val v1 = tree(first.body)
        assertTrue(v1["previousVersion"].isNull)
        val moved = tree(register(project, analyst.token, point(-78.2, -7.899)).body)
        assertEquals(2, moved["component"]["attributes"]["version_number"].asInt())
        assertEquals(v1["component"]["id"].asString(), moved["component"]["attributes"]["previous_version"].asString())
        assertTrue(moved["previousVersion"]["attributes"]["superseded"].asBoolean())
        assertTrue(moved["distanceM"].asDouble() in 100.0..120.0, moved["distanceM"].toString())
        val same = register(project, analyst.token, point(-78.2, -7.899))
        assertEquals(HttpStatus.CONFLICT, same.status, same.body)
        assertEquals(listOf("SAME_COORDINATES"), problemCodes(same.body))
    }

    @Test
    fun `el PATCH cambia datos con If-Match y rechaza geometrias y campos de identidad`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val component = tree(register(project, analyst.token, point(-78.2, -7.9)).body)["component"]
        val id = component["id"].asString()
        val ifMatch = mapOf(IfMatch.HEADER to "\"${component["updatedAt"].asString()}\"")
        assertEquals(
            listOf("geometries"),
            problemFields(
                patch(
                    "/api/sgspe/project-components/$id",
                    mapOf("geometries" to mapOf("location" to point(-78.0, -7.0))),
                    analyst.token,
                    ifMatch
                ).body
            )
        )
        assertEquals(
            listOf("attributes.code"),
            problemFields(patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("code" to "OTRO")), analyst.token, ifMatch).body)
        )
        assertEquals(
            HttpStatus.PRECONDITION_REQUIRED,
            patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), analyst.token).status
        )
        val changed = patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), analyst.token, ifMatch)
        assertEquals(HttpStatus.OK, changed.status, changed.body)
        assertEquals("EJECUTADO", tree(changed.body)["attributes"]["component_status"].asString())
        assertEquals(
            HttpStatus.PRECONDITION_FAILED,
            patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("dip" to 60)), analyst.token, ifMatch).status
        )
    }

    @Test
    fun `la API generica no escribe componentes`() {
        val project = projectWithCrs()
        val response =
            exchange(
                "POST",
                "/api/objects/project_component/records",
                mapOf("attributes" to mapOf("project" to project, "code" to "TST-X", "component_type" to "SONDAJE", "version_number" to 1))
            )
        assertEquals(HttpStatus.FORBIDDEN, response.status)
    }

    // P15: los rechazos de datos llevan su código y el campo, y una geometría que PostGIS no procesa es un 400, no un 500
    @Test
    fun `una geometria absurda, vacia o de otro tipo es un 400 con su codigo`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val absurd = register(project, analyst.token, point(1e12, 9123456.0), "TST-ABS-1")
        assertEquals(HttpStatus.BAD_REQUEST, absurd.status, absurd.body)
        assertEquals(listOf("INVALID_GEOMETRY"), problemCodes(absurd.body))
        assertEquals(listOf("geometries.location"), problemFields(absurd.body))
        val empty = register(project, analyst.token, mapOf("type" to "Point", "coordinates" to emptyList<Double>()), "TST-ABS-2")
        assertEquals(HttpStatus.BAD_REQUEST, empty.status, empty.body)
        assertEquals(listOf("INVALID_GEOMETRY"), problemCodes(empty.body))
        val wrongType =
            exchange(
                "POST",
                "/api/sgspe/project-components",
                mapOf(
                    "attributes" to mapOf("project" to project, "code" to "TST-ABS-3", "component_type" to "ACCESO"),
                    "geometries" to mapOf("trace" to point(-78.2, -7.9))
                ),
                analyst.token
            )
        assertEquals(HttpStatus.BAD_REQUEST, wrongType.status, wrongType.body)
        assertEquals(listOf("geometries.trace"), problemFields(wrongType.body))
        val unknown = exchange("POST", "/api/sgspe/project-components?x=1", mapOf("attributes" to emptyMap<String, Any?>()), analyst.token)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, unknown.status, unknown.body)
        val extra = exchange("POST", "/api/sgspe/project-components", mapOf("atributos" to emptyMap<String, Any?>()), analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, extra.status, extra.body)
        assertEquals(listOf("atributos"), problemFields(extra.body))
        val outOfRange = register(project, analyst.token, point(-78.2, -7.9), "TST-ABS-4", mapOf("dip" to 120))
        assertEquals(HttpStatus.BAD_REQUEST, outOfRange.status, outOfRange.body)
        assertEquals(listOf("attributes.dip"), problemFields(outOfRange.body))
        assertEquals(0, tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt())
    }

    // el alta toma el candado de la importación de componentes del proyecto: dos altas a la vez del mismo código se
    // ordenan (nunca un 500 por la clave única), y un código nuevo en las dos con las mismas coordenadas es una versión 1 y un 409
    @Test
    fun `dos altas simultaneas del mismo codigo no chocan con la clave unica`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val same =
            (1..2)
                .map { CompletableFuture.supplyAsync { register(project, analyst.token, point(-78.2, -7.9), "TST-PAR-1") } }
                .map { it.join() }
        assertEquals(listOf(HttpStatus.CREATED, HttpStatus.CONFLICT), same.map { it.status }.sortedBy { it.value() }, same.map { it.body }.toString())
        val moved =
            listOf(point(-78.2, -7.8), point(-78.2, -7.7))
                .map { location -> CompletableFuture.supplyAsync { register(project, analyst.token, location, "TST-PAR-2") } }
                .map { it.join() }
        assertTrue(moved.all { it.status == HttpStatus.CREATED }, moved.map { it.status to it.body }.toString())
        val versions = tree(send("GET", "/api/objects/project_component/records?project=$project&code=TST-PAR-2", null, HttpStatus.OK))["content"]
        assertEquals(setOf(1, 2), versions.values().map { it["attributes"]["version_number"].asInt() }.toSet())
        assertEquals(1, versions.values().count { !it["attributes"]["superseded"].asBoolean() })
    }

    // ---- cierre de la revisión de la Tarea 19 y ruling P25 ----

    private fun registerOf(
        project: String,
        token: String,
        geometries: Map<String, Any?>,
        code: String,
        type: String
    ) = exchange(
        "POST",
        "/api/sgspe/project-components",
        mapOf("attributes" to mapOf("project" to project, "code" to code, "component_type" to type), "geometries" to geometries),
        token
    )

    private fun updatedAtOf(id: String): String = record("project_component", id)["updatedAt"].asString()

    private fun ifMatch(id: String) = mapOf(IfMatch.HEADER to "\"${updatedAtOf(id)}\"")

    // un polígono en lazo (se cruza a sí mismo): PostGIS lo rechazaría, así que un 403 prueba que el permiso se miró antes
    private val bowTie =
        mapOf(
            "type" to "Polygon",
            "coordinates" to listOf(listOf(listOf(-78.0, -8.0), listOf(-77.9, -7.9), listOf(-77.9, -8.0), listOf(-78.0, -7.9), listOf(-78.0, -8.0)))
        )

    @Test
    fun `P25 un PATCH sobre una version reemplazada es 409 COMPONENT_SUPERSEDED, con If-Match vigente o viejo`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val v1 = tree(register(project, analyst.token, point(-78.2, -7.9), "TST-SUP-1").body)["component"]["id"].asString()
        val stale = ifMatch(v1)
        val v2 = tree(register(project, analyst.token, point(-78.2, -7.899), "TST-SUP-1").body)["component"]["id"].asString()
        for (headers in listOf(ifMatch(v1), stale)) {
            val response = patch("/api/sgspe/project-components/$v1", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), analyst.token, headers)
            assertEquals(HttpStatus.CONFLICT, response.status, response.body)
            assertEquals(listOf("COMPONENT_SUPERSEDED"), problemCodes(response.body))
            assertEquals(listOf("attributes"), problemFields(response.body))
        }
        assertEquals(null, record("project_component", v1)["attributes"]["component_status"]?.takeUnless { it.isNull }?.asString())
        // la vigente sí se cambia
        val current = patch("/api/sgspe/project-components/$v2", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), analyst.token, ifMatch(v2))
        assertEquals(HttpStatus.OK, current.status, current.body)
    }

    @Test
    fun `sin permiso o sin asignacion es 403 antes de cualquier cuenta de PostGIS`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val component = tree(register(project, analyst.token, point(-78.2, -7.9), "TST-403-1").body)["component"]
        val id = component["id"].asString()
        val footprint = mapOf("footprint" to bowTie)
        val readOnly = userWithRole("READ_ONLY")
        val outsider = member("GIS_ANALYST", projectWithCrs())
        for ((who, account) in listOf("sin permiso" to readOnly, "sin asignación" to outsider)) {
            val created = registerOf(project, account.token, footprint, "TST-403-2", "PLATAFORMA")
            assertEquals(HttpStatus.FORBIDDEN, created.status, "$who: ${created.body}")
            val changed =
                patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), account.token, ifMatch(id))
            assertEquals(HttpStatus.FORBIDDEN, changed.status, "$who: ${changed.body}")
            // un PATCH que no cambia nada tampoco depende de los datos: sigue siendo 403
            val unchanged =
                patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_type" to "PLATAFORMA")), account.token, ifMatch(id))
            assertEquals(HttpStatus.FORBIDDEN, unchanged.status, "$who: ${unchanged.body}")
        }
        // con permiso y asignación, el mismo lazo sí llega a PostGIS y es un 400
        assertEquals(HttpStatus.BAD_REQUEST, registerOf(project, analyst.token, footprint, "TST-403-2", "PLATAFORMA").status)
    }

    @Test
    fun `un PATCH que no cambia nada deja el updatedAt intacto`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val id = tree(register(project, analyst.token, point(-78.2, -7.9), "TST-NOOP-1", mapOf("dip" to 45)).body)["component"]["id"].asString()
        val before = updatedAtOf(id)
        val response =
            patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("dip" to 45, "component_type" to "PLATAFORMA")), analyst.token, ifMatch(id))
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertEquals(before, tree(response.body)["updatedAt"].asString())
        assertEquals(before, updatedAtOf(id))
    }

    @Test
    fun `una version nueva por trace o footprint, y el cambio de clase de geometria entre versiones`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)

        fun line(latitude: Double) = mapOf("type" to "LineString", "coordinates" to listOf(listOf(-78.2, latitude), listOf(-78.19, latitude + 0.01)))

        fun footprint(latitude: Double) = square(-78.2, latitude, 0.001)

        assertEquals(HttpStatus.CREATED, registerOf(project, analyst.token, mapOf("trace" to line(-7.9)), "TST-ACC-1", "ACCESO").status)
        val traceV2 = registerOf(project, analyst.token, mapOf("trace" to line(-7.89)), "TST-ACC-1", "ACCESO")
        assertEquals(HttpStatus.CREATED, traceV2.status, traceV2.body)
        assertEquals(2, tree(traceV2.body)["component"]["attributes"]["version_number"].asInt())
        assertTrue(tree(traceV2.body)["distanceM"].asDouble() > 1000.0, traceV2.body)
        assertEquals(HttpStatus.CONFLICT, registerOf(project, analyst.token, mapOf("trace" to line(-7.89)), "TST-ACC-1", "ACCESO").status)

        assertEquals(HttpStatus.CREATED, registerOf(project, analyst.token, mapOf("footprint" to footprint(-7.9)), "TST-PLT-F", "PLATAFORMA").status)
        val footprintV2 = registerOf(project, analyst.token, mapOf("footprint" to footprint(-7.899)), "TST-PLT-F", "PLATAFORMA")
        assertEquals(HttpStatus.CREATED, footprintV2.status, footprintV2.body)
        assertEquals(2, tree(footprintV2.body)["component"]["attributes"]["version_number"].asInt())
        assertTrue(tree(footprintV2.body)["previousVersion"]["attributes"]["superseded"].asBoolean())

        // la clase de geometría cambia entre versiones: no hay con qué comparar el 1 cm, así que es una versión nueva
        val asLocation = registerOf(project, analyst.token, mapOf("location" to point(-78.2, -7.9)), "TST-PLT-F", "PLATAFORMA")
        assertEquals(HttpStatus.CREATED, asLocation.status, asLocation.body)
        assertEquals(3, tree(asLocation.body)["component"]["attributes"]["version_number"].asInt())
    }

    @Test
    fun `exploration_campaign se cambia por PATCH`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val campaign = createRecord("exploration_campaign", sample("exploration_campaign") + mapOf("project" to project))
        val id =
            tree(
                register(project, analyst.token, point(-78.2, -7.9), "TST-CMP-1", mapOf("component_type" to "SONDAJE")).body
            )["component"]["id"].asString()
        val response = patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("exploration_campaign" to campaign)), analyst.token, ifMatch(id))
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertEquals(campaign, tree(response.body)["attributes"]["exploration_campaign"].asString())
        assertEquals(campaign, record("project_component", id)["attributes"]["exploration_campaign"].asString())
    }

    @Test
    fun `un proyecto mal formado lleva un codigo distinto del proyecto que falta`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)

        fun post(attributes: Map<String, Any?>) =
            exchange(
                "POST",
                "/api/sgspe/project-components",
                mapOf("attributes" to attributes, "geometries" to mapOf("location" to point(-78.2, -7.9))),
                analyst.token
            )

        val missing = post(mapOf("code" to "TST-PRJ-1", "component_type" to "SONDAJE"))
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.body)
        assertEquals(listOf("FIELD_REQUIRED"), problemCodes(missing.body))
        val malformed = post(mapOf("project" to "no-es-un-uuid", "code" to "TST-PRJ-1", "component_type" to "SONDAJE"))
        assertEquals(HttpStatus.BAD_REQUEST, malformed.status, malformed.body)
        assertEquals(listOf("INVALID_VALUE"), problemCodes(malformed.body))
        assertEquals(listOf("attributes.project"), problemFields(malformed.body))
    }
}
