package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// un campo derivado ("derived": true en model/model.json) solo lo escribe el código de sgspe, con la marca SgspeWrite
// (domain-model §4.6, api.md §2). por la API genérica, /admin o un módulo: un 400 que nombra el campo si el alta trae un
// valor o si el cambio lo deja distinto de lo guardado. el PUT reemplaza el registro: reenviar un derivado tal como se
// leyó no es cambiarlo, y omitirlo tampoco (el portal de la Fase 2.3 no los manda): lo que el PUT deja vacío lo devuelve
// DerivedFieldsKeeper
@Component
@Order(GuardOrder.DERIVED)
class DerivedFieldsGuard(
    private val catalog: ModelCatalog,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink() || WriteMark.present()) return emptyList()
        val derived = catalog.derivedFields(change.objectName)
        if (derived.isEmpty()) return emptyList()
        val sent = change.attributes.orEmpty().keys
        val touched = view.changed(change).filter { it in derived && it in sent }.sorted()
        return touched.map { field ->
            Finding(
                code = "DERIVED_FIELD",
                message = "El campo $field es derivado: lo calcula SGSPE y no se escribe por esta vía",
                field = field,
                status = HttpStatus.BAD_REQUEST
            )
        }
    }
}
