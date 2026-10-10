package sgspe.warning

// los nombres de la alerta temprana (BC-09, database-model.md §14) que comparten WarningWriteGuard, JOB-04 y sus pruebas
object Warnings {
    const val WARNING = "early_warning"
    const val RULE = "early_warning_rule"
    const val SIGNAL = "early_warning_signal"
    const val INCIDENT = "incident"
    const val MENTION = "media_mention"

    // la acción declarada de la doble llave de confirm y dismiss (RQ-SEC-004)
    const val CONFIRM_ALERT = "CONFIRM_ALERT"
    val DECISIONS: Set<String> = setOf("confirm", "dismiss")
    const val INCIDENT_CLOSE = "close"

    // las alertas que ya no están abiertas (WF-J): fuera de ellas, una alerta silencia su regla (ASM-097)
    val FINISHED: Set<String> = setOf("dismissed", "closed")

    // los seis tipos de regla del MVP (catálogo «Tipo de regla de alerta», ASM-097), sin CAMBIO DE POSTURA
    const val SIGNALS = "CONTEO DE SEÑALES"
    const val QCS = "CONTEO DE QCS"
    const val OVERDUE_COMMITMENTS = "COMPROMISOS VENCIDOS"
    const val NEGATIVE_MENTIONS = "MENCIONES NEGATIVAS"
    const val INCIDENTS = "INCIDENTES"
    const val RISK_LEVEL = "NIVEL DE RIESGO"
    val KINDS: List<String> = listOf(SIGNALS, QCS, OVERDUE_COMMITMENTS, NEGATIVE_MENTIONS, INCIDENTS, RISK_LEVEL)

    // window_days vacío vale 7; el umbral de NIVEL DE RIESGO es un puntaje P × I, de 1 a 25
    const val DEFAULT_WINDOW = 7L
    const val MAX_WINDOW = 365L
    const val MAX_SCORE = 25L

    // la marca de una cuenta de servicio en su correo de respaldo (WAS-ADR-0043)
    const val SERVICE_ACCOUNT_DOMAIN = "@service-accounts.invalid"
}
