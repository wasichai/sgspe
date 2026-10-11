package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6ApiTest
import sgspe.SyntheticGeo
import java.util.UUID

// el modelo de F6 contra wasichai de verdad (§3.4 y §4.1 de la especificación de F6, ASM-123): community y
// environmental_feature guardan su código y sus campos de origen, y el código no se repite; y cada ayuda de F6ApiTest arma
// lo que promete, para que 6.2a y 6.2b partan de una base probada
class TerritoryModelApiTest : F6ApiTest() {
    private val lon = SyntheticGeo.ORIGIN.first
    private val lat = SyntheticGeo.ORIGIN.second

    @Test
    fun `comunidad y elemento ambiental guardan su codigo y sus campos de origen, y el codigo no se repite`() {
        runBlocking {
            val batch = UUID.randomUUID().toString()
            val code = uniqueName("CC-")
            val community =
                createWith(
                    "community",
                    mapOf(
                        "name" to "Comunidad Ejemplo",
                        "code" to code,
                        "source_crs" to "EPSG 32717",
                        "source_reference" to "Padrón ficticio de prueba",
                        "import_batch_id" to batch
                    ),
                    mapOf("territory" to SyntheticGeo.multiSquare(lon, lat, 500.0))
                )
            val stored = record("community", community)["attributes"]
            assertEquals(
                listOf(code, "EPSG 32717", "Padrón ficticio de prueba", batch),
                listOf("code", "source_crs", "source_reference", "import_batch_id").map { stored[it].asString() }
            )
            val repeated = exchange("POST", "/api/objects/community/records", mapOf("attributes" to mapOf("name" to "Comunidad Ejemplo B", "code" to code)))
            assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
            // sin código, las comunidades no chocan: el único de PostgreSQL no compara los NULL (Review Focus 1)
            communityWithTerritory("Comunidad Ejemplo C", null)
            communityWithTerritory("Comunidad Ejemplo D", null)
            val featureCode = uniqueName("EA-")
            environmentalFeature("Microcuenca Ejemplo", "MICROCUENCA", boundary = SyntheticGeo.multiSquare(lon, lat, 800.0), code = featureCode)
            val again =
                exchange(
                    "POST",
                    "/api/objects/environmental_feature/records",
                    mapOf("attributes" to mapOf("name" to "Microcuenca Ejemplo B", "feature_type" to "MICROCUENCA", "code" to featureCode))
                )
            assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        }
    }

    @Test
    fun `las ayudas de F6ApiTest arman el territorio sintetico`() {
        runBlocking {
            val project = utmProject()
            assertEquals("EPSG 32717", record("project", project.toString())["attributes"]["working_crs"].asString())
            val located = locality("Caserío Ejemplo", SyntheticGeo.point(lon, lat))
            val bare = locality("Anexo Ejemplo", null)
            assertEquals("Point", record("locality", located.toString())["geometries"]["location"]["type"].asString())
            val bareLocation = record("locality", bare.toString())["geometries"].path("location")
            assertTrue(bareLocation.isNull || bareLocation.isMissingNode, bareLocation.toString())
            val community = communityWithTerritory("Comunidad Ejemplo", SyntheticGeo.multiSquare(lon, lat, 2000.0), uniqueName("CC-"))
            assertEquals("MultiPolygon", record("community", community.toString())["geometries"]["territory"]["type"].asString())
            val parcel = parcel(uniqueName("PRD-"), "PROPIEDAD", SyntheticGeo.square(lon, lat, 300.0), community)
            assertEquals(community.toString(), record("land_parcel", parcel.toString())["attributes"]["community"].asString())
            val owner = parcelOwner(parcel, "Titular Ejemplo")
            assertEquals(parcel.toString(), record("land_right", owner.toString())["attributes"]["land_parcel"].asString())
            val (east, _) = SyntheticGeo.offset(lon, lat, 800.0, 0.0)
            val platform = component(project, "TST-PLT-1", "PLATAFORMA", SyntheticGeo.square(east, lat, 60.0))
            val access =
                component(
                    project,
                    "TST-ACC-1",
                    "ACCESO",
                    SyntheticGeo.line(SyntheticGeo.offset(lon, lat, -1000.0, 0.0), SyntheticGeo.offset(lon, lat, 1000.0, 0.0))
                )
            assertEquals("Polygon", record("project_component", platform.toString())["geometries"]["footprint"]["type"].asString())
            assertEquals("LineString", record("project_component", access.toString())["geometries"]["trace"]["type"].asString())
            val feature = environmentalFeature("Sitio Ejemplo", "SITIO SAGRADO", location = SyntheticGeo.point(lon, lat))
            assertEquals("SITIO SAGRADO", record("environmental_feature", feature.toString())["attributes"]["feature_type"].asString())
            val grievance = confidentialGrievance(project, located)
            assertEquals(located.toString(), record("grievance", grievance.toString())["attributes"]["locality"].asString())
            // la confidencial no la lee un CRO del proyecto, sí otro GRIEVANCE_MANAGER (READ_CONFIDENTIAL, security.md §9.6)
            val officer = member("COMMUNITY_RELATIONS_OFFICER", project.toString())
            assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/grievance/records/$grievance", token = officer.token).status)
            val manager = member("GRIEVANCE_MANAGER", project.toString())
            assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/grievance/records/$grievance", token = manager.token).status)
        }
    }
}
