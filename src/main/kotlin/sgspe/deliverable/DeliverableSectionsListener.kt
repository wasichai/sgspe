package sgspe.deliverable

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Records
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.util.UUID

// las secciones del tipo al dar de alta un entregable (especificación de F8 §2.2.13, api.md §4.12): en la transacción del
// alta y como quien lo da de alta (Records lleva la marca y su permiso de CREATE sobre deliverable_section), una por
// SectionSpec, con su clave, título, orden y tipo; las CALCULADA nacen con origen CALCULADO y las demás HUMANO. un tipo que
// todavía no se genera (T1 hasta el plan 8.2c) no crea ninguna. el texto lo escribe una persona después.
// completado (#105 a #111, deliverables.md §5): un entregable creado antes de que su tipo ganara secciones recibe las que le
// faltan cuando una persona lo actualiza o lo transiciona en draft u observed, también al pedir un borrador (DeliverableDrafts
// parcha generation_requested_at). solo en esos estados: después del borrador LISTO una sección nueva daría SECTIONS_CHANGED
// en las transiciones de revisión. y nunca como la plataforma (JOB-05 parcha el estado del borrador al terminar: una sección
// creada entonces quedaría después de draft_generated_at). dos escrituras a la vez sobre el mismo entregable se ordenan por
// el candado de su fila (y el de DeliverableDrafts), así que la segunda ya ve las creadas; la clave única (deliverable,
// section_key) de model.json impide el duplicado de todos modos
@Component
@Order(150)
class DeliverableSectionsListener(
    private val records: Records,
    private val types: DeliverableTypes,
    private val callers: Callers
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Deliverables.DELIVERABLE) return
        when (change.kind) {
            RecordChangeKind.CREATED -> create(change.recordId, change.after?.get(Deliverables.TYPE), emptySet())
            RecordChangeKind.UPDATED, RecordChangeKind.TRANSITIONED -> {
                if (change.state !in Deliverables.REQUESTABLE || callers.userOrNull() == null) return
                val type = (change.after ?: change.before)?.get(Deliverables.TYPE)
                if (types.of(type?.toString()) == null) return
                val present =
                    records
                        .all(Deliverables.SECTION, mapOf(Deliverables.DELIVERABLE to change.recordId.toString()))
                        .mapNotNull { it.attributes[Deliverables.SECTION_KEY]?.toString() }
                        .toSet()
                create(change.recordId, type, present)
            }
            else -> return
        }
    }

    private suspend fun create(
        deliverable: UUID,
        typeName: Any?,
        present: Set<String>
    ) {
        val type = types.of(typeName?.toString()) ?: return
        for (spec in missing(type, present)) {
            records.create(
                Deliverables.SECTION,
                mapOf(
                    Deliverables.DELIVERABLE to deliverable.toString(),
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

        // las secciones del tipo que el entregable todavía no tiene, en el orden del tipo
        fun missing(
            type: DeliverableType,
            present: Set<String>
        ): List<SectionSpec> = type.sections.filter { it.key !in present }
    }
}
