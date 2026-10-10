package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.AttentionTotals
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Raised
import sgspe.common.Values
import sgspe.common.toCodedException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// lo que las hojas de attention-register necesitan además de las QCS, ya leído como quien exporta. `readable` son los
// objeto.campo que puede leer (Readability); los mapas solo traen lo que puede leer, así que lo que falta sale «no disponible».
// `codes` da el código de los registros a los que apuntan reclassified_from_* y created_commitment, por id; un código vacío es
// un original que no se muestra (en EXTERNO, uno confidencial o cuya marca no se lee) y deja la celda vacía
class AttentionContext(
    val readable: Set<String>,
    val stakeholders: Map<String, String> = emptyMap(),
    val people: Map<String, String> = emptyMap(),
    val places: Map<String, String> = emptyMap(),
    val topics: Map<String, String> = emptyMap(),
    val codes: Map<String, String> = emptyMap()
) {
    fun canRead(path: String): Boolean = path in readable
}

// las hojas de attention-register según el nivel de divulgación (deliverables.md §10, ASM-101, RF-GRV-005): INTERNO trae las tres
// hojas con todas sus columnas y las confidenciales que quien exporta lee; EXTERNO, las mismas hojas sin confidenciales, sin texto
// libre, sin persona, sin quien recibió ni responsable y con el grupo de interés por código GI-, más una hoja «Notas»; PUBLICO
// solo cuenta planteamientos (AttentionCounting) sobre la grilla del catálogo con SmallCells (ASM-088). nada de esto lee datos:
// recibe lo que quien exporta pudo leer. en ningún nivel hay documento de identidad, teléfono ni correo de una persona
object AttentionSheets {
    // los estados en que corre un plazo y la fecha que mide cada uno (workflows.md «Plazos y escalamiento», ASM-100): los mismos
    // que JOB-01 (privacy solo depende de common; AttentionSheetsTest comprueba que coinciden con Deadlines.TRACKED)
    val DUE_FIELDS: Map<String, Map<String, String>> =
        mapOf(
            Attention.GRIEVANCE to
                mapOf(
                    "received" to "ack_due_date",
                    "acknowledged" to "resolution_due_date",
                    "in_assessment" to "resolution_due_date",
                    "in_investigation" to "resolution_due_date",
                    "in_action" to "resolution_due_date"
                ),
            Attention.INQUIRY to mapOf("received" to "response_due_date")
        )

    // el texto libre (LONG_TEXT) de las QCS: nunca en EXTERNO
    val FREE_TEXT: Set<String> = setOf("description", "response", "resolution", "requested_resource")

    // los campos de cada QCS que las hojas escriben: su lectura se comprueba antes (Readability)
    val COMMON_FIELDS: List<String> =
        listOf(
            "code",
            "channel",
            "received_at",
            "received_by_user_email",
            "anonymous",
            "confidential",
            "category",
            "priority",
            "deadline_status",
            "owner_user_email",
            "stakeholder",
            "person",
            "locality",
            "community",
            "topic",
            "description",
            "response",
            "responded_at",
            "closed_date"
        )
    val GRIEVANCE_FIELDS: List<String> =
        listOf("severity", "ack_due_date", "resolution_due_date", "resolved_date", "resolution", "complainant_conformity_given")
    val INQUIRY_FIELDS: List<String> = listOf("response_due_date")
    val REQUEST_FIELDS: List<String> = listOf("request_medium", "requested_resource", "created_commitment")

    // las hojas de cada tipo, en el orden de las fichas
    val SHEETS: Map<String, String> = mapOf(Attention.GRIEVANCE to "Quejas", Attention.INQUIRY to "Consultas", Attention.REQUEST to "Solicitudes")

    // el estado agrupado de PUBLICO: abiertas, cerradas, rechazadas, desistidas, declinadas
    val GROUPS: Map<String, String> = mapOf("closed" to "cerradas", "rejected" to "rechazadas", "withdrawn" to "desistidas", "declined" to "declinadas")
    const val OPEN = "abiertas"
    const val NO_DATA = "SIN DATO"
    const val ANONYMOUS = "anónima"

    private val LIMA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // los campos de un tipo: los comunes más los propios (la queja mide el acuse y la resolución; la consulta, la respuesta)
    fun fields(type: String): List<String> =
        COMMON_FIELDS +
            when (type) {
                Attention.GRIEVANCE -> GRIEVANCE_FIELDS
                Attention.INQUIRY -> INQUIRY_FIELDS
                else -> REQUEST_FIELDS
            } + Attention.RECLASSIFIED_FROM.getValue(type).keys

