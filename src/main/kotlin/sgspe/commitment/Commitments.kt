package sgspe.commitment

// los nombres y los conjuntos de estados de WF-D que comparten las guardas, los comandos, JOB-01, los avisos y los tableros
// (workflows.md WF-D, dashboards.md §3.2): una sola fuente
object Commitments {
    const val COMMITMENT = "commitment"
    const val RESCHEDULE = "commitment_reschedule"
    const val DEPENDENCY = "commitment_dependency"
    const val DELIVERY = "delivery"
    const val CANDIDATE = "commitment_candidate"
    const val STAKEHOLDERS = "commitment_stakeholder"
    const val EVIDENCE_DELIVERY = "evidence_delivery"

    // donde JOB-01 calcula deadline_status: pendiente de cumplimiento
    val PENDING = setOf("approved", "in_progress", "blocked")

    // donde se lee fulfilled_date: cumplido
    val FULFILLED = setOf("fulfilled", "verified", "closed")

    // los montos aprobados de ASM-063: comprometido
    val COMMITTED = PENDING + FULFILLED

    // abierto: todos salvo closed y cancelled
    val CLOSED = setOf("closed", "cancelled")

    // desde approved la fecha de vencimiento solo cambia con una reprogramación (RF-COM-003)
    val AFTER_APPROVAL = COMMITTED + "cancelled"

    // donde se pueden registrar entregas y reprogramaciones: la ejecución
    val EXECUTING = PENDING

    // las transiciones que llegan a approved (D1, D2, D5, D7)
    val APPROVALS = setOf("approve", "approve_senior", "approve_direct")
}
