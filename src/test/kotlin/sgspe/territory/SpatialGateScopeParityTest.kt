package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import sgspe.F6QueryApiTest
import sgspe.common.Records
import java.util.UUID

// la paridad de SpatialGate con Records (especificación de F6 §2.2 elección 3 y §5, riesgo 5): el mismo objeto leído por
// Records (RecordService, con sus RecordReadScope) y por la compuerta da los mismos ids, en un objeto raíz (componente), un
// hijo (miembro de área), una QCS confidencial, el proyecto y un maestro del tenant (predio), para una analista asignada,
// el ADMIN y una analista sin asignación. si wasichai cambia cuándo pide los alcances, esta prueba lo nota. la sonda solo
// existe en el contexto de esta clase (@TestConfiguration anidada): una ruta funcional que lee de las dos formas en la
// misma petición, como la misma persona
class SpatialGateScopeParityTest : F6QueryApiTest() {
    @TestConfiguration
    class Probe {
        @Bean
        fun gateParityProbe(
            gate: SpatialGate,
            records: Records
        ): RouterFunction<ServerResponse> =
            coRouter {
                GET("/api/sgspe/test/gate-parity") { request ->
                    val objectName = request.queryParam("object").orElseThrow()
                    val ids =
                        request
                            .queryParam("ids")
                            .orElseThrow()
                            .split(",")
                            .map(UUID::fromString)
                    val viaRecords = records.all(objectName, ids = ids).map { it.id }.sorted()
                    val outcome =
                        gate.select(listOf(GateRead(objectName, emptySet(), principal = true)), { bind ->
                            val id = column(objectName, SpatialGate.ID)
                            "SELECT t.$id AS id FROM ${table(objectName)} t WHERE t.$id = ANY(${bind(ids.toTypedArray())})"
                        }) { row -> row.get("id", UUID::class.java)!!.toString() }
                    val viaGate =
                        when (outcome) {
                            is GateOutcome.Rows -> outcome.rows.sorted()
                            is GateOutcome.Unavailable -> error("una consulta sin campos no puede quedar no disponible")
                        }
                    ServerResponse.ok().bodyValueAndAwait(mapOf("records" to viaRecords, "gate" to viaGate))
                }
            }
    }

    private class Data(
        val a: UUID,
        val b: UUID,
        val componentA: UUID,
        val componentB: UUID,
        val memberA: UUID,
        val memberB: UUID,
        val openA: UUID,
        val hiddenA: UUID,
        val openB: UUID,
        val parcel: UUID
    )

    private fun data(): Data =
        runBlocking {
            val a = utmProject()
            val b = utmProject()
            val place = locality("Caserío Ejemplo", at(50.0, 50.0))
            val componentA = component(a, uniqueName("PLT-"), "PLATAFORMA", rect(0.0, 0.0, 100.0, 100.0))
            val componentB = component(b, uniqueName("PLT-"), "PLATAFORMA", rect(200.0, 0.0, 300.0, 100.0))
            val memberA = areaMember(area(a), "locality", place)
            val memberB = areaMember(area(b), "locality", place)
            val openA = UUID.fromString(grievance(a.toString(), attributes = mapOf("locality" to place.toString())))
            val hiddenA = confidentialGrievance(a, place)
            val openB = UUID.fromString(grievance(b.toString()))
            val parcel = parcel(uniqueName("PR-"), "PROPIEDAD", rect(0.0, 0.0, 500.0, 500.0))
            Data(a, b, componentA, componentB, memberA, memberB, openA, hiddenA, openB, parcel)
        }

    // los ids que leen las dos vías, que tienen que ser los mismos
    private fun parity(
        objectName: String,
        ids: List<UUID>,
        token: String
    ): List<String> {
        val body = get("/api/sgspe/test/gate-parity?object=$objectName&ids=${ids.joinToString(",")}", token)
        val viaRecords = body["records"].values().map { it.asString() }
        assertEquals(viaRecords, body["gate"].values().map { it.asString() }, "$objectName por Records y por la compuerta")
        return viaRecords
    }

    private fun ids(vararg ids: UUID): List<String> = ids.map { it.toString() }.sorted()

    @Test
    fun `una analista asignada a un proyecto lee lo mismo por Records y por la compuerta`() {
        val d = data()
        val analyst = member("ANALYST", d.a.toString()).token
        assertEquals(ids(d.componentA), parity("project_component", listOf(d.componentA, d.componentB), analyst))
        assertEquals(ids(d.memberA), parity("area_of_influence_member", listOf(d.memberA, d.memberB), analyst))
        assertEquals(ids(d.openA), parity("grievance", listOf(d.openA, d.hiddenA, d.openB), analyst))
        assertEquals(ids(d.a), parity("project", listOf(d.a, d.b), analyst))
        assertEquals(ids(d.parcel), parity("land_parcel", listOf(d.parcel), analyst))
    }

    @Test
    fun `el ADMIN lee todo por las dos vias`() {
        val d = data()
        assertEquals(ids(d.componentA, d.componentB), parity("project_component", listOf(d.componentA, d.componentB), token))
        assertEquals(ids(d.memberA, d.memberB), parity("area_of_influence_member", listOf(d.memberA, d.memberB), token))
        assertEquals(ids(d.openA, d.hiddenA, d.openB), parity("grievance", listOf(d.openA, d.hiddenA, d.openB), token))
        assertEquals(ids(d.a, d.b), parity("project", listOf(d.a, d.b), token))
    }

    @Test
    fun `una analista sin asignacion no lee nada de proyecto por ninguna via y si los maestros`() {
        val d = data()
        val outsider = userWithRole("ANALYST").token
        assertEquals(emptyList<String>(), parity("project_component", listOf(d.componentA, d.componentB), outsider))
        assertEquals(emptyList<String>(), parity("area_of_influence_member", listOf(d.memberA, d.memberB), outsider))
        assertEquals(emptyList<String>(), parity("grievance", listOf(d.openA, d.hiddenA, d.openB), outsider))
        assertEquals(emptyList<String>(), parity("project", listOf(d.a, d.b), outsider))
        assertEquals(ids(d.parcel), parity("land_parcel", listOf(d.parcel), outsider))
    }
}
