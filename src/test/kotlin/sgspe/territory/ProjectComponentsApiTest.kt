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
}