    // los objeto.campo que leen las hojas
    val REQUIRES: List<String> =
        Attention.OBJECTS.flatMap { type -> fields(type).map { "$type.$it" } } +
            listOf("organization.name", "person.given_names", "person.surnames", "locality.name", "community.name", "topic.name", "commitment.code")

    // la fecha que mide la QCS en su estado (null fuera de los estados con plazo)
    fun dueField(
        type: String,
        state: String?
    ): String? = DUE_FIELDS[type]?.get(state)

    // la hoja de un tipo, en INTERNO o EXTERNO
    fun records(
        type: String,
        lines: List<Raised>,
        context: AttentionContext,
        disclosure: Disclosure
    ): XlsxSheet {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva registros: solo agregados" }
        val internal = disclosure == Disclosure.INTERNO
        val header =
            listOf("Código", "Canal", "Recibida") + (if (internal) listOf("Recibida por") else emptyList()) +
                listOf("Anónima") + (if (internal) listOf("Confidencial") else emptyList()) +
                listOf("Categoría", "Prioridad", "Estado", "Situación de plazo", "Plazo que corre") +
                (if (internal) listOf("Responsable") else emptyList()) + listOf("Grupo de interés") +
                (if (internal) listOf("Persona") else emptyList()) + listOf("Localidad", "Comunidad", "Tema") +
                (if (internal) listOf("Descripción", "Respuesta") else emptyList()) + listOf("Respondida", "Cerrada", "Reclasificada de") +
                when (type) {
                    Attention.GRIEVANCE ->
                        listOf("Severidad", "Acuse", "Plazo de resolución", "Resuelta") +
                            (if (internal) listOf("Resolución") else emptyList()) +
                            "Conformidad"
                    Attention.REQUEST -> listOf("Medio") + (if (internal) listOf("Recurso pedido") else emptyList()) + "Compromiso creado"
                    else -> emptyList()
                }
        val rows =
            lines.asSequence().map { line ->
                val a = line.attributes

                fun readable(name: String) = context.canRead("$type.$name")

                fun text(name: String): Any = if (readable(name)) Values.text(a[name]).orEmpty() else NOT_AVAILABLE

                fun flag(name: String): Any = if (readable(name)) (if (Values.bool(a[name]) == true) "Sí" else "No") else NOT_AVAILABLE

                fun named(
                    name: String,
                    names: Map<String, String>,
                    path: String
                ): Any {
                    if (!readable(name)) return NOT_AVAILABLE
                    val id = Values.text(a[name]) ?: return ""
                    return if (context.canRead(path)) names[id] ?: NOT_AVAILABLE else NOT_AVAILABLE
                }
                val due = dueField(type, line.state)
                listOf(text("code"), text("channel"), if (readable("received_at")) limaTime(a["received_at"]) else NOT_AVAILABLE) +
                    (if (internal) listOf(text("received_by_user_email")) else emptyList()) +
                    listOf(flag("anonymous")) + (if (internal) listOf(flag("confidential")) else emptyList()) +
                    listOf(
                        text("category"),
                        text("priority"),
                        line.state.orEmpty(),
                        if (due == null && readable("deadline_status")) "" else text("deadline_status"),
                        if (due == null) "" else text(due)
                    ) +
                    (if (internal) listOf(text("owner_user_email")) else emptyList()) +
                    listOf(stakeholder(a, context, type, internal)) +
                    (if (internal) listOf(person(a, context, type)) else emptyList()) +
                    listOf(
                        named("locality", context.places, "locality.name"),
                        named("community", context.places, "community.name"),
                        named("topic", context.topics, "topic.name")
                    ) +
                    (if (internal) listOf(text("description"), text("response")) else emptyList()) +
                    listOf(
                        if (readable("responded_at")) limaTime(a["responded_at"]) else NOT_AVAILABLE,
                        text("closed_date"),
                        reclassifiedFrom(type, a, context)
                    ) +
                    when (type) {
                        Attention.GRIEVANCE ->
                            listOf(text("severity"), text("ack_due_date"), text("resolution_due_date"), text("resolved_date")) +
                                (if (internal) listOf(text("resolution")) else emptyList()) + flag("complainant_conformity_given")
                        Attention.REQUEST ->
                            listOf(text("request_medium")) + (if (internal) listOf(text("requested_resource")) else emptyList()) +
                                named("created_commitment", context.codes, "commitment.code")
                        else -> emptyList()
                    }
            }
        return XlsxSheet(SHEETS.getValue(type), header, rows)
    }

