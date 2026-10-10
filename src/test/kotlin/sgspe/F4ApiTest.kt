package sgspe

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import sgspe.common.Records
import java.util.UUID

// la base de las pruebas de integración de F4 (plan 4.1; los planes 4.2a y 4.2b la usan para las suyas): lo de
// F3DomainApiTest más quejas, consultas, solicitudes, riesgos, medidas, incidentes, menciones, señales, reglas y alertas que
// ya cumplen las guardas de 4.2 en todo su ciclo (AttentionWriteGuard: no anónimas ni confidenciales, con categoría,
// severidad BAJA, resolución, respuesta y respuesta comunicada; RiskWriteGuard: responsable y motivo de cierre;
// WarningWriteGuard: un incidente con su respuesta inmediata y sus consecuencias, una regla con umbral entero y ventana),
// las revisiones de riesgo como la plataforma (risk_assessment es apiOnly y appendOnly: su endpoint es del plan 4.2b) y
// la escritura de un derivado como la plataforma, como la harán ese endpoint y JOB-01
abstract class F4ApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var records: Records

    // una escritura de solo derivados como la plataforma (Records.patch con la marca SgspeWrite y su motivo): AUT-11 mira
    // el cambio de risk_level, que en 4.2b escribe el endpoint de revisiones
    protected fun platformPatch(
        objectName: String,
        id: String,
        changes: Map<String, Any?>,
        reason: String = "derivado de prueba como la plataforma"
    ) {
        runBlocking { records.asPlatform(organizationId()) { records.patch(objectName, UUID.fromString(id), changes, reason) } }
    }

    // los campos comunes de las tres QCS (ASM-011) con lo que AttentionWriteGuard pide a lo largo de WF-E1, WF-E2 y WF-E3:
    // ni anónima ni confidencial, con categoría, respuesta y respuesta comunicada (responded_at). quien la crea queda como
    // quien la recibió, como en el portal (S37); el admin no, porque su cuenta no es un usuario de la organización
    protected fun attentionAttributes(
        project: String,
        receiver: Account?,
        prefix: String
    ): Map<String, Any?> =
        mapOf(
            "project" to project,
            "code" to uniqueName(prefix),
            "channel" to "PRESENCIAL",
            "received_at" to "2026-10-05T15:00:00Z",
            "anonymous" to false,
            "confidential" to false,
            "category" to "AGUA Y MEDIO AMBIENTE",
            "description" to "Planteamiento de prueba sobre el canal de riego.",
            "priority" to "MEDIA",
            "response" to "Respuesta de prueba comunicada en el local comunal.",
            "responded_at" to "2026-10-08T15:00:00Z"
        ) + (receiver?.let { mapOf("received_by_user_id" to it.id, "received_by_user_email" to it.email) } ?: emptyMap())

    // una queja en received: severidad BAJA, para que `resolve` no pida la aprobación de las graves (RN-GRV-002), con su
    // resolución y sin conformidad del reclamante, para que `reopen` pase
    protected fun grievance(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "grievance",
            attentionAttributes(project, creator, "QJ-") +
                mapOf(
                    "severity" to "BAJA",
                    "resolution" to "Resolución de prueba: se reparó el canal de riego.",
                    "complainant_conformity_given" to false
                ) + attributes,
            withReason("alta de prueba"),
            creator?.token ?: token
        )

    protected fun inquiry(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String = createRecord("inquiry", attentionAttributes(project, creator, "CO-") + attributes, withReason("alta de prueba"), creator?.token ?: token)

    protected fun stakeholderRequest(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "stakeholder_request",
            attentionAttributes(project, creator, "SO-") +
                mapOf("request_medium" to "CARTA DE SOLICITUD", "requested_resource" to "Materiales para el canal de riego.") + attributes,
            withReason("alta de prueba"),
            creator?.token ?: token
        )

    // un riesgo en identified con lo que pide RiskWriteGuard (plan 4.2b): un responsable para `assess` y el motivo de cierre
    // para las `close_*`
    protected fun socialRisk(
        project: String,
        creator: Account? = null,
        owner: Account = userWithRole("SOCIAL_RISK_MANAGER"),
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "social_risk",
            mapOf(
                "project" to project,
                "code" to uniqueName("RS-"),
                "title" to "Expectativas de empleo local",
                "category" to "EXPECTATIVAS Y BENEFICIOS",
                "owner_user_id" to owner.id,
                "owner_user_email" to owner.email,
                "close_reason" to "El riesgo dejó de existir."
            ) + attributes,
            withReason("alta de prueba"),
            creator?.token ?: token
        )

    // una revisión de riesgo como la plataforma, con su puntaje P × I y su nivel con los umbrales por defecto (ASM-004)
    protected fun riskAssessment(
        risk: String,
        assessor: Account,
        probability: Int = 3,
        impact: Int = 4,
        residual: Boolean = false,
        date: String = "2026-10-07"
    ): String =
        platformCreate(
            "risk_assessment",
            mapOf(
                "social_risk" to risk,
                "assessment_date" to date,
                "probability" to probability,
                "impact" to impact,
                "score" to probability * impact,
                "risk_level" to riskLevel(probability * impact),
                "residual" to residual,
                "assessor_user_id" to assessor.id,
                "assessor_user_email" to assessor.email
            )
        )

    // una medida con responsable, la que `decide_mitigation` y `resume_mitigation` exigen (RN-RSK-003)
    protected fun mitigationMeasure(
        risk: String,
        owner: Account,
        creator: Account? = null
    ): String =
        createRecord(
            "mitigation_measure",
            mapOf(
                "social_risk" to risk,
                "description" to "Mesa de trabajo sobre empleo local.",
                "owner_user_id" to owner.id,
                "owner_user_email" to owner.email,
                "planned_date" to "2026-11-15"
            ),
            token = creator?.token ?: token
        )

    // un incidente en reported que `close` de WF-M deja cerrar: con su respuesta inmediata y sus consecuencias (RF-EWS-001)
    protected fun incident(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "incident",
            mapOf(
                "project" to project,
                "code" to uniqueName("IN-"),
                "incident_type" to "BLOQUEO DE VIA",
                "occurred_at" to "2026-10-06T13:00:00Z",
                "place" to "Acceso a la plataforma de perforación",
                "description" to "Bloqueo del acceso por la junta de usuarios.",
                "immediate_response" to "Diálogo en el lugar con la junta.",
                "consequences" to "Dos horas sin acceso."
            ) + attributes,
            token = creator?.token ?: token
        )

    protected fun mediaMention(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "media_mention",
            mapOf(
                "project" to project,
                "outlet_name" to "Radio Ejemplo",
                "published_date" to "2026-10-06",
                "headline" to "La junta de usuarios pide diálogo",
                "tone" to "NEGATIVO"
            ) + attributes,
            token = creator?.token ?: token
        )

    protected fun warningSignal(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "early_warning_signal",
            mapOf(
                "project" to project,
                "source_type" to "EQUIPO SOCIAL",
                "received_at" to "2026-10-06T15:00:00Z",
                "description" to "Se anuncia una movilización."
            ) + attributes,
            token = creator?.token ?: token
        )

    // una regla que WarningWriteGuard (plan 4.2b) acepta: umbral entero y ventana de 1 a 365 días; sin `project`, del tenant
    protected fun warningRule(
        project: String?,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "early_warning_rule",
            mapOf(
                "name" to "Señales de la semana",
                "rule_kind" to "CONTEO DE SEÑALES",
                "threshold" to 3,
                "window_days" to 7,
                "severity" to "MEDIO",
                "enabled" to true
            ) + listOfNotNull(project?.let { "project" to it }) + attributes,
            token = creator?.token ?: token
        )

    // una alerta de una regla como la crea JOB-04: la plataforma, con su regla y su hora (los campos de la plataforma)
    protected fun ruleWarning(
        project: String,
        rule: String,
        detectedAt: String = "2026-10-07T15:00:00Z"
    ): String =
        platformCreate(
            "early_warning",
            mapOf(
                "project" to project,
                "title" to "Alerta de la regla",
                "detected_at" to detectedAt,
                "severity" to "MEDIO",
                "ai_detected" to false,
                "early_warning_rule" to rule
            ),
            "alerta de prueba como JOB-04"
        )

    // una alerta registrada por una persona, en detected (WF-J); las de JOB-04 las crea la plataforma con generator_key. sin
    // detected_at ni early_warning_rule, que solo escribe la plataforma (403 PLATFORM_ONLY): la hora la pone SGSPE en el alta
    protected fun earlyWarning(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "early_warning",
            mapOf(
                "project" to project,
                "title" to "Movilización anunciada",
                "severity" to "ALTO",
                "trigger_description" to "Tres señales del equipo social en una semana.",
                "ai_detected" to false
            ) + attributes,
            token = creator?.token ?: token
        )

    companion object {
        // el nivel de un puntaje P × I con los umbrales por defecto de project (risk_*_min_score: 5, 10 y 15, ASM-004)
        fun riskLevel(score: Int): String =
            when {
                score >= 15 -> "CRITICO"
                score >= 10 -> "ALTO"
                score >= 5 -> "MEDIO"
                else -> "BAJO"
            }
    }
}
