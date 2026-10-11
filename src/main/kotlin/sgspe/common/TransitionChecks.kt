package sgspe.common

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import wasichai.core.common.NotFoundException
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// un hallazgo de la consulta en seco, con la forma que fija el portal (Fase 2.3): code, message y field, que es null si el
// hallazgo no apunta a un campo
data class CheckItem(
    val code: String,
    val message: String,
    val field: String?
)

data class TransitionCheck(
    val allowed: Boolean,
    val blocking: List<CheckItem>,
    val warnings: List<CheckItem>
)

// POST /api/sgspe/transition-checks (api.md §4.1): corre en seco las reglas de sgspe sobre una transición y devuelve lo
// que la bloquearía y las advertencias, sin escribir nada. la lista de transiciones de wasichai ya explica los roles y el
// permiso UPDATE; esto agrega lo que deciden las guardas (RN-ENG-005, las precondiciones de WF-A…). exige READ sobre el
// objeto: el registro se lee como quien llama
@RestController
@RequestMapping("/api/sgspe/transition-checks")
class TransitionChecksController(
    private val rules: List<WriteRule>,
    private val records: Records,
    private val definitions: ObjectDefinitions,
    private val catalog: ModelCatalog,
    private val callers: Callers
) {
    @PostMapping
    suspend fun check(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): TransitionCheck {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        StrictBody.requireOnly(body, setOf("object", "recordId", "transition"))
        val objectName = Values.text(body["object"]) ?: throw ValidationException("Falta el objeto", "object", "es obligatorio")
        val recordId = Records.uuid(body["recordId"]) ?: throw ValidationException("Falta el registro", "recordId", "debe ser un UUID")
        val name = Values.text(body["transition"]) ?: throw ValidationException("Falta la transición", "transition", "es obligatoria")
        val user = callers.require()
        val record = records.get(objectName, recordId)
        val transition =
            catalog.transition(objectName, name)
                ?: throw NotFoundException("El workflow de $objectName no tiene la transición $name")
        val change =
            RecordWrite(
                organizationId = user.organizationId,
                userId = user.userId,
                objectId = definitions.current(user.organizationId, objectName).obj.id,
                objectName = objectName,
                recordId = UUID.fromString(record.id),
                kind = RecordChangeKind.TRANSITIONED,
                before = record.attributes,
                transition = name
            )
        val findings = mutableListOf<Finding>()
        if (record.state != transition.from) {
            findings += Finding("STATE_MISMATCH", "La transición $name sale de ${transition.from}, pero el registro está en ${record.state ?: "ningún estado"}")
        }
        rules.forEach { findings += it.findings(change, dryRun = true) }
        val blocking = findings.filter { it.blocking }.map { CheckItem(it.code, it.message, it.field) }
        val warnings = findings.filterNot { it.blocking }.map { CheckItem(it.code, it.message, it.field) }
        return TransitionCheck(blocking.isEmpty(), blocking, warnings)
    }
}
