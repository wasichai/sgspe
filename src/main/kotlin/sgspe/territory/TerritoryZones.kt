package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.toCodedException
import wasichai.core.data.RecordCriterion
import java.util.UUID

// una zona de las consultas (especificación de F6 §2.2 elección 4): se lee por Records como quien llama (404 si no existe o
// no está a su alcance; 403 sin READ sobre el objeto) y se resuelve a sus ids por relación, como el filtro de territorio de
// los tableros (dashboards.md §3.1): una localidad con sus sectores (parent_locality); una unidad administrativa con sus
// unidades hijas, sus localidades y sus comunidades; un área de influencia con sus miembros (y las unidades de ellos). la
// geometría es la de su límite (boundary o territory); una localidad sin límite filtra solo por relación
@Service
class TerritoryZones(
    private val records: Records,
    private val limits: TerritoryQueryProperties
) {
    suspend fun read(
        zoneObject: String,
        id: UUID
    ): Zone {
        val geometryField = GEOMETRY[zoneObject] ?: throw notFound(zoneObject)
        val zone = records.find(zoneObject, id) ?: throw notFound(zoneObject)
        val geometry = ZoneRules.usable(Records.geometriesOf(zone)[geometryField])
        val relations =
            when (zoneObject) {
                TerritoryNames.LOCALITY -> mapOf(TerritoryNames.LOCALITY to withSectors(setOf(id)))
                TerritoryNames.COMMUNITY -> mapOf(TerritoryNames.COMMUNITY to setOf(id))
                TerritoryNames.UNIT -> unitRelations(setOf(id))
                else -> areaRelations(id)
            }
        return Zone(relations, geometry)
    }

    private suspend fun withSectors(localities: Set<UUID>): Set<UUID> {
        if (localities.isEmpty()) return emptySet()
        return localities + ids(TerritoryNames.LOCALITY, Criteria.anyOf("parent_locality", localities))
    }

    private suspend fun unitRelations(roots: Set<UUID>): Map<String, Set<UUID>> {
        val units = roots.toMutableSet()
        var frontier: Set<UUID> = roots
        while (frontier.isNotEmpty()) {
            val children = ids(TerritoryNames.UNIT, Criteria.anyOf("parent_unit", frontier)) - units
            units += children
            frontier = children
        }
        return mapOf(
            TerritoryNames.LOCALITY to withSectors(ids(TerritoryNames.LOCALITY, Criteria.anyOf(TerritoryNames.UNIT, units))),
            TerritoryNames.COMMUNITY to ids(TerritoryNames.COMMUNITY, Criteria.anyOf(TerritoryNames.UNIT, units))
        )
    }

    private suspend fun areaRelations(area: UUID): Map<String, Set<UUID>> {
        val members = records.all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(TerritoryNames.AREA, listOf(area))), max = limits.maxRows)

        fun of(field: String): Set<UUID> = members.mapNotNull { Records.uuid(it.attributes[field]) }.toSet()
        val units = of(TerritoryNames.UNIT)
        val fromUnits = if (units.isEmpty()) emptyMap() else unitRelations(units)
        return mapOf(
            TerritoryNames.LOCALITY to withSectors(of(TerritoryNames.LOCALITY)) + fromUnits[TerritoryNames.LOCALITY].orEmpty(),
            TerritoryNames.COMMUNITY to of(TerritoryNames.COMMUNITY) + fromUnits[TerritoryNames.COMMUNITY].orEmpty(),
            TerritoryNames.AREA to setOf(area)
        )
    }

    private suspend fun ids(
        objectName: String,
        criterion: RecordCriterion
    ): Set<UUID> = records.all(objectName, criteria = listOf(criterion), max = limits.maxRows).mapNotNull { Records.uuid(it.id) }.toSet()

    private fun notFound(zoneObject: String) =
        Finding("ZONE_NOT_FOUND", "La zona $zoneObject no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()

    companion object {
        // el límite de cada objeto que puede ser zona
        val GEOMETRY: Map<String, String> =
            mapOf(
                TerritoryNames.LOCALITY to "boundary",
                TerritoryNames.COMMUNITY to "territory",
                TerritoryNames.UNIT to "boundary",
                TerritoryNames.AREA to "boundary"
            )
    }
}

// reglas puras de una zona: qué geometría llega a ST_GeomFromGeoJSON y qué campos tiene que poder leer quien llama para que el
// filtro de la zona no sea una lista incompleta sin decirlo
object ZoneRules {
    // una geometría con tipo y coordenadas (o geometrías) no vacías; cualquier otra cosa se trata como «sin geometría»
    fun usable(geometry: Map<String, Any?>?): Map<String, Any?>? {
        if (geometry == null) return null
        val type = geometry["type"] as? String
        if (type.isNullOrBlank()) return null
        val parts = geometry["coordinates"] ?: geometry["geometries"]
        return if (parts is Collection<*> && parts.isNotEmpty()) geometry else null
    }

    // los campos «objeto.campo» que el filtro usa para esta zona: las relaciones en las que la zona trae ids, las geometrías si
    // la zona tiene una y, con periodo, el campo de fecha
    fun required(
        of: String,
        target: ZoneTarget,
        zone: Zone,
        periodGiven: Boolean = false
    ): List<String> {
        val fields = mutableListOf<String>()
        target.relations.filter { zone.relations[it].orEmpty().isNotEmpty() }.forEach { fields += "$of.$it" }
        if (zone.geometry != null) target.geometries.forEach { fields += "$of.$it" }
        if (periodGiven) target.period?.let { fields += "$of.${it.field}" }
        return fields
    }
}
