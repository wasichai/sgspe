package sgspe.territory

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.PreconditionFailedException
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.util.UUID

data class AreaApproval(
    val approved: RecordResponse,
    val superseded: RecordResponse?
)

// la aprobación de una versión del área de influencia (RN-TER-003, WF-O, api.md §4.2): en una transacción lleva la versión
// en `draft` a `approved` y la aprobada anterior del mismo proyecto y tipo a `superseded`, que se conserva. las dos son
// transiciones de comando: por la API genérica, CommandTransitionGuard las rechaza; aquí se aplican con la marca y como
// quien llama (nunca asPlatform dentro de una petición). doble llave: UPDATE y la acción declarada APPROVE sobre el área, y
// los roles de las dos transiciones (RF-WFL-002). exige If-Match, motivo y al menos un miembro.
// candados, siempre en este orden: primero el del proyecto y tipo, que deja en fila a las aprobaciones que compiten por la
// versión única, y después el de cada área que cambia (`area_of_influence:<id>`), el mismo que toma TerritoryWriteGuard para
// leer su estado antes de aceptar un miembro o un cambio de identidad de la versión. eso estrecha la ventana, no la cierra:
// la API genérica no abre una transacción por petición (architecture.md §14, riesgo 16, UPS-22), así que el candado de la
// guarda se suelta al leer el estado y un miembro puede entrar justo después de que la aprobación confirmó (o salir
// justo antes: una versión aprobada sin miembros). ninguna aprobación toma el del proyecto y tipo estando dentro de uno de
// área, así que dos aprobaciones no se interbloquean
@Service
class AreasOfInfluence(
    private val records: Records,
    private val versions: Versions,
    private val transactions: Transactions,
    private val permissions: Permissions,
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val areas: Areas
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun approve(
        id: UUID,
        reason: String?,
        ifMatch: String?
    ): AreaApproval {
        val expected = IfMatch.required(ifMatch)
        if (reason.isNullOrBlank()) {
            throw ValidationException("Aprobar un área de influencia exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        }
        val user = callers.require()
        permissions.require(user, "Aprobar un área de influencia", Actions.UPDATE to AREA)
        if (!permissions.has(user, APPROVE, AREA)) {
            throw Finding(
                "ACTION_REQUIRED",
                "Aprobar un área de influencia exige la acción APPROVE sobre $AREA",
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
        TransitionRoles.require(catalog, user, AREA, listOf("approve", "supersede"))
        // la clave del candado de la versión única sale del proyecto y el tipo; se vuelven a leer ya dentro del candado
        val read = records.get(AREA, id)
        val project = Values.text(read.attributes["project"])!!
        val type = Values.text(read.attributes["area_type"])!!
        // el área oficial con el working_crs que el proyecto tiene ahora, no el de cuando se dibujó, se mide ANTES de abrir la
        // transacción: un fallo de PostGIS dentro la dejaría abortada (25P02) y la respuesta sería un 500 sin código. si la
        // geometría no se puede medir la aprobación sigue y el área queda como estaba (P20)
        val measured = areas.measure(AREA, read)
        return transactions.locked("$AREA:$project:$type") {
            transactions.locked(areaLock(id)) {
                val area = records.get(AREA, id)
                versions.check(AREA, id, expected, area.updatedAt, "El área de influencia")
                if (Values.text(area.attributes["project"]) != project || Values.text(area.attributes["area_type"]) != type) {
                    throw PreconditionFailedException("El área de influencia cambió de proyecto o de tipo: vuelve a cargarla", area.updatedAt?.toString())
                }
                if (area.state != TerritoryWriteGuard.DRAFT) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Solo se aprueba una versión en borrador; esta está en ${area.state}",
                        "state"
                    ).toCodedException()
                }
                if (records.count(TerritoryWriteGuard.MEMBER, filters = mapOf(TerritoryWriteGuard.AREA_FIELD to id.toString())) == 0L) {
                    throw Finding(
                        "MEMBERS_REQUIRED",
                        "Un área de influencia sin miembros no se aprueba: agregue sus localidades, comunidades o unidades",
                        TerritoryWriteGuard.MEMBER
                    ).toCodedException()
                }
                val previous =
                    records
                        .all(AREA, filters = mapOf("project" to project, "area_type" to type), criteria = listOf(Criteria.inState(setOf("approved"))))
                        .filter { it.id != id.toString() }
                // RN-TER-003 y FROZEN_AREA lo impiden, pero un dato anterior a la guarda podría traer varias: se reemplazan todas
                if (previous.size > 1) log.warn("AreasOfInfluence: {} versiones aprobadas de {} {}; se reemplazan todas", previous.size, project, type)
                val superseded =
                    previous.map { old ->
                        transactions.locked(areaLock(UUID.fromString(old.id))) { records.transition(AREA, UUID.fromString(old.id), "supersede", reason) }
                    }
                records.transition(AREA, id, "approve", reason)
                if (measured != null) areas.write(AREA, id, measured)
                AreaApproval(records.get(AREA, id), superseded.firstOrNull())
            }
        }
    }

    companion object {
        const val AREA = "area_of_influence"
        const val APPROVE = "APPROVE"

        // la clave que serializa las escrituras sobre una versión: la aprobación y los miembros que se le agregan o quitan
        fun areaLock(id: UUID): String = "$AREA:$id"
    }
}

// api.md §4.2. Idempotency-Key se acepta y se ignora en F2 (§3.5)
@RestController
@RequestMapping("/api/sgspe/areas-of-influence")
class AreasOfInfluenceController(
    private val areas: AreasOfInfluence
) {
    @PostMapping("/{id}/approve")
    suspend fun approve(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: Map<String, Any?>?,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): AreaApproval {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        StrictBody.requireOnly(body.orEmpty(), emptySet())
        return areas.approve(id, ChangeReason.fromHeader(reason), ifMatch)
    }
}
