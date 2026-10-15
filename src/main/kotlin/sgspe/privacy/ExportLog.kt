package sgspe.privacy

import org.springframework.stereotype.Component
import sgspe.common.ProjectReach
import sgspe.common.Records
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.util.UUID

// una fila de export_log por cada salida (RQ-AUD-011, deliverables.md §9.5): quién, qué, con qué nivel, qué filtros y
// cuántas filas. export_log es appendOnly y apiOnly: solo la escriben los endpoints de sgspe, como el usuario que sale, con la
// marca de Records; nunca como la plataforma. leerla es de SOCIAL_MANAGER y AUDITOR, así que quien exporta puede no leer la
// fila que acaba de escribir. export_kind distingue exportación, emisión y descarga (estas dos, desde F8). la fila lleva el
// proyecto solo si quien exporta es ADMIN o está asignado a él: quien lo lee todo sin estar asignado (READ_ALL_PROJECTS) no
// escribe en ese proyecto (ProjectScopeGuard respondería 403), y el proyecto queda en filters_description
@Component
class ExportLog(
    private val records: Records,
    private val reach: ProjectReach,
    private val clock: Clock
) {
    suspend fun export(
        user: AuthenticatedUser,
        objectName: String,
        format: String,
        disclosure: Disclosure,
        filters: String,
        rows: Int,
        project: UUID?
    ) {
        val logged = if (project == null || user.isAdmin || project in reach.assigned(user)) project else null
        records.create(
            OBJECT,
            mapOf(
                "exported_at" to clock.instant().toString(),
                "exporter_user_id" to user.userId.toString(),
                "exporter_user_email" to user.email,
                "export_kind" to EXPORT,
                "object_name" to objectName,
                "file_format" to format,
                "disclosure_level" to disclosure.name,
                "filters_description" to filters,
                "row_count" to rows
            ) + listOfNotNull(logged?.let { "project" to it.toString() }),
            "exportación $objectName ${disclosure.name}"
        )
    }

    // una emisión o una descarga de un archivo de entregable (desde F8, deliverables.md §9.5): `kind` es EMISION o DESCARGA,
    // con el entregable (un borrador) o la versión; filters_description dice qué se emitió o descargó (el borrador por su
    // instante de congelamiento, la versión por su número) y, en la emisión, la confirmación de la lista de verificación
    suspend fun deliverable(
        user: AuthenticatedUser,
        kind: String,
        level: String,
        format: String,
        filters: String,
        project: UUID?,
        deliverable: UUID?,
        version: UUID?
    ) {
        val logged = if (project == null || user.isAdmin || project in reach.assigned(user)) project else null
        records.create(
            OBJECT,
            mapOf(
                "exported_at" to clock.instant().toString(),
                "exporter_user_id" to user.userId.toString(),
                "exporter_user_email" to user.email,
                "export_kind" to kind,
                "object_name" to DELIVERABLE,
                "file_format" to format,
                "disclosure_level" to level,
                "filters_description" to filters,
                "row_count" to 1
            ) +
                listOfNotNull(
                    logged?.let { "project" to it.toString() },
                    deliverable?.let { DELIVERABLE to it.toString() },
                    version?.let { "deliverable_version" to it.toString() }
                ),
            "${kind.lowercase()} de entregable $level"
        )
    }

    companion object {
        const val OBJECT = "export_log"
        const val EXPORT = "EXPORTACION"
        const val ISSUE = "EMISION"
        const val DOWNLOAD = "DESCARGA"
        private const val DELIVERABLE = "deliverable"
    }
}
