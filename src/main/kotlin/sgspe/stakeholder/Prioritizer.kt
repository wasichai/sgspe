package sgspe.stakeholder

// la prioridad informativa de un grupo de interés con la matriz de priorización del proyecto (RF-STK-011,
// prioritization_rule): grupo de influencia × postura × ámbito y, si la fila lo dice, tipo de actor. una fila con el tipo
// del grupo de interés gana a la que vale para cualquier tipo. null si falta un dato o ninguna fila coincide. con dos filas
// de la misma especificidad gana la primera de la lista; en los datos no ocurre, porque el único compuesto y
// StakeholderWriteGuard lo impiden (los NULL son distintos). information_priority es requerido en el modelo
object Prioritizer {
    data class Rule(
        val influenceGroup: String?,
        val stance: String?,
        val scope: String?,
        val stakeholderType: String?,
        val priority: String
    )

    fun priority(
        rules: List<Rule>,
        influenceGroup: String?,
        stance: String?,
        scope: String?,
        stakeholderType: String?
    ): String? {
        if (influenceGroup == null || stance == null || scope == null) return null
        val matching = rules.filter { it.influenceGroup == influenceGroup && it.stance == stance && it.scope == scope }
        return (
            matching.firstOrNull {
                stakeholderType != null && it.stakeholderType == stakeholderType
            } ?: matching.firstOrNull { it.stakeholderType == null }
        )?.priority
    }
}
