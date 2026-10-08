package sgspe.common

import wasichai.core.data.RecordResponse

// los cargos vigentes de una persona y cómo se citan: «cargo, organización». es como un tablero nombra a una persona
// cuyo nombre quien mira no puede leer (dashboards.md §5.3, RQ-SEC-010) y como la exportación cita a una persona en
// EXTERNO; vive en common para que sgspe.dashboard y sgspe.privacy compartan la regla sin depender uno del otro
// (architecture.md §5.3). cada uno lee los registros a su manera (el tablero, con su tope de lectura)
object Positions {
    const val OBJECT = "position"
    const val STATUS = "position_status"
    const val CURRENT = "VIGENTE"

    data class Current(
        val person: String,
        val title: String?,
        val organization: String?
    )

    // el cargo de una fila de position si está VIGENTE y tiene persona; si no, null
    fun current(record: RecordResponse): Current? {
        val a = record.attributes
        if (Values.text(a[STATUS]) != CURRENT) return null
        val person = Values.text(a["person"]) ?: return null
        return Current(person, Values.text(a["position_title"]), Values.text(a["organization"]))
    }

    // «cargo, organización», con lo que haya de los dos; organizationNames son los nombres que quien lee puede ver
    fun label(
        position: Current,
        organizationNames: Map<String, String>
    ): String = listOfNotNull(position.title, position.organization?.let(organizationNames::get)?.ifBlank { null }).joinToString(", ")
}
