package sgspe.deliverable

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Records
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener

// las secciones del tipo al dar de alta un entregable (especificación de F8 §2.2.13, api.md §4.12): en la transacción del
// alta y como quien lo da de alta (Records lleva la marca y su permiso de CREATE sobre deliverable_section), una por
// SectionSpec, con su clave, título, orden y tipo; las CALCULADA nacen con origen CALCULADO y las demás HUMANO. un tipo que
// todavía no se genera (T1 hasta el plan 8.2c) no crea ninguna. el texto lo escribe una persona después
@Component
@Order(150)
class DeliverableSectionsListener(
    private val records: Records,
    private val types: DeliverableTypes
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Deliverables.DELIVERABLE || change.kind != RecordChangeKind.CREATED) return
        val type = types.of(change.after?.get(Deliverables.TYPE)?.toString()) ?: return
        for (spec in type.sections) {
            records.create(
                Deliverables.SECTION,
                mapOf(
                    Deliverables.DELIVERABLE to change.recordId.toString(),
                    Deliverables.SECTION_KEY to spec.key,
                    "title" to spec.title,
                    "sort_order" to spec.order,
                    "section_kind" to spec.kind,
                    Deliverables.CONTENT_ORIGIN to if (spec.kind == CALCULATED) "CALCULADO" else Deliverables.HUMAN
                ),
                REASON
            )
        }
    }

    companion object {
        const val REASON = "secciones del tipo de entregable"
        private const val CALCULATED = "CALCULADA"
    }
}
