package sgspe.attention

import org.springframework.http.HttpStatus
import sgspe.common.Attention
import sgspe.common.Finding
import sgspe.common.Values
import java.util.UUID

// las reglas de datos de las QCS (workflows.md WF-E1, WF-E2 y WF-E3; especificación de F4 §3.2), puras: lo que se decide con el
// registro antes y después de la escritura, sin lecturas ni permisos. AttentionWriteGuard las corre y suma las que leen o
// miran permisos (confidencialidad, queja grave, doble llave y cierre sin ejecución)
object AttentionRules {
    // atender una queja exige su categoría y su severidad
    val ATTEND = setOf("investigate", "start_action", "start_action_after_investigation")

    // resolverla exige la resolución y la respuesta
    val RESOLVE = setOf("resolve", "resolve_high_severity")

    // las transiciones que comunican una respuesta: exigen responded_at (cuenta como absuelta, RQ-DSH-023) y/o response
    val RESPONDED_AT: Map<String, Set<String>> =
        mapOf(
            Attention.GRIEVANCE to setOf("close_resolved", "close", "reject_received", "reject_acknowledged", "reject_in_assessment"),
            Attention.INQUIRY to setOf("answer", "close_referred"),
            Attention.REQUEST to setOf("accept", "close_declined")
        )
    val RESPONSE: Map<String, Set<String>> =
        mapOf(
            Attention.GRIEVANCE to setOf("reject_received", "reject_acknowledged", "reject_in_assessment"),
            Attention.INQUIRY to setOf("answer", "refer"),
            Attention.REQUEST to setOf("decline", "close_unfulfilled", "accept")
        )

    const val REOPEN = "reopen"
    const val RESOLVE_PLAIN = "resolve"
    const val RESOLVE_HIGH = "resolve_high_severity"
    const val CLOSE_UNFULFILLED = "close_unfulfilled"
    const val CONFORMITY = "complainant_conformity_given"
    const val DUPLICATE_OF = "duplicate_of"
    const val CREATED_COMMITMENT = "created_commitment"

    // los vínculos que solo escriben los comandos: reclassified_from_* (Reclassifications) y created_commitment de la solicitud
    // (Acceptances). la regla de conteo (AttentionCounting) saca de los conteos al original que apunta un reclassified_from_*,
    // y el cierre del compromiso (RequestClosureListener) y close_unfulfilled confían en created_commitment: fijarlos a mano
    // ocultaría un planteamiento abierto o reapuntaría la solicitud a otro compromiso
    val COMMAND_FIELDS: Map<String, Set<String>> =
        Attention.OBJECTS.associateWith { objectName ->
            Attention.RECLASSIFIED_FROM.getValue(objectName).keys + if (objectName == Attention.REQUEST) setOf(CREATED_COMMITMENT) else emptySet()
        }

    // una persona (`person`) sin la marca SgspeWrite (`marked`) que fija, cambia o vacía uno de esos vínculos (`changed`, los
    // campos cuyo valor cambia: reenviar el valor tal como se leyó no es cambiarlo): 403 COMMAND_ONLY sobre el campo. la
    // plataforma (sin usuario) y los comandos sí los escriben
    fun commandOnly(
        objectName: String,
        changed: Set<String>,
        person: Boolean,
        marked: Boolean
    ): Finding? {
        if (!person || marked) return null
        val field = COMMAND_FIELDS[objectName].orEmpty().firstOrNull { it in changed } ?: return null
        return Finding(
            "COMMAND_ONLY",
            "$field lo escribe solo su comando (la reclasificación o la aceptación): no se fija ni se cambia por esta vía",
            field,
            status = HttpStatus.FORBIDDEN
        )
    }

    // los campos de identidad que una QCS anónima no lleva (RF-GRV-001, GAP-005)
    val IDENTITY = listOf("person", "stakeholder")

