package sgspe.deliverable

import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Permissions
import sgspe.common.StrictQuery
import wasichai.core.common.Actions

// una sección de un tipo como la lee el portal (deliverables.md §7.2): clave, título, orden, tipo, si es obligatoria, si
// admite sugerencias de IA (AI-04) y su guía de redacción
data class SectionView(
    val key: String,
    val title: String,
    val order: Int,
    val kind: String,
    val required: Boolean,
    val assisted: Boolean,
    val guide: String
)

// un tipo de entregable registrado: su valor de «Tipo de entregable», su versión y sus secciones por orden
data class DeliverableTypeView(
    val type: String,
    val version: Int,
    val sections: List<SectionView>
)

// GET /api/sgspe/deliverable-types (api.md §4.12): los tipos que el backend registra (DeliverableTypes), para que el portal no
// copie sus secciones. exige READ sobre deliverable a quien llama, como la persona (sin él, 403 como los demás endpoints)
@Service
class DeliverableTypeCatalog(
    private val types: DeliverableTypes,
    private val callers: Callers,
    private val permissions: Permissions
) {
    suspend fun list(): List<DeliverableTypeView> {
        permissions.require(callers.require(), "Leer los tipos de entregable", Actions.READ to Deliverables.DELIVERABLE)
        return compose(types.all)
    }

    companion object {
        // los tipos por nombre y sus secciones por orden: un orden estable aunque cambie el registro de los componentes
        fun compose(types: Collection<DeliverableType>): List<DeliverableTypeView> =
            types.sortedBy { it.name }.map { type ->
                DeliverableTypeView(
                    type.name,
                    type.version,
                    type.sections.sortedBy { it.order }.map {
                        SectionView(it.key, it.title, it.order, it.kind, it.required, it.assisted, it.guide)
                    }
                )
            }
    }
}

@RestController
class DeliverableTypeCatalogController(
    private val catalog: DeliverableTypeCatalog
) {
    @GetMapping("/api/sgspe/deliverable-types")
    suspend fun list(request: ServerHttpRequest): List<DeliverableTypeView> {
        StrictQuery.requireOnly(request.queryParams, emptySet())
        return catalog.list()
    }
}
