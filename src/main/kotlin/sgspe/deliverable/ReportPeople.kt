package sgspe.deliverable

import sgspe.privacy.MentionTarget
import sgspe.privacy.RegisterSheets
import java.util.UUID

// las personas que cita un informe (especificación de F8 §2.2.9, ASM-137): las menciones con lo que cada nivel necesita
// (nombre, cargo y organización, código GI-, localidad) y los nombres de las personas de los grupos de interés del proyecto
// para el aviso de datos personales. lee solo lo que el tipo declara (ReportSource). la usan T1 (DiagnosisScene) y T2
// (FieldReportType.Scene)
class ReportPeople(
    private val source: ReportSource,
    project: UUID
) {
    val people = linkedMapOf<String, MentionTarget>()
    val names = linkedSetOf<String>()

    // la persona detrás de cada mención (person, y la titular de un stakeholder o un position), para AI-04 de sección
    val holders = linkedMapOf<String, UUID>()
    private val byProject = mapOf(Deliverables.PROJECT to project.toString())
    private val positions = mutableMapOf<UUID, SourceRow?>()

    suspend fun organizationName(id: UUID?): String? = id?.let { source.one("organization", it)?.text("name") }

    // el cargo VIGENTE de una persona, o null, leído una vez por ensamblado (#83): lo usan la mención y varias secciones
    suspend fun position(person: UUID): SourceRow? =
        if (person in positions) {
            positions[person]
        } else {
            source.all("position", mapOf("person" to person.toString())).firstOrNull { it.text("position_status") == VIGENTE }.also { positions[person] = it }
        }

    // la mención de una persona: su token, con sus datos de cita registrados una vez
    suspend fun person(id: UUID): String {
        val key = "person:$id"
        holders[key] = id
        if (key !in people) {
            val row = source.one("person", id)
            val name = listOfNotNull(row?.text("given_names"), row?.text("surnames")).joinToString(" ").ifEmpty { "Sin nombre" }
            names += name
            val position = position(id)
            val stakeholder = source.all("stakeholder", byProject + ("person" to id.toString())).firstOrNull()
            people[key] =
                MentionTarget(
                    name,
                    position?.text("position_title"),
                    organizationName(position?.ref("organization")),
                    stakeholder?.let { RegisterSheets.code(it.id) },
                    row?.ref("residence_locality")?.let { source.one("locality", it)?.text("name") }
                )
        }
        return "[[$key]]"
    }

    // una mención escrita en la narrativa: [[person:<id>]], [[stakeholder:<id>]] o [[position:<id>]]; una que no existe queda sin
    // resolver y Mentions la imprime «una persona»
    suspend fun mention(key: String) {
        if (key in people) return
        val id = runCatching { UUID.fromString(key.substringAfter(':')) }.getOrNull() ?: return
        when (key.substringBefore(':')) {
            "person" -> person(id)
            "stakeholder" -> {
                val row = source.one("stakeholder", id) ?: return
                val code = RegisterSheets.code(row.id)
                val holder = row.ref("person")?.let { person(it).let { _ -> people["person:$it"] } }
                row.ref("person")?.let { holders[key] = it }
                val organization = organizationName(row.ref("organization"))
                people[key] =
                    holder?.copy(stakeholderCode = code)
                        ?: MentionTarget(organization ?: code, null, organization, code, row.ref("locality")?.let { source.one("locality", it)?.text("name") })
            }
            "position" -> {
                val row = source.one("position", id) ?: return
                val holder = row.ref("person")?.let { person(it).let { _ -> people["person:$it"] } }
                row.ref("person")?.let { holders[key] = it }
                people[key] =
                    MentionTarget(
                        holder?.name ?: row.text("position_title") ?: "Sin nombre",
                        row.text("position_title"),
                        organizationName(row.ref("organization")),
                        holder?.stakeholderCode,
                        holder?.locality
                    )
            }
        }
    }

    // los nombres de las personas de los grupos de interés del proyecto, para el aviso de datos personales (ASM-137)
    suspend fun projectNames() {
        for (row in source.all("stakeholder", byProject)) {
            val id = row.ref("person") ?: continue
            val person = source.one("person", id) ?: continue
            listOfNotNull(person.text("given_names"), person.text("surnames")).joinToString(" ").ifEmpty { null }?.let { names += it }
        }
    }

    // un grupo de interés como se nombra en una tabla: su persona (mención), su organización o su código GI-
    suspend fun stakeholder(row: SourceRow): String =
        row.ref("person")?.let { person(it) } ?: organizationName(row.ref("organization")) ?: RegisterSheets.code(row.id)

    // un grupo de interés por su cargo y su organización, sin nombre (Tablas 6.1 a 6.3): «Presidente, Junta Directiva…»
    suspend fun actor(row: SourceRow): String {
        val position = row.ref("person")?.let { position(it) }
        val organization = organizationName(position?.ref("organization") ?: row.ref("organization"))
        return listOfNotNull(position?.text("position_title"), organization).joinToString(", ").ifEmpty { RegisterSheets.code(row.id) }
    }

    companion object {
        const val VIGENTE = "VIGENTE"
    }
}
