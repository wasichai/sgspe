package sgspe.territory

import sgspe.scope.ProjectScope

// la fecha por la que una fuente de las capas de gestión se acota al periodo (gis.md §2.2, «con filtro de periodo»)
sealed interface LayerPeriod {
    val fields: List<String>

    // un DATETIME: desde el primer instante del periodo en Lima hasta el primero que ya no está
    data class Moment(
        val field: String
    ) : LayerPeriod {
        override val fields: List<String> get() = listOf(this.field)
    }

    // una DATE, con los dos extremos del periodo incluidos
    data class Day(
        val field: String
    ) : LayerPeriod {
        override val fields: List<String> get() = listOf(this.field)
    }

    // un tramo de dos DATE que cruza el periodo: empieza a más tardar su último día y termina (o sigue abierto) desde su
    // primer día; sin inicio no cruza ninguno
    data class Span(
        val start: String,
        val end: String
    ) : LayerPeriod {
        override val fields: List<String> get() = listOf(start, end)
    }
}

// de dónde toma su punto un registro sin geometría propia (gis.md §2.4, «relación antes que coordenada»): el objeto al que
// apunta la relación, sus puntos y, si no tiene, un punto sobre la superficie de sus polígonos, en ese orden
data class PlacementTarget(
    val objectName: String,
    val points: List<String>,
    val surfaces: List<String>
) {
    val geometries: List<String> get() = points + surfaces
}

// las relaciones por las que un registro cae a un territorio: el campo se llama como el objeto al que apunta
object Placements {
    val BY_RELATION: Map<String, PlacementTarget> =
        mapOf(
            TerritoryNames.LOCALITY to PlacementTarget(TerritoryNames.LOCALITY, listOf("location"), listOf("boundary")),
            TerritoryNames.COMMUNITY to PlacementTarget(TerritoryNames.COMMUNITY, emptyList(), listOf("territory")),
            TerritoryNames.AREA to PlacementTarget(TerritoryNames.AREA, emptyList(), listOf("boundary"))
        )
}

// una fuente de registros de una capa de gestión: su geometría propia (o null), las relaciones por las que cae a un territorio
// en orden y su fecha (null: el periodo no la acota)
data class PlacedSource(
    val objectName: String,
    val geometry: String?,
    val relations: List<String> = emptyList(),
    val period: LayerPeriod? = null
)

// los agregados por localidad, que nunca nombran un registro, con los objetos que leen
enum class LayerAggregate(
    val objects: List<String>
) {
    // LYR-17: grupos de interés por postura en su localidad (RQ-GIS-015)
    STAKEHOLDERS_BY_STANCE(listOf("stakeholder", TerritoryNames.LOCALITY)),

    // LYR-23: aportes ejecutados por localidad, sumados por moneda (ASM-063)
    CONTRIBUTIONS_BY_CURRENCY(listOf("social_contribution", TerritoryNames.LOCALITY))
}

data class ManagementLayer(
    val id: String,
    val sources: List<PlacedSource> = emptyList(),
    val aggregate: LayerAggregate? = null
) {
    // los objetos que lee la capa: sus fuentes, los destinos de sus relaciones y los de su agregado. sin READ sobre alguno,
    // 403 (api.md, capas de layers/{layer})
    val objects: List<String>
        get() =
            (
                sources.flatMap { source -> listOf(source.objectName) + source.relations.map { Placements.BY_RELATION.getValue(it).objectName } } +
                    aggregate?.objects.orEmpty()
            ).distinct()
}

// las capas de gestión LYR-17 a LYR-24 de gis.md §2.2 (plan 6.2c). las fuentes de una capa se leen en este orden y comparten
// el tope de elementos; una relación cae a su territorio aunque la fila de §2.2 solo nombre location, porque la regla de
// §2.4 es general (decisión 4 del plan)
object ManagementLayerCatalog {
    private val TERRITORIAL = listOf(TerritoryNames.LOCALITY, TerritoryNames.COMMUNITY)

    // los padres por los que un hijo de ProjectScope se acota por proyecto, todos a un salto de una raíz
    // (ManagementLayerCatalogTest); vacía para una raíz, que se acota por su campo project
    fun parents(objectName: String): List<Pair<String, String>> =
        if (objectName in ProjectScope.ROOTS) {
            emptyList()
        } else {
            ProjectScope.CHILDREN[objectName] ?: error("$objectName no está clasificado en ProjectScope")
        }

    val CATALOG: Map<String, ManagementLayer> =
        listOf(
            ManagementLayer("LYR-17", aggregate = LayerAggregate.STAKEHOLDERS_BY_STANCE),
            ManagementLayer(
                "LYR-18",
                listOf(
                    PlacedSource("engagement", "location", TERRITORIAL, LayerPeriod.Moment("held_at")),
                    PlacedSource("field_visit", "route", period = LayerPeriod.Span("actual_start_date", "actual_end_date")),
                    PlacedSource("field_observation", "location", listOf(TerritoryNames.LOCALITY), LayerPeriod.Moment("observed_at"))
                )
            ),
            ManagementLayer("LYR-19", listOf(PlacedSource("commitment", "location", TERRITORIAL, LayerPeriod.Day("agreed_date")))),
            ManagementLayer(
                "LYR-20",
                listOf("grievance", "inquiry", "stakeholder_request").map { PlacedSource(it, "location", TERRITORIAL, LayerPeriod.Moment("received_at")) }
            ),
            ManagementLayer("LYR-21", listOf(PlacedSource("social_risk", "location", TERRITORIAL + TerritoryNames.AREA))),
            ManagementLayer(
                "LYR-22",
                listOf(
                    PlacedSource("incident", "location", TERRITORIAL, LayerPeriod.Moment("occurred_at")),
                    PlacedSource("early_warning_signal", "location", TERRITORIAL, LayerPeriod.Moment("received_at")),
                    PlacedSource("early_warning", "location", TERRITORIAL, LayerPeriod.Moment("detected_at"))
                )
            ),
            ManagementLayer(
                "LYR-23",
                listOf(
                    PlacedSource("social_initiative", "location", TERRITORIAL, LayerPeriod.Span("start_date", "end_date")),
                    PlacedSource("delivery", "location", period = LayerPeriod.Day("delivery_date"))
                ),
                LayerAggregate.CONTRIBUTIONS_BY_CURRENCY
            ),
            ManagementLayer("LYR-24", listOf(PlacedSource("evidence", "location", period = LayerPeriod.Moment("captured_at"))))
        ).associateBy { it.id }
}