    // el alta o el cambio: anónima sin identidad y una queja que no es duplicado de sí misma
    fun record(
        objectName: String,
        recordId: UUID?,
        after: Map<String, Any?>
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (Values.bool(after["anonymous"]) == true) {
            IDENTITY.firstOrNull { Values.text(after[it]) != null }?.let { field ->
                findings +=
                    Finding(
                        "ANONYMOUS_WITH_IDENTITY",
                        "Una QCS anónima no lleva $field: quite la identidad o desmarque anonymous",
                        field,
                        status = HttpStatus.BAD_REQUEST
                    )
            }
        }
        if (objectName == Attention.GRIEVANCE && recordId != null && Values.text(after[DUPLICATE_OF]) == recordId.toString()) {
            findings += Finding("INVALID_LINK", "Una queja no es duplicado de sí misma", DUPLICATE_OF, status = HttpStatus.BAD_REQUEST)
        }
        return findings
    }

    // una transición con el registro como está guardado: los datos que cada transición exige
    fun transition(
        objectName: String,
        transition: String,
        record: Map<String, Any?>
    ): List<Finding> {
        val required = mutableListOf<String>()
        if (objectName == Attention.GRIEVANCE && transition in ATTEND) required += listOf("category", Attention.SEVERITY)
        if (objectName == Attention.GRIEVANCE && transition in RESOLVE) required += listOf("resolution", "response")
        if (transition in RESPONSE[objectName].orEmpty()) required += "response"
        if (transition in RESPONDED_AT[objectName].orEmpty()) required += Attention.RESPONDED_AT
        val findings =
            required.distinct().filter { Values.text(record[it]) == null }.mapTo(mutableListOf()) { field ->
                Finding("FIELD_REQUIRED", "La transición $transition exige $field", field, status = HttpStatus.BAD_REQUEST)
            }
        if (objectName == Attention.GRIEVANCE && transition == REOPEN && Values.bool(record[CONFORMITY]) == true) {
            findings += Finding("CONFORMITY_GIVEN", "No se reabre una queja con la conformidad de quien la planteó", CONFORMITY)
        }
        return findings
    }

    // una queja grave: ALTA o CRITICA (RN-GRV-002)
    fun grave(record: Map<String, Any?>): Boolean = Values.text(record[Attention.SEVERITY]) in Attention.HIGH_SEVERITY

    // la confidencialidad que una escritura cambia sin READ_CONFIDENTIAL: quitar la marca, o cambiar el responsable o quien la
    // recibió de una QCS confidencial (ASM-095). `before` vacío: un alta, que juzga confidentialReceiver
    fun protectedChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<String> {
        if (!Attention.isConfidential(before)) return emptyList()
        val fields = mutableListOf<String>()
        if (!Attention.isConfidential(after)) fields += Attention.CONFIDENTIAL
        for (field in listOf(Attention.OWNER, Attention.RECEIVER)) if (!Values.same(before[field], after[field])) fields += field
        return fields
    }

    // el alta de una confidencial cuyo receptor no es quien escribe (ASM-095): exige READ_CONFIDENTIAL
    fun foreignReceiver(
        after: Map<String, Any?>,
        writer: UUID
    ): Boolean = Attention.isConfidential(after) && Values.text(after[Attention.RECEIVER]) != writer.toString()

    // el alta que hace la reclasificación (Reclassifications): el código de sgspe (`marked`, la marca SgspeWrite) con el
    // vínculo reclassified_from_* del objeto y el receptor informados. su receptor es el del original, copiado (el comando no
    // deja cambiarlo), no uno que elige quien llama: foreignReceiver no la juzga. sin receptor no hay exención, y por la API
    // genérica, sin la marca, un reclassified_from_* no exime
    fun receiverCopied(
        objectName: String,
        after: Map<String, Any?>,
        marked: Boolean
    ): Boolean =
        marked &&
            Values.text(after[Attention.RECEIVER]) != null &&
            Attention.RECLASSIFIED_FROM[objectName]
                .orEmpty()
                .keys
                .any { Values.text(after[it]) != null }
}
