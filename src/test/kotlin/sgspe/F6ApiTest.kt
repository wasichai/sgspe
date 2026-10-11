package sgspe

import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.math.cos

// geometrías sintéticas para las pruebas de F6 (RQ-GIS-021): puntos, líneas y cuadrados alrededor de ORIGIN, en la zona UTM
// 17S (EPSG 32717). los metros pasan a grados con la aproximación equirectangular local, que cerca de ORIGIN y hasta unos
// 5 km se aleja menos de 0.5 % de la medida en UTM: las pruebas que miden comparan con tolerancia. ningún dato real
object SyntheticGeo {
    val ORIGIN: Pair<Double, Double> = -78.50 to -7.90

    // metros por grado de latitud (y de longitud en el ecuador)
    private const val METERS_PER_DEGREE = 111_320.0

    fun offset(
        lon: Double,
        lat: Double,
        dxM: Double,
        dyM: Double
    ): Pair<Double, Double> = (lon + dxM / (METERS_PER_DEGREE * cos(Math.toRadians(lat)))) to (lat + dyM / METERS_PER_DEGREE)

    fun point(
        lon: Double,
        lat: Double
    ): Map<String, Any> = mapOf("type" to "Point", "coordinates" to listOf(lon, lat))

    fun line(vararg lonLat: Pair<Double, Double>): Map<String, Any> {
        require(lonLat.size >= 2) { "una línea necesita al menos dos puntos" }
        return mapOf("type" to "LineString", "coordinates" to lonLat.map { listOf(it.first, it.second) })
    }

    // un cuadrado de `sideM` metros centrado en (lon, lat), con el anillo en sentido antihorario
    fun square(
        lon: Double,
        lat: Double,
        sideM: Double
    ): Map<String, Any> = mapOf("type" to "Polygon", "coordinates" to listOf(ring(lon, lat, sideM)))

    // el mismo cuadrado como MultiPolygon de una parte: community.territory y environmental_feature.boundary son MULTIPOLYGON
    fun multiSquare(
        lon: Double,
        lat: Double,
        sideM: Double
    ): Map<String, Any> = mapOf("type" to "MultiPolygon", "coordinates" to listOf(listOf(ring(lon, lat, sideM))))

    private fun ring(
        lon: Double,
        lat: Double,
        sideM: Double
    ): List<List<Double>> {
        require(sideM > 0) { "el lado del cuadrado debe ser positivo" }
        val half = sideM / 2
        val corners = listOf(-half to -half, half to -half, half to half, -half to half, -half to -half)
        return corners.map { (dx, dy) -> offset(lon, lat, dx, dy).let { listOf(it.first, it.second) } }
    }
}

// la base de las pruebas de integración de F6 (plan 6.1; los planes 6.2a y 6.2b la usan): lo de F5ApiTest más un proyecto en
// UTM 17S, localidades con y sin ubicación, comunidades con territorio y código, predios con su titular ficticio, componentes
// por el endpoint de versiones, elementos ambientales y una QCS confidencial, con geometrías de SyntheticGeo. las ayudas son
// suspend y devuelven UUID (contrato común de los planes de F6); por dentro usan las llamadas bloqueantes de las bases, así
// que se llaman desde runBlocking. todas escriben como el admin salvo confidentialGrievance
abstract class F6ApiTest : F5ApiTest() {
    // un proyecto con CRS de trabajo UTM 17S (EPSG 32717), el de ORIGIN: las medidas de F6 van en el working_crs (gis.md
    // §3.4). su equipo se arma con member(rol, proyecto.toString()), como en F5ApiTest
    protected suspend fun utmProject(code: String = uniqueName("PRY-UTM-")): UUID =
        UUID.fromString(project(mapOf("code" to code, "working_crs" to "EPSG 32717")))

    // una localidad CASERIO con su punto, o sin geometría (los dos casos de la ficha territorial y de NO_GEOMETRY)
    protected suspend fun locality(
        name: String,
        location: Map<String, Any>?
    ): UUID = UUID.fromString(createWith("locality", mapOf("name" to name, "locality_type" to "CASERIO"), geometries("location" to location)))