    // el grupo de interés: su nombre en INTERNO si se lee, si no (y siempre en EXTERNO) su código GI-
    private fun stakeholder(
        a: Map<String, Any?>,
        context: AttentionContext,
        type: String,
        internal: Boolean
    ): Any {
        if (!context.canRead("$type.stakeholder")) return NOT_AVAILABLE
        val id = Values.text(a["stakeholder"]) ?: return ""
        return if (internal) context.stakeholders[id] ?: RegisterSheets.code(id) else RegisterSheets.code(id)
    }

    // la persona que plantea: «anónima», sus nombres y apellidos si quien exporta los lee, o «no disponible»
    private fun person(
        a: Map<String, Any?>,
        context: AttentionContext,
        type: String
    ): Any {
        if (Values.bool(a["anonymous"]) == true) return ANONYMOUS
        if (!context.canRead("$type.person")) return NOT_AVAILABLE
        val id = Values.text(a["person"]) ?: return ""
        return context.people[id] ?: NOT_AVAILABLE
    }

    // el código del registro original de una reclasificación
    private fun reclassifiedFrom(
        type: String,
        a: Map<String, Any?>,
        context: AttentionContext
    ): Any {
        val links = Attention.RECLASSIFIED_FROM.getValue(type)
        if (links.keys.any { !context.canRead("$type.$it") }) return NOT_AVAILABLE
        val id = links.keys.firstNotNullOfOrNull { Values.text(a[it]) } ?: return ""
        return context.codes[id] ?: NOT_AVAILABLE
    }

    // «Resumen» de INTERNO: los conteos del reporte unificado, por tipo y en total. «Absueltas» se cuenta con responded_at:
    // si quien exporta no lo lee en un tipo, la celda sale «no disponible», y el total también, nunca un 0 (RQ-DSH-032)
    fun summary(
        byType: Map<String, AttentionTotals>,
        readable: Set<String>
    ): XlsxSheet {
        val total =
            AttentionTotals(
                byType.values.sumOf { it.received },
                byType.values.sumOf { it.open },
                byType.values.sumOf { it.closed },
                byType.values.sumOf { it.rejected },
                byType.values.sumOf { it.withdrawn },
                byType.values.sumOf { it.absolved },
                byType.values.sumOf { it.overdue }
            )
        val rows =
            byType.map { (type, t) -> listOf<Any?>(SHEETS.getValue(type)) + counts(t, absolvedReadable(type, readable)) } +
                listOf(listOf<Any?>("Total") + counts(total, byType.keys.all { absolvedReadable(it, readable) }))
        return XlsxSheet("Resumen", listOf("Tipo", "Recibidas", "Abiertas", "Cerradas", "Rechazadas", "Desistidas", "Absueltas", "Vencidas"), rows.asSequence())
    }

    private fun counts(
        t: AttentionTotals,
        absolvedReadable: Boolean
    ): List<Any?> = listOf(t.received, t.open, t.closed, t.rejected, t.withdrawn, if (absolvedReadable) t.absolved else NOT_AVAILABLE, t.overdue)

    private fun absolvedReadable(
        type: String,
        readable: Set<String>
    ): Boolean = "$type.${Attention.RESPONDED_AT}" in readable

    // «Notas»: en EXTERNO, que no salen las confidenciales ni el texto libre (sin conteos); en PUBLICO, si el conteo las incluye
    fun notes(
        disclosure: Disclosure,
        confidentialIncluded: Boolean
    ): XlsxSheet {
        val lines =
            when (disclosure) {
                Disclosure.EXTERNO ->
                    listOf(
                        "Las QCS confidenciales no se incluyen en esta exportación.",
                        "El texto libre (descripción, respuesta, resolución y recurso pedido), la persona, quien recibió y el responsable no se incluyen."
                    )
                else ->
                    listOf(
                        "El conteo incluye las QCS confidenciales: ${if (confidentialIncluded) "sí" else "no"}.",
                        "Cada planteamiento cuenta una vez, como el último registro de su reclasificación. Las celdas con menos de " +
                            "${SmallCells.MIN} planteamientos salen como «${SmallCells.HIDDEN}»."
                    )
            }
        return XlsxSheet("Notas", listOf("Nota"), lines.map { listOf<Any?>(it) }.asSequence())
    }

