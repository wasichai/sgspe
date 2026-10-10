package sgspe.common

// los nombres de las quejas, consultas y solicitudes (QCS, BC-07) que comparten sgspe.attention, sgspe.scope (el alcance de
// las confidenciales), sgspe.deadline, sgspe.notification, los tableros y las exportaciones: viven en common porque scope y
// privacy solo dependen de common (architecture.md §5.3)
object Attention {
    const val GRIEVANCE = "grievance"
    const val INQUIRY = "inquiry"
    const val REQUEST = "stakeholder_request"

    // en el orden de las fichas (database-model.md §12)
    val OBJECTS: List<String> = listOf(GRIEVANCE, INQUIRY, REQUEST)

    // la acción declarada que lee toda QCS confidencial del objeto (ASM-095, security.md §9.6)
    const val READ_CONFIDENTIAL = "READ_CONFIDENTIAL"

    // la acción declarada de quien resuelve una queja grave (RN-GRV-002): ve la queja confidencial ALTA o CRITICA
    const val APPROVE = "APPROVE"

    const val CONFIDENTIAL = "confidential"
    const val OWNER = "owner_user_id"
    const val RECEIVER = "received_by_user_id"
    const val SEVERITY = "severity"
    const val RECEIVED_AT = "received_at"
    const val RESPONDED_AT = "responded_at"
    const val DEADLINE_STATUS = "deadline_status"

    // las severidades de una queja grave (RN-GRV-002)
    val HIGH_SEVERITY: Set<String> = setOf("ALTA", "CRITICA")

    // los vínculos de reclasificación de cada QCS: campo -> objeto del registro original (RF-GRV-007, ASM-072)
    val RECLASSIFIED_FROM: Map<String, Map<String, String>> =
        mapOf(
            GRIEVANCE to mapOf("reclassified_from_inquiry" to INQUIRY, "reclassified_from_request" to REQUEST),
            INQUIRY to mapOf("reclassified_from_grievance" to GRIEVANCE, "reclassified_from_request" to REQUEST),
            REQUEST to mapOf("reclassified_from_grievance" to GRIEVANCE, "reclassified_from_inquiry" to INQUIRY)
        )

    // los estados finales de cada workflow (dashboards.md §3.2): abierta es todo lo demás
    val FINAL_STATES: Map<String, Set<String>> =
        mapOf(
            GRIEVANCE to setOf("closed", "rejected", "withdrawn"),
            INQUIRY to setOf("closed"),
            REQUEST to setOf("closed")
        )

    // el campo de reclasificación con que el registro nuevo apunta a un original de `source` (reclassified_from_<tipo>)
    fun reclassifiedField(
        target: String,
        source: String
    ): String =
        RECLASSIFIED_FROM
            .getValue(target)
            .entries
            .first { it.value == source }
            .key

    fun isConfidential(attributes: Map<String, Any?>): Boolean = Values.bool(attributes[CONFIDENTIAL]) == true

    fun isOpen(
        objectName: String,
        state: String?
    ): Boolean = state != null && state !in FINAL_STATES.getValue(objectName)
}

// un planteamiento como lo cuenta AttentionCounting: su tipo (el objeto), su id, su estado y sus atributos ya leídos
data class Raised(
    val type: String,
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>
)

// los conteos de un conjunto de planteamientos (api.md §4.6, reporte unificado): recibidos, abiertos, cerrados (en
// `closed`), rechazados, desistidos, absueltos (con responded_at, RQ-DSH-023) y vencidos (abiertos con deadline_status VENCIDO)
data class AttentionTotals(
    val received: Int,
    val open: Int,
    val closed: Int,
    val rejected: Int,
    val withdrawn: Int,
    val absolved: Int,
    val overdue: Int
)

// la regla de conteo de dashboards.md §3.3, pura: cada planteamiento cuenta una vez, como el último registro de su cadena de
// reclasificaciones; los originales a los que apunta un reclassified_from_* no cuentan. la usan el reporte unificado, los
// KPI-GRV, la exportación PUBLICO y la regla CONTEO DE QCS de JOB-04 (plan 4.2b). solo ve lo que recibe: lo que quien llama
// no lee no está en la lista y no cuenta
object AttentionCounting {
    // los originales reclasificados que la lista apunta: (tipo, id)
    fun originals(rows: Collection<Raised>): Set<Pair<String, String>> =
        rows
            .flatMap { row ->
                Attention.RECLASSIFIED_FROM[row.type].orEmpty().mapNotNull { (field, source) ->
                    Values.text(row.attributes[field])?.let { source to it }
                }
            }.toSet()

    // los planteamientos: la lista sin los originales que otro registro de la lista reclasificó
    fun counted(rows: Collection<Raised>): List<Raised> {
        val originals = originals(rows)
        return rows.filterNot { (it.type to it.id) in originals }
    }

    fun totals(rows: Collection<Raised>): AttentionTotals {
        val counted = counted(rows)
        return AttentionTotals(
            received = counted.size,
            open = counted.count { Attention.isOpen(it.type, it.state) },
            closed = counted.count { it.state == "closed" },
            rejected = counted.count { it.state == "rejected" },
            withdrawn = counted.count { it.state == "withdrawn" },
            absolved = counted.count { Values.text(it.attributes[Attention.RESPONDED_AT]) != null },
            overdue = counted.count { Attention.isOpen(it.type, it.state) && Values.text(it.attributes[Attention.DEADLINE_STATUS]) == OVERDUE }
        )
    }

    const val OVERDUE = "VENCIDO"
}
