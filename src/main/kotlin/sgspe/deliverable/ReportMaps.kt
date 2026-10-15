package sgspe.deliverable

import org.springframework.stereotype.Component
import sgspe.privacy.ReportMapImages
import java.util.UUID

// lo que un tipo pide dibujar (especificación de F8 §3.7): la clave del bloque, el proyecto y su working_crs, la comunidad
// del encuadre y las capas LYR-NN del mapa. el encuadre, el GetMap y la composición son del plan 8.2c
data class MapRequest(
    val key: String,
    val project: UUID,
    val workingCrs: String,
    val community: UUID?,
    val layers: List<String>
)

// el puerto de los mapas de informe (§2.2.17): solo JOB-05 lo llama, dentro de su corrida como la plataforma; devuelve la
// imagen INTERNO y la EXTERNO (con la divulgación por capa de F6), o ninguna si el mapa falló (MAP_FAILED). ReportMaps de
// GeoServer llega con el plan 8.2c; las pruebas usan un doble
fun interface ReportMaps {
    suspend fun draw(
        organizationId: UUID,
        request: MapRequest
    ): ReportMapImages
}

// los mapas de un backend sin GeoServer para informes (plan 8.2b): todo mapa falla, el borrador sale LISTO con MAP_FAILED y
// la emisión responde 409 MAP_FAILED (§3.7, paso 5). 8.2c lo reemplaza
@Component
class UnavailableReportMaps : ReportMaps {
    override suspend fun draw(
        organizationId: UUID,
        request: MapRequest
    ): ReportMapImages = ReportMapImages()
}