    // PUBLICO: planteamientos por tipo y estado agrupado, por tipo y canal, por tipo y categoría, y recibidos y absueltos por
    // tipo, sobre la grilla del catálogo con sus ceros y SmallCells en cada tabla; sin totales, territorio ni grupo de interés.
    // `states` son los estados de cada workflow, `channels` y `categories` los catálogos qcs_channel y qcs_category
    fun aggregates(
        lines: List<Raised>,
        readable: Set<String>,
        states: Map<String, List<String>>,
        channels: List<String>,
        categories: List<String>,
        confidentialIncluded: Boolean
    ): List<XlsxSheet> {
        val counted = AttentionCounting.counted(lines)
        val types = Attention.OBJECTS
        if (channels.isEmpty() || categories.isEmpty() || types.any { states[it].isNullOrEmpty() }) {
            throw Finding(
                "CATALOGUE_UNAVAILABLE",
                "Un catálogo de las QCS no está disponible: una salida PUBLICO no puede armar su grilla",
                "type",
                status = HttpStatus.CONFLICT
            ).toCodedException()
        }
        val groupAxis = types.associateWith { type -> states.getValue(type).map { group(it) }.distinct() + NOT_AVAILABLE }
        val byState = counted.groupingBy { it.type to (if (it.state in states.getValue(it.type)) group(it.state) else NOT_AVAILABLE) }.eachCount()
        val stateTable = SmallCells.apply(types.flatMap { t -> groupAxis.getValue(t).map { g -> (t to g) to (byState[t to g] ?: 0) } })

        fun byField(
            field: String,
            known: List<String>
        ): List<Pair<Pair<String, String>, Any>> {
            val axis = known + NO_DATA + NOT_AVAILABLE
            val counts =
                counted
                    .groupingBy { line ->
                        line.type to
                            when {
                                "${line.type}.$field" !in readable -> NOT_AVAILABLE
                                Values.text(line.attributes[field]) == null -> NO_DATA
                                Values.text(line.attributes[field]) in known -> Values.text(line.attributes[field])!!
                                else -> NOT_AVAILABLE
                            }
                    }.eachCount()
            return SmallCells.apply(types.flatMap { t -> axis.map { v -> (t to v) to (counts[t to v] ?: 0) } })
        }
        // las absueltas de un tipo cuyo responded_at quien exporta no lee salen «no disponible», fuera de la supresión, que se
        // completa sobre las demás celdas de la tabla
        val absolvedCells =
            types.flatMap { t ->
                val ofType = counted.filter { it.type == t }
                listOf((t to "Recibidas") to ofType.size, (t to "Absueltas") to ofType.count { Values.text(it.attributes[Attention.RESPONDED_AT]) != null })
            }
        val unknown = absolvedCells.map { it.first }.filter { (t, column) -> column == "Absueltas" && !absolvedReadable(t, readable) }.toSet()
        val published = SmallCells.apply(absolvedCells.filter { it.first !in unknown }).toMap()
        val absolved = absolvedCells.map { (key, _) -> key to (published[key] ?: NOT_AVAILABLE) }

        fun sheet(
            name: String,
            column: String,
            cells: List<Pair<Pair<String, String>, Any>>
        ) = XlsxSheet(
            name,
            listOf("Tipo", column, "Planteamientos"),
            cells.asSequence().map { (key, count) ->
                listOf(SHEETS.getValue(key.first), key.second, count)
            }
        )
        return listOf(
            sheet("Por tipo y estado", "Estado", stateTable),
            sheet("Por tipo y canal", "Canal", byField("channel", channels)),
            sheet("Por tipo y categoría", "Categoría", byField("category", categories)),
            sheet("Absueltas por tipo", "Conteo", absolved),
            notes(Disclosure.PUBLICO, confidentialIncluded)
        )
    }

    fun group(state: String?): String = GROUPS[state] ?: OPEN

    // un instante como fecha y hora de Lima; vacío sin valor
    fun limaTime(value: Any?): String {
        val text = Values.text(value) ?: return ""
        val instant = runCatching { Instant.parse(text) }.getOrElse { runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull() } ?: return text
        return instant.atZone(LIMA).format(LIMA_TIME)
    }
}
