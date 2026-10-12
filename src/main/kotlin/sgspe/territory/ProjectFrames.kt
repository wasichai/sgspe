package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.toCodedException
import java.util.UUID

// el proyecto de una consulta y el CRS en que se mide (gis.md §3.2)
data class ProjectFrame(
    val id: UUID,
    val srid: Int
)

// lee el proyecto como quien llama: uno que no existe, de otro tenant o fuera de su alcance es 404 PROJECT_NOT_FOUND (no se
// distingue, como la API genérica); sin un working_crs de trabajo (32717 a 32719), 409 WORKING_CRS_REQUIRED: nunca se mide en
// grados
@Component
class ProjectFrames(
    private val records: Records
) {
    suspend fun of(id: UUID): ProjectFrame {
        val project =
            records.find(TerritoryNames.PROJECT, id)
                ?: throw Finding("PROJECT_NOT_FOUND", "El proyecto no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()
        val srid =
            Crs.srid(Values.text(project.attributes["working_crs"]))?.takeIf { Crs.isWorkingZone(it) }
                ?: throw Finding(
                    "WORKING_CRS_REQUIRED",
                    "El proyecto no tiene CRS de trabajo: defina working_crs para medir en metros",
                    "project",
                    status = HttpStatus.CONFLICT
                ).toCodedException()
        return ProjectFrame(id, srid)
    }
}
