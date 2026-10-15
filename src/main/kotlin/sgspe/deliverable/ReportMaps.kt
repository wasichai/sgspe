package sgspe.deliverable

import sgspe.privacy.ReportMapImages
import java.util.UUID

// qué geometrías encuadran un mapa (especificación de F8 §3.7 paso 1; decisión 9 del plan 8.2c): T2 portada, el territorio
// comunal y la ubicación del proyecto; T2 predio, el territorio comunal; T1 áreas, las versiones de área y la ubicación del
// proyecto; T1 Anexo 1, el área de interés, la ubicación y las concesiones del proyecto
enum class MapFrameKind { COMMUNITY_AND_PROJECT, COMMUNITY, AREAS, INTEREST_AND_CONCESSIONS }

// lo que un tipo pide dibujar (especificación de F8 §3.7): la clave del bloque, el proyecto y su working_crs, la comunidad,
// las capas LYR-NN, el encuadre, las versiones de área (LYR-13), las concesiones del proyecto (en línea con estilo propio),
// si va en el marco horizontal de 24 × 16 cm y la organización autora de la fuente
data class MapRequest(
    val key: String,
    val project: UUID,
    val workingCrs: String,
    val community: UUID?,
    val layers: List<String>,
    val frame: MapFrameKind = MapFrameKind.COMMUNITY,
    val areas: List<UUID> = emptyList(),
    val concessions: List<UUID> = emptyList(),
    val landscape: Boolean = false,
    val author: String? = null
)

// el puerto de los mapas de informe (§2.2.17): solo JOB-05 lo llama, dentro de su corrida como la plataforma; devuelve la
// imagen INTERNO y la EXTERNO (con la divulgación por capa de F6), o ninguna si el mapa falló (MAP_FAILED). GeoServerReportMaps
// es el de producción (plan 8.2c); las pruebas de JOB-05 usan un doble
fun interface ReportMaps {
    suspend fun draw(
        organizationId: UUID,
        request: MapRequest
    ): ReportMapImages
}