    // una comunidad campesina con su territorio (MULTIPOLYGON: SyntheticGeo.multiSquare) y, si se da, su código, único en el
    // tenant desde F6: dos comunidades con el mismo código chocan (409)
    protected suspend fun communityWithTerritory(
        name: String,
        territory: Map<String, Any>?,
        code: String? = null
    ): UUID =
        UUID.fromString(
            createWith(
                "community",
                mapOf("name" to name, "community_type" to "COMUNIDAD CAMPESINA", "code" to code).filterValues { it != null },
                geometries("territory" to territory)
            )
        )

    // un predio (POLYGON: SyntheticGeo.square) con su tenencia del catálogo (PROPIEDAD, POSESION, COMUNAL o PROPIEDAD DE LA
    // EMPRESA) y, si se da, su comunidad. el código es único en el tenant: usar uniqueName("PRD-")
    protected suspend fun parcel(
        code: String,
        tenure: String,
        boundary: Map<String, Any>,
        community: UUID? = null
    ): UUID =
        UUID.fromString(
            createWith(
                "land_parcel",
                mapOf("code" to code, "tenure_type" to tenure, "community" to community?.toString()).filterValues { it != null },
                mapOf("boundary" to boundary)
            )
        )

    // el titular ficticio de un predio: una persona con ese nombre y su derecho PROPIEDAD (land_right; el titular es un dato
    // restringido, security.md §7.9)
    protected suspend fun parcelOwner(
        parcel: UUID,
        ownerName: String
    ): UUID =
        UUID.fromString(
            createRecord("land_right", mapOf("land_parcel" to parcel.toString(), "holder_person" to person(ownerName), "tenure_type" to "PROPIEDAD"))
        )

    // un componente por POST /api/sgspe/project-components (project_component es apiOnly): la geometría decide el campo
    // (Point → location, LineString → trace, Polygon → footprint) y `type` es el component_type (SONDAJE, PLATAFORMA, ACCESO,
    // CAMPAMENTO o CAPTACION). un código nuevo en el proyecto es la versión 1
    protected suspend fun component(
        project: UUID,
        code: String,
        type: String,
        geometry: Map<String, Any>
    ): UUID {
        val field =
            COMPONENT_FIELDS[geometry["type"]]
                ?: throw IllegalArgumentException("un componente lleva Point, LineString o Polygon, no ${geometry["type"]}")
        val body =
            mapOf(
                "attributes" to mapOf("project" to project.toString(), "code" to code, "component_type" to type),
                "geometries" to mapOf(field to geometry)
            )
        return UUID.fromString(tree(send("POST", "/api/sgspe/project-components", body, HttpStatus.CREATED))["component"]["id"].asString())
    }

    // un elemento ambiental con su tipo del catálogo (ZONA DE PROTECCION, FUENTE DE AGUA, MICROCUENCA, CERRO, SITIO SAGRADO o
    // RESTO ARQUEOLOGICO), sus geometrías (boundary es MULTIPOLYGON) y, si se da, su código, único en el tenant desde F6
    protected suspend fun environmentalFeature(
        name: String,
        type: String,
        boundary: Map<String, Any>? = null,
        location: Map<String, Any>? = null,
        code: String? = null
    ): UUID =
        UUID.fromString(
            createWith(
                "environmental_feature",
                mapOf("name" to name, "feature_type" to type, "code" to code).filterValues { it != null },
                geometries("boundary" to boundary, "location" to location)
            )
        )

    // una queja confidencial del proyecto en la localidad. la registra un GRIEVANCE_MANAGER asignado (tiene
    // READ_CONFIDENTIAL, así que puede ser su receptor): la leen el ADMIN y quien tiene READ_CONFIDENTIAL, nunca un CRO ni un
    // ANALYST (security.md §9.6)
    protected suspend fun confidentialGrievance(
        project: UUID,
        locality: UUID
    ): UUID =
        UUID.fromString(
            grievance(
                project.toString(),
                member("GRIEVANCE_MANAGER", project.toString()),
                mapOf("confidential" to true, "locality" to locality.toString())
            )
        )

    private fun geometries(vararg pairs: Pair<String, Map<String, Any>?>): Map<String, Any?> = pairs.filter { it.second != null }.toMap()

    companion object {
        private val COMPONENT_FIELDS = mapOf("Point" to "location", "LineString" to "trace", "Polygon" to "footprint")
    }
}
