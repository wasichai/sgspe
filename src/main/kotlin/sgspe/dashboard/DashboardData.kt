package sgspe.dashboard

import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.Positions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.engagement.AttendanceCounts
import sgspe.engagement.EngagementWriteGuard
import sgspe.stakeholder.FollowUp
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// los conjuntos que leen los indicadores de un pedido, cargados una vez con Records como quien llama (RecordService
// aplica el tenant y los permisos de objeto y de campo; nunca asPlatform) y agregados en Kotlin (dashboards.md §6). los
// filtros de territorio se resuelven a localidades y comunidades: una localidad incluye sus sectores, una unidad
// administrativa sus localidades y unidades hijas, un área de influencia los miembros de esa versión (dashboards.md §3.1).
// en los indicadores de grupos de interés el territorio filtra al grupo de interés (su localidad y su área de influencia;
// stakeholder no tiene comunidad, así que community no lo filtra y el KPI lo dice en ignoredFilters, ruling P27); en los
// de relacionamiento, a la interacción (su localidad o su comunidad).
// la lectura es acotada (dashboards.md §6.2, conjuntos pequeños): cada conjunto se lee con Records.all hasta maxRows
// filas, y las relaciones M2M se leen una interacción a la vez hasta maxRelated interacciones. pasarse no recorta la
// cifra en silencio: es un 400 TOO_MANY_RECORDS que pide acotar los filtros
class DashboardData(
    private val records: Records,
    val filters: DashboardFilters,
    // si quien mira puede leer objeto.campo (la definición filtrada por sus permisos, dashboards.md §8). sin valor por
    // defecto: es una barrera de privacidad y quien arma los datos tiene que decirla
    val readable: suspend (String) -> Boolean,
    private val maxRows: Int = MAX_ROWS,
    private val maxRelated: Int = MAX_RELATED
) {
    private val cache = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(
        key: String,
        load: suspend () -> T
    ): T = cache[key] as T? ?: load().also { cache[key] = it }

    // ---- lectura acotada ----

    private suspend fun read(
        objectName: String,
        equal: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null
    ): List<RecordResponse> = records.all(objectName, equal, criteria, ids, max = maxRows)

    // lo mismo por una lista de ids o por un campo que apunta a ellos, en tramos de CHUNK: el tope cuenta el conjunto entero
    private suspend fun readIn(
        objectName: String,
        field: String?,
        ids: Collection<String>,
        equal: Map<String, String> = emptyMap()
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().map(UUID::fromString).chunked(CHUNK)) {
            out +=
                if (field == null) {
                    read(objectName, equal, ids = chunk)
                } else {
                    read(objectName, equal, listOf(Criteria.anyOf(field, chunk)))
                }
            if (out.size > maxRows) throw Records.tooMany(objectName, maxRows)
        }
        return out
    }

    // los registros del otro lado de una M2M de cada interacción: una lectura por interacción, hasta maxRelated
    private suspend fun relatedOf(
        engagements: Collection<String>,
        relationship: String
    ): Map<String, List<RecordResponse>> {
        if (engagements.size > maxRelated) throw Records.tooMany("engagement", maxRelated)
        return engagements.associateWith { records.related("engagement", UUID.fromString(it), relationship) }
    }

    // ---- territorio ----

    class Territory(
        val localities: Set<String>,
        val communities: Set<String>,
        val area: String?,
        // el filtro community: los grupos de interés lo ignoran
        val community: Boolean = false
    )

    // los filtros de territorio pedidos, cada uno resuelto a localidades y comunidades; todos deben cumplirse
    private suspend fun territories(): List<Territory> =
        cached("territories") {
            val out = mutableListOf<Territory>()
            filters.locality?.let { out += Territory(withSectors(setOf(it.toString())), emptySet(), null) }
            filters.community?.let { out += Territory(emptySet(), setOf(it.toString()), null, community = true) }
            filters.administrativeUnit?.let { out += unitTerritory(setOf(it.toString())) }
            filters.areaOfInfluence?.let { out += areaTerritory(listOf(it.toString())) }
            out
        }

    // el territorio de los grupos de interés: sin community, que stakeholder no tiene (ruling P27; el KPI lo informa en
    // ignoredFilters en vez de quedar en cero)
    suspend fun stakeholderTerritories(): List<Territory> = territories().filterNot { it.community }

    // el territorio de las interacciones: los filtros más AIDS o AIIS del ámbito
    suspend fun engagementTerritories(): List<Territory> = territories() + scopeTerritory()

    private suspend fun withSectors(localities: Set<String>): Set<String> {
        if (localities.isEmpty()) return emptySet()
        return localities + readIn("locality", "parent_locality", localities).map { it.id }
    }

    private suspend fun unitTerritory(roots: Set<String>): Territory {
        val units = roots.toMutableSet()
        var frontier: Set<String> = roots
        while (frontier.isNotEmpty()) {
            val children = readIn("administrative_unit", "parent_unit", frontier).map { it.id }.toSet() - units
            units += children
            frontier = children
        }
        val localities = readIn("locality", "administrative_unit", units).map { it.id }.toSet()
        val communities = readIn("community", "administrative_unit", units).map { it.id }.toSet()
        return Territory(withSectors(localities), communities, null)
    }

    private suspend fun areaTerritory(areas: List<String>): Territory {
        if (areas.isEmpty()) return Territory(emptySet(), emptySet(), null)
        val members = readIn("area_of_influence_member", "area_of_influence", areas)
        val localities = members.mapNotNull { Values.text(it.attributes["locality"]) }.toSet()
        val communities = members.mapNotNull { Values.text(it.attributes["community"]) }.toSet()
        val units = members.mapNotNull { Values.text(it.attributes["administrative_unit"]) }.toSet()
        val fromUnits = if (units.isEmpty()) Territory(emptySet(), emptySet(), null) else unitTerritory(units)
        return Territory(withSectors(localities) + fromUnits.localities, communities + fromUnits.communities, areas.singleOrNull())
    }

    // AIDS y AIIS, en los indicadores de relacionamiento, son los miembros de las áreas aprobadas de ese tipo de los
    // proyectos; REGIONAL y NACIONAL solo filtran grupos de interés (dashboards.md §3.1). null: el ámbito no filtra
    private suspend fun scopeTerritory(): List<Territory> =
        cached("scopeTerritory") {
            val scope = filters.scope
            if (scope == AIDS || scope == AIIS) {
                val areas =
                    read(
                        "area_of_influence",
                        mapOf("area_type" to scope),
                        listOf(Criteria.inState(setOf(APPROVED)), Criteria.anyOf("project", filters.projects))
                    ).map { it.id }
                listOf(areaTerritory(areas))
            } else {
                emptyList()
            }
        }

    // ---- grupos de interés ----

    // los grupos de interés de los proyectos, filtrados por territorio y ámbito (vigentes y retirados: cada KPI decide)
    suspend fun stakeholders(): List<StakeholderRow> =
        cached("stakeholders") {
            if (filters.projects.isEmpty()) return@cached emptyList<StakeholderRow>()
            val all = read("stakeholder", criteria = listOf(Criteria.anyOf("project", filters.projects)))
            val territories = stakeholderTerritories()
            all
                .filter { s ->
                    val locality = Values.text(s.attributes["locality"])
                    val area = Values.text(s.attributes["area_of_influence"])
                    territories.all { t -> (locality != null && locality in t.localities) || (t.area != null && area == t.area) } &&
                        (filters.scope == null || Values.text(s.attributes["stakeholder_scope"]) == filters.scope)
                }.map {
                    val a = it.attributes
                    StakeholderRow(
                        it.id,
                        it.state,
                        Values.text(a["influence_group"]),
                        Values.text(a["stakeholder_scope"]),
                        Values.text(a["stakeholder_type"]),
                        Values.text(a["stance"]),
                        Values.decimal(a["interest_index"]),
                        Values.decimal(a["power_index"]),
                        Values.text(a["person"]),
                        Values.text(a["organization"])
                    )
                }
        }

    // los vigentes (dashboards.md §3.2: todos salvo retired)
    suspend fun currentStakeholders(): List<StakeholderRow> = stakeholders().filter { it.current }

    suspend fun assessments(): Map<String, List<AssessmentRow>> =
        cached("assessments") {
            readIn("stakeholder_assessment", "stakeholder", stakeholders().map { it.id })
                .mapNotNull { r ->
                    val a = r.attributes
                    val stakeholder = Values.text(a["stakeholder"]) ?: return@mapNotNull null
                    val date = Values.text(a["assessment_date"])?.let(LocalDate::parse) ?: return@mapNotNull null
                    AssessmentRow(
                        stakeholder,
                        date,
                        Values.text(a["stance"]),
                        Values.decimal(a["trust_score"])?.toInt(),
                        Values.bool(a["group_overridden"]) == true,
                        Values.text(a["override_reason"]),
                        r.createdAt,
                        Values.decimal(a["influence_score"])
                    )
                }.groupBy { it.stakeholder }
        }

    // ---- relacionamiento ----

    // las interacciones realizadas (EngagementWriteGuard.HELD_STATES) de los proyectos con held_at entre dos fechas de
    // Lima; con `territorial`, filtradas por el territorio y el ámbito de la interacción
    private suspend fun held(
        from: LocalDate,
        to: LocalDate,
        territorial: Boolean
    ): List<EngagementRow> =
        cached("held:$from:$to:$territorial") {
            if (filters.projects.isEmpty()) return@cached emptyList<EngagementRow>()
            val start = from.atStartOfDay(LIMA).toInstant()
            val until = to.plusDays(1).atStartOfDay(LIMA).toInstant()
            val all =
                read(
                    "engagement",
                    criteria =
                        listOf(
                            Criteria.anyOf("project", filters.projects),
                            Criteria.inState(EngagementWriteGuard.HELD_STATES),
                            Criteria.between("held_at", start, until)
                        )
                )
            val territories = if (territorial) engagementTerritories() else emptyList()
            all
                .filter { e ->
                    val locality = Values.text(e.attributes["locality"])
                    val community = Values.text(e.attributes["community"])
                    territories.all { t -> (locality != null && locality in t.localities) || (community != null && community in t.communities) }
                }.mapNotNull {
                    val a = it.attributes
                    val date = FollowUp.dateOf(a["held_at"]) ?: return@mapNotNull null
                    EngagementRow(
                        it.id,
                        Values.text(a["project"]),
                        Values.text(a["engagement_type"]),
                        Values.text(a["title"]),
                        date,
                        AttendanceCounts.FIELDS.associateWith { f -> Values.decimal(a[f])?.toLong() }
                    )
                }
        }

    suspend fun inPeriod(): List<EngagementRow> = held(filters.periodStart, filters.periodEnd, territorial = true)

    // los 12 meses que terminan en periodEnd, para las series
    suspend fun window(): List<EngagementRow> = held(YearMonth.from(filters.periodEnd).minusMonths(11).atDay(1), filters.periodEnd, territorial = true)

    suspend fun participants(engagements: Collection<String>): List<RecordResponse> = readIn("engagement_participant", "engagement", engagements)

    // los contactos del periodo con los grupos de interés vigentes del filtro (dashboards.md §3.3): una interacción
    // realizada con held_at en el periodo, por el vínculo M2M engagement_stakeholder o por un participante con attended.
    // el territorio y el ámbito filtran al grupo de interés, no a la interacción. KPI-STK-04, 05 y 07 reciben esta misma
    // lista, así que el denominador de KPI-STK-07 es el conjunto contado por KPI-STK-04
    suspend fun contacts(): List<Contact> =
        cached("contacts") {
            val current = currentStakeholders().map { it.id }.toSet()
            val rows = held(filters.periodStart, filters.periodEnd, territorial = false)
            val byId = rows.associateBy { it.id }
            val out = mutableListOf<Contact>()
            relatedOf(byId.keys, "engagement_stakeholder").forEach { (engagement, linked) ->
                val row = byId.getValue(engagement)
                linked.forEach { out += Contact(it.id, row.id, row.heldDate) }
            }
            participants(byId.keys)
                .filter { Values.bool(it.attributes["attended"]) == true }
                .forEach { p ->
                    val stakeholder = Values.text(p.attributes["stakeholder"]) ?: return@forEach
                    val engagement = byId[Values.text(p.attributes["engagement"])] ?: return@forEach
                    out += Contact(stakeholder, engagement.id, engagement.heldDate)
                }
            out.filter { it.stakeholder in current }.distinct()
        }

    suspend fun questions(engagements: Collection<String>): List<QuestionRow> =
        readIn("engagement_question", "engagement", engagements).mapNotNull { q ->
            QuestionRow(
                Values.text(q.attributes["engagement"]) ?: return@mapNotNull null,
                Values.text(q.attributes["topic"]),
                Values.bool(q.attributes["answered_on_site"]) == true || Values.text(q.attributes["answered_at"]) != null
            )
        }

    suspend fun topicsOf(engagements: Collection<String>): Map<String, Set<String>> =
        relatedOf(engagements, "engagement_topic").mapValues { (_, topics) -> topics.map { it.id }.toSet() }

    // el campo `field` de cada registro, solo si quien mira puede leerlo; si no, ninguno (la etiqueta cae a otra cosa)
    suspend fun names(
        objectName: String,
        ids: Collection<String>,
        field: String = "name"
    ): Map<String, String> {
        if (ids.isEmpty() || !readable("$objectName.$field")) return emptyMap()
        return readIn(objectName, null, ids).associate { it.id to (Values.text(it.attributes[field]) ?: "") }
    }

    // el nombre de cada persona, solo si quien mira puede leerlo (permiso de campo, security.md §7.9): sin lectura de
    // given_names no se pide ningún registro de person
    suspend fun namesOfPeople(ids: Collection<String>): Map<String, String> {
        if (ids.isEmpty() || !readable("person.given_names")) return emptyMap()
        val surnames = readable("person.surnames")
        return readIn("person", null, ids)
            .mapNotNull { p ->
                val parts = listOfNotNull(Values.text(p.attributes["given_names"]), if (surnames) Values.text(p.attributes["surnames"]) else null)
                val name = parts.joinToString(" ")
                if (name.isBlank()) null else p.id to name
            }.toMap()
    }

    // los cargos VIGENTE de cada persona (Positions); vacío si quien mira no lee los cargos
    suspend fun currentPositions(people: Collection<String>): Map<String, List<Positions.Current>> {
        if (people.isEmpty() || !readable("${Positions.OBJECT}.${Positions.STATUS}")) return emptyMap()
        return readIn(Positions.OBJECT, "person", people, mapOf(Positions.STATUS to Positions.CURRENT))
            .mapNotNull(Positions::current)
            .groupBy { it.person }
    }

    // «cargo, organización» de los cargos vigentes de cada persona
    suspend fun positionLabels(people: Collection<String>): Map<String, List<String>> {
        if (!readable("${Positions.OBJECT}.position_title")) return emptyMap()
        val positions = currentPositions(people)
        val organizations = names("organization", positions.values.flatten().mapNotNull { it.organization })
        return positions.mapValues { (_, list) -> list.map { Positions.label(it, organizations) }.filter { it.isNotBlank() } }
    }

    // las organizaciones de los cargos vigentes de cada persona
    suspend fun positionOrganizations(people: Collection<String>): Map<String, List<String>> {
        val positions = currentPositions(people)
        val organizations = names("organization", positions.values.flatten().mapNotNull { it.organization })
        return positions.mapValues { (_, list) -> list.mapNotNull { p -> p.organization?.let(organizations::get)?.ifBlank { null } }.distinct() }
    }

    suspend fun projectRecords(): List<RecordResponse> = cached("projects") { readIn("project", null, filters.projects.map { it.toString() }) }

    // la meta de cada mes que pide KPI-ENG-02 (la serie de 12 meses y todos los del periodo), una entrada por mes ya sumada
    // entre los proyectos del filtro (EngagementKpis.monthlyTargets): la del plan vigente en el mes o, si no hay, la del
    // proyecto (2 por defecto, ProjectParameters) (RN-ENG-003)
    suspend fun monthlyTargets(): List<MonthlyTarget> {
        val plans =
            if (filters.projects.isEmpty()) {
                emptyList()
            } else {
                read("engagement_plan", criteria = listOf(Criteria.anyOf("project", filters.projects))).mapNotNull { p ->
                    val a = p.attributes
                    EngagementKpis.PlanTarget(
                        p.id,
                        Values.text(a["project"]) ?: return@mapNotNull null,
                        Values.text(a["start_date"])?.let(LocalDate::parse),
                        Values.text(a["end_date"])?.let(LocalDate::parse),
                        Values.decimal(a["meetings_per_month_target"])?.toLong() ?: return@mapNotNull null
                    )
                }
            }
        val projects = projectRecords().associate { it.id to ProjectParameters.integer(it.attributes, "meetings_per_month_target") }
        return EngagementKpis.monthlyTargets(EngagementKpis.targetMonths(filters.periodStart, filters.periodEnd), projects, plans)
    }

    companion object {
        const val CHUNK = 500

        // el tope de filas de un conjunto y de interacciones cuyas M2M se leen una a una, en serie (dashboards.md §6.2;
        // sin requisito de volumen hasta GAP-027). 200 lecturas de related son unos segundos por el túnel y menos de uno
        // con la base al lado
        const val MAX_ROWS = 20_000
        const val MAX_RELATED = 200

        private const val AIDS = "AIDS"
        private const val AIIS = "AIIS"
        private const val APPROVED = "approved"
    }
}
