package sgspe.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import wasichai.notifications.Audience
import wasichai.notifications.NotificationDraft
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import wasichai.notifications.Notifications
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// los textos de los avisos, puros y en español (decisión D2: wasichai-notifications los entrega; SGSPE decide a quién y qué
// dice). ningún texto lleva datos personales: el código y el título del registro, sus fechas y el nivel (RQ-SEC-010)
object NoticeTexts {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun approvalPending(
        objectName: String,
        label: String
    ): Pair<String, String> =
        when (objectName) {
            "project" -> "Proyecto por revisar: $label" to "El proyecto espera su revisión para pasar a vigente."
            "engagement" -> "Acta por aprobar: $label" to "El acta de la interacción espera su aprobación."
            "commitment" -> "Compromiso por aprobar: $label" to "El compromiso espera su aprobación."
            else -> "Aprobación pendiente: $label" to "El registro espera su aprobación."
        }

    fun commitmentDueSoon(
        label: String,
        due: LocalDate
    ): Pair<String, String> = "Compromiso por vencer: $label" to "Vence el ${due.format(DATE)}."

    fun commitmentOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Compromiso vencido: $label" to "Venció el ${due.format(DATE)}."
        } else {
            "Compromiso vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierto: revise con su responsable."
        }

    fun taskOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Tarea vencida: $label" to "Venció el ${due.format(DATE)}."
        } else {
            "Tarea vencida, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierta."
        }
}

// la puerta de SGSPE a wasichai-notifications (decisión D2, WAS-ADR-0046, WAS-ADR-0060): bandeja, correo, preferencias y
// SSE son del módulo; aquí solo se publica y se resuelve con una clave estable por aviso, así un aviso repetido no se
// duplica (RF-NTF-001) y uno que ya no aplica se resuelve. Notifications.publish se une a la transacción de quien escribe:
// un aviso se confirma o se revierte con su escritura. un aviso mal formado (un error de SGSPE) se registra y no hace fallar
// la escritura del usuario; un destinatario que dejó la organización lo descarta el módulo con una advertencia
@Component
class Notices(
    private val notifications: Notifications
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // el id del aviso, o null si no quedó ningún destinatario
    suspend fun publish(
        organizationId: UUID,
        source: String,
        key: String,
        kind: NotificationKind,
        text: Pair<String, String>,
        users: Collection<UUID>,
        link: NotificationLink? = null,
        dueAt: Instant? = null
    ): UUID? {
        if (users.isEmpty()) return null
        return try {
            notifications.publish(organizationId, source, draft(key, kind, text, users, link, dueAt))
        } catch (e: IllegalArgumentException) {
            log.error("Notices: aviso {} {} mal formado, no se publicó", source, key, e)
            null
        }
    }

    suspend fun resolve(
        organizationId: UUID,
        source: String,
        key: String
    ): Boolean =
        try {
            notifications.resolve(organizationId, source, key)
        } catch (e: IllegalArgumentException) {
            log.error("Notices: clave {} {} mal formada, no se resolvió", source, key, e)
            false
        }

    companion object {
        // las fuentes de SGSPE en el módulo (^[a-z][a-z0-9_.:-]{1,80}$; nunca manual, rule:… ni automation:…)
        const val APPROVALS = "sgspe.approvals"
        const val DEADLINES = "sgspe.deadlines"

        // el borrador de un aviso a personas concretas, con su enlace al registro (la bandeja lo quita a quien no lee el objeto)
        fun draft(
            key: String,
            kind: NotificationKind,
            text: Pair<String, String>,
            users: Collection<UUID>,
            link: NotificationLink?,
            dueAt: Instant?
        ): NotificationDraft =
            NotificationDraft(
                kind = kind,
                title = text.first,
                audience = users.distinct().map { Audience.User(it) },
                body = text.second,
                link = link,
                key = key,
                dueAt = dueAt
            )
    }
}
