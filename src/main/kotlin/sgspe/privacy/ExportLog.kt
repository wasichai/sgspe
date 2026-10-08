package sgspe.privacy

import org.springframework.stereotype.Component
import sgspe.common.Records
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.util.UUID

// una fila de export_log por cada salida (RQ-AUD-011, deliverables.md §9.5): quién, qué, con qué nivel, qué filtros y
// cuántas filas. export_log es appendOnly y apiOnly: solo la escriben los endpoints de sgspe, como el usuario que sale, con la
// marca de Records; nunca como la plataforma. leerla es de SOCIAL_MANAGER y AUDITOR, así que quien exporta puede no leer la
// fila que acaba de escribir. export_kind distingue exportación, emisión y descarga (estas dos, desde F8)
@Component
class ExportLog(
    private val records: Records,
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
            ) + listOfNotNull(project?.let { "project" to it.toString() }),
            "exportación $objectName ${disclosure.name}"
        )
    }

    companion object {
        const val OBJECT = "export_log"
        const val EXPORT = "EXPORTACION"
    }
}
