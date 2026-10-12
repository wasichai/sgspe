package sgspe.territory

import tools.jackson.databind.json.JsonMapper
import wasichai.core.data.RecordCriterion
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.gis.srid
import java.util.UUID

// una zona ya leída como quien llama (TerritoryZones): sus ids por campo de relación (locality con sus sectores, community,
// area_of_influence) y su geometría en GeoJSON EPSG:4326, o null si no tiene
data class Zone(
    val relations: Map<String, Set<UUID>>,
    val geometry: Map<String, Any?>?
)

// el campo de fecha por el que se acota el periodo: DATETIME (instant, un instante de Lima) o DATE
data class PeriodField(
    val field: String,
    val instant: Boolean
)

// cómo se asocia un objeto a una zona: sus relaciones territoriales (gis.md §2.4 y las relaciones locality y community del
// modelo), sus geometrías propias y su fecha para el periodo (null si no tiene)
data class ZoneTarget(
    val relations: List<String>,
    val geometries: List<String>,
    val period: PeriodField?
)

// los objetos que lista GET /api/sgspe/territory/zones/{zoneObject}/{id}/records (casos 2, 3 y 5 de gis.md §5.3, más las QCS,
// las interacciones, los incidentes y las señales). un grupo de interés nunca se ubica por un punto (RQ-GIS-015): solo por su
// localidad y su área de influencia
object ZoneTargets {
    private val TERRITORIAL = listOf("locality", "community")
    private val RECEIVED = PeriodField("received_at", instant = true)

    val ALL: Map<String, ZoneTarget> =
        mapOf(
            "stakeholder" to ZoneTarget(listOf("locality", "area_of_influence"), emptyList(), null),
            "commitment" to ZoneTarget(TERRITORIAL, listOf("location"), PeriodField("agreed_date", instant = false)),
            "social_risk" to ZoneTarget(TERRITORIAL + "area_of_influence", listOf("location"), null),
            "grievance" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED),
            "inquiry" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED),
            "stakeholder_request" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED),
            "engagement" to ZoneTarget(TERRITORIAL, listOf("location"), PeriodField("held_at", instant = true)),
            "incident" to ZoneTarget(TERRITORIAL, listOf("location"), PeriodField("occurred_at", instant = true)),
            "early_warning_signal" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED)
        )
}

// los RecordCriterion de los filtros por zona (patrón a de gis.md §5.2): la geometría de referencia viaja enlazada como
// GeoJSON (ST_GeomFromGeoJSON), en el SRID de cada campo; los ids de la zona, como un arreglo enlazado. reciben la definición
// que wasichai filtró por quien llama: un campo que no puede leer no filtra (no sería un oráculo sobre su valor), y si no
// queda nada con qué filtrar, ningún registro pasa (FALSE)
object ZoneCriterion {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    // el registro nombra la zona por alguna relación o alguna de sus geometrías la interseca
    fun of(
        relations: List<String>,
        geometries: List<String>,
        zone: Zone,
        excludeTouching: Boolean = false
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val terms = mutableListOf<String>()
            for (field in relations) {
                val ids = zone.relations[field].orEmpty()
                if (ids.isEmpty()) continue
                val column = columnOf(definition, field) ?: continue
                terms += "$column = ANY(${bind(ids.toTypedArray())})"
            }
            val geometry = zone.geometry
            if (geometry != null) {
                for (field in geometries) {
                    val found = definition.fields.firstOrNull { it.name == field } ?: continue
                    val srid = found.srid ?: continue
                    val column = SqlIdentifier.quote(found.columnName)
                    val reference = "ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(${bind(json(geometry))}), 4326), $srid)"
                    terms +=
                        if (excludeTouching) {
                            // una geometría que solo toca el borde de la referencia no está dentro de ella
                            "(ST_Intersects($column, $reference) AND NOT ST_Touches($column, $reference))"
                        } else {
                            "ST_Intersects($column, $reference)"
                        }
                }
            }
            if (terms.isEmpty()) NOTHING else terms.joinToString(" OR ")
        }

    // alguna geometría del registro está a menos de `meters` de la referencia, medido en `srid` (el working_crs): el prefiltro
    // usa el índice GIST con la envolvente ampliada en grados (gis.md §3.4) y la medida exacta va en metros
    fun within(
        geometries: List<String>,
        reference: Map<String, Any?>,
        meters: Number,
        srid: Int
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val terms =
                geometries.mapNotNull { name ->
                    val found = definition.fields.firstOrNull { it.name == name } ?: return@mapNotNull null
                    val fieldSrid = found.srid ?: return@mapNotNull null
                    val column = SqlIdentifier.quote(found.columnName)
                    val ref = "ST_SetSRID(ST_GeomFromGeoJSON(${bind(json(reference))}), 4326)"
                    "($column && ST_Transform(ST_Expand($ref, ${bind(SpatialSql.degrees(meters))}), $fieldSrid) AND " +
                        "ST_DWithin(ST_Transform($column, ${bind(srid)}), ST_Transform($ref, ${bind(srid)}), ${bind(meters.toDouble())}))"
                }
            if (terms.isEmpty()) NOTHING else terms.joinToString(" OR ")
        }

    // el campo no es verdadero (falso o vacío): las versiones vigentes de project_component. sin la columna, ninguno
    fun notTrue(field: String): RecordCriterion = RecordCriterion { definition, _ -> columnOf(definition, field)?.let { "$it IS NOT TRUE" } ?: NOTHING }

    private const val NOTHING = "FALSE"

    private fun columnOf(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    private fun json(geometry: Map<String, Any?>): String = JSON.writeValueAsString(geometry)
}
