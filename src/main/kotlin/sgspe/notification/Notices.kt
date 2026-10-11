package sgspe.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.common.Values
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

    // F4: una alerta nueva (WarningNoticeListener), por una persona o por JOB-04: su título y su severidad, que no llevan datos
    // personales (los de JOB-04 dicen la regla, el tipo, el conteo y el umbral)
    fun warningNew(
        title: String,
        severity: String?
    ): Pair<String, String> = "Alerta nueva: $title" to (severity?.let { "Severidad $it. " } ?: "") + "Inicie su triaje."

    fun approvalPending(
        objectName: String,
        label: String
    ): Pair<String, String> =
        when (objectName) {
            "project" -> "Proyecto por revisar: $label" to "El proyecto espera su revisión para pasar a vigente."
            "engagement" -> "Acta por aprobar: $label" to "El acta de la interacción espera su aprobación."
            "commitment" -> "Compromiso por aprobar: $label" to "El compromiso espera su aprobación."
            "social_contribution" -> "Aporte por aprobar: $label" to "El aporte espera su aprobación: revise sus ítems y los lineamientos."
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

    // F4: un riesgo con la revisión vencida (DeadlineNotices, ASM-099). `label` es «Riesgo <código>»
    fun riskReviewOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Revisión de riesgo vencida: $label" to "La revisión debía hacerse el ${due.format(DATE)}."
        } else {
            "Revisión de riesgo vencida, escalamiento $level: $label" to "La revisión debía hacerse el ${due.format(DATE)} y sigue pendiente."
        }

    // F4: el plazo de una QCS (ASM-100, ASM-102). `label` es «Queja <código>» o «Consulta <código>»: el tipo y el código, nunca
    // la descripción, la persona ni otro texto libre
    fun qcsDueSoon(
        label: String,
        due: LocalDate
    ): Pair<String, String> = "Plazo por vencer: $label" to "Vence el ${due.format(DATE)}."

    fun qcsOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Plazo vencido: $label" to "Venció el ${due.format(DATE)}."
        } else {
            "Plazo vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue sin atender."
        }

    // el aviso genérico del nivel 2 de una QCS confidencial a quien no la ve (ASM-102): fijo salvo el nombre del proyecto, sin
    // código, título, tipo, fecha, nivel, categoría ni enlace al registro
    fun qcsConfidentialOverdue(project: String): Pair<String, String> =
        "QCS confidencial fuera de plazo" to "Una QCS confidencial del proyecto $project superó su plazo. Pida el detalle al equipo de quejas."

    // el título y la descripción de la tarea de escalamiento de los niveles 1 y 2 de una QCS confidencial (JOB-01, D-1): fijos
    // salvo el nombre del proyecto y el nivel, como el aviso genérico
    fun qcsConfidentialEscalation(
        project: String,
        level: Int
    ): Pair<String, String> =
        "QCS confidencial vencida en $project" to
            "Una QCS confidencial superó su plazo (escalamiento de nivel $level). Pida el detalle al equipo de quejas."

    // los de la tarea de acuse genérica de una queja confidencial (ConfidentialTasksListener, D-1), en lugar de la de AUT-02
    fun qcsConfidentialAck(project: String): Pair<String, String> =
        "Acusar recibo de una QCS confidencial en $project" to "Una QCS confidencial espera su acuse. Pida el detalle al equipo de quejas."

    // cómo nombran el aviso y la tarea genéricos al proyecto: su nombre, su código o, sin ellos, su id
    fun projectName(
        project: Map<String, Any?>,
        id: String
    ): String = Values.text(project["name"]) ?: Values.text(project["code"]) ?: id

    // F4: un riesgo cuya revisión sube su nivel a ALTO o CRITICO (RiskNoticeListener). `label` es «Riesgo <código>»
    fun riskEscalated(
        label: String,
        level: String
    ): Pair<String, String> = "Riesgo escalado a $level: $label" to "La última revisión dejó el riesgo en nivel $level: decida su tratamiento."

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
        // las fuentes de SGSPE en el módulo (^[a-z][a-z0-9_.:-]{1,80}$; nunca manual, rule:… ni automation:…). desde F4, también
        // las alertas nuevas (WarningNoticeListener)
        const val WARNINGS = "sgspe.warnings"
        const val APPROVALS = "sgspe.approvals"
        const val DEADLINES = "sgspe.deadlines"

        // F4: los avisos de nivel de riesgo (RiskNoticeListener)
        const val RISKS = "sgspe.risks"

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
