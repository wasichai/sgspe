package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus
import sgspe.common.IfMatch
import tools.jackson.databind.JsonNode
import java.util.UUID

// la base de las pruebas de integración del plan 6.2a (sobre F6ApiTest del plan 6.1): geometrías en metros desde
// SyntheticGeo.ORIGIN (UTM 17S) armadas solo con SyntheticGeo.offset, así no dependen de si square toma la esquina o el
// centro; localidades con límite, unidades, áreas de influencia con sus miembros y su aprobación; y las lecturas de los
// endpoints de consulta
abstract class F6QueryApiTest : F6ApiTest() {
    private fun corner(
        dxM: Double,
        dyM: Double
    ): List<Double> {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val (x, y) = SyntheticGeo.offset(lon, lat, dxM, dyM)
        return listOf(x, y)
    }

    // un punto a dxM al este y dyM al norte del origen
    protected fun at(
        dxM: Double,
        dyM: Double
    ): Map<String, Any> = mapOf("type" to "Point", "coordinates" to corner(dxM, dyM))

    // el rectángulo de (x0, y0) a (x1, y1) metros; `multi` lo da como MultiPolygon de una parte (territorio comunal, áreas)
    protected fun rect(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        multi: Boolean = false
    ): Map<String, Any> {
        val ring = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1, x0 to y0).map { (dx, dy) -> corner(dx, dy) }
        return if (multi) {
            mapOf("type" to "MultiPolygon", "coordinates" to listOf(listOf(ring)))
        } else {
            mapOf("type" to "Polygon", "coordinates" to listOf(ring))
        }
    }

    protected fun path(vararg points: Pair<Double, Double>): Map<String, Any> =
        mapOf("type" to "LineString", "coordinates" to points.map { (dx, dy) -> corner(dx, dy) })

    protected fun localityWith(
        name: String = "Caserío Ejemplo",
        boundary: Map<String, Any?>? = null,
        location: Map<String, Any?>? = null,
        type: String = "CASERIO",
        attributes: Map<String, Any?> = emptyMap()
    ): UUID =
        UUID.fromString(
            createWith(
                "locality",
                mapOf("name" to name, "locality_type" to type) + attributes,
                listOfNotNull(boundary?.let { "boundary" to it }, location?.let { "location" to it }).toMap()
            )
        )

    protected fun unit(
        level: String,
        boundary: Map<String, Any?>
    ): UUID =
        UUID.fromString(
            createWith(
                "administrative_unit",
                mapOf("ubigeo_code" to uniqueName("U"), "name" to "Unidad Ejemplo $level", "level" to level),
                mapOf("boundary" to boundary),
                headers = withReason("alta de prueba")
            )
        )

    protected fun area(
        project: UUID,
        type: String = "AIDS",
        version: Int = 1,
        boundary: Map<String, Any?>? = null
    ): UUID =
        UUID.fromString(
            createWith(
                "area_of_influence",
                mapOf("project" to project.toString(), "area_type" to type, "version_number" to version),
                if (boundary == null) emptyMap() else mapOf("boundary" to boundary)
            )
        )

    protected fun areaMember(
        area: UUID,
        field: String,
        target: UUID,
        condition: String = "INCLUIDO"
    ): UUID =
        UUID.fromString(
            createRecord("area_of_influence_member", mapOf("area_of_influence" to area.toString(), field to target.toString(), "member_condition" to condition))
        )

    // la aprobación de una versión (WF-O) por una gerencia social asignada al proyecto
    protected fun approveArea(
        area: UUID,
        project: UUID
    ) {
        val manager = member("SOCIAL_MANAGER", project.toString())
        send(
            "POST",
            "/api/sgspe/areas-of-influence/$area/approve",
            null,
            HttpStatus.OK,
            manager.token,
            mapOf(IfMatch.HEADER to "\"${record("area_of_influence", area.toString())["updatedAt"].asString()}\"", REASON to reason("aprobación de prueba"))
        )
    }

    protected fun get(
        path: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", path, null, HttpStatus.OK, token))

    // un valor null en la respuesta: viaja como null o, si el mapeador de wasichai omite los nulos, no viaja
    protected fun absent(node: JsonNode?): Boolean = node == null || node.isNull

    // el código de error de una consulta que tiene que responder `status`
    protected fun failing(
        path: String,
        status: HttpStatus,
        token: String = this.token
    ): String {
        val response = exchange("GET", path, null, token)
        assertEquals(status, response.status, "GET $path: ${response.body}")
        return code(response)
    }
}
