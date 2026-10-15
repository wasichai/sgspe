package sgspe.deliverable

import org.springframework.stereotype.Component
import sgspe.common.ObjectDefinitions
import sgspe.territory.GeometryRef
import sgspe.territory.MapExtent
import sgspe.territory.MapFeature
import sgspe.territory.MapLayerQuery
import sgspe.territory.ReportMapFeatures
import wasichai.core.common.NotFoundException
import wasichai.gis.GeoServerLayers
import java.util.UUID

// el nombre de una capa publicada en GeoServer (<tabla física>__<columna>) y la columna de cada campo que el estilo nombra
data class PublishedName(
    val layer: String,
    val columns: Map<String, String>
)

// lo que los mapas de informe leen de la base, como la plataforma: un puerto para que GeoServerReportMapsTest no necesite base
interface MapData {
    suspend fun extent(
        organizationId: UUID,
        srid: Int,
        refs: List<GeometryRef>
    ): MapExtent?

    suspend fun features(
        organizationId: UUID,
        srid: Int,
        query: MapLayerQuery,
        bbox: MapExtent,
        tolerance: Double,
        max: Int
    ): List<MapFeature>

    // null si el tenant no tiene el objeto, la geometría o alguno de los campos
    suspend fun published(
        organizationId: UUID,
        objectName: String,
        geometry: String,
        fields: List<String>
    ): PublishedName?
}

// la implementación: las geometrías por ReportMapFeatures (SpatialGate) y los nombres físicos por ObjectDefinitions, como
// WmsProxy
@Component
class TerritoryMapData(
    private val reader: ReportMapFeatures,
    private val definitions: ObjectDefinitions
) : MapData {
    override suspend fun extent(
        organizationId: UUID,
        srid: Int,
        refs: List<GeometryRef>
    ): MapExtent? = reader.extent(organizationId, srid, refs)

    override suspend fun features(
        organizationId: UUID,
        srid: Int,
        query: MapLayerQuery,
        bbox: MapExtent,
        tolerance: Double,
        max: Int
    ): List<MapFeature> = reader.features(organizationId, srid, query, bbox, tolerance, max)

    override suspend fun published(
        organizationId: UUID,
        objectName: String,
        geometry: String,
        fields: List<String>
    ): PublishedName? {
        val definition =
            try {
                definitions.current(organizationId, objectName)
            } catch (_: NotFoundException) {
                return null
            }
        val column = definition.fields.firstOrNull { it.name == geometry }?.columnName ?: return null
        val columns = fields.associateWith { name -> definition.fields.firstOrNull { it.name == name }?.columnName ?: return null }
        return PublishedName(GeoServerLayers.name(definition.obj.physicalTable, column), columns)
    }
}
