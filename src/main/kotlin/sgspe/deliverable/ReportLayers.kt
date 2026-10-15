package sgspe.deliverable

import sgspe.territory.GeometryRef
import sgspe.territory.MapLayerQuery
import java.util.UUID

// lo que una capa en línea necesita saber del mapa para armar su consulta
data class MapContext(
    val project: UUID,
    val community: UUID?,
    val areas: List<UUID>,
    val concessions: List<UUID>
)

// una capa de un mapa de informe por su LYR-NN (gis.md §2.2): publicada en GeoServer (layers.json, por nombre con el estilo de
// la paleta; `filter` es la igualdad de LYR-03) o en línea (sus elementos los lee ReportMapFeatures como la plataforma;
// `confidential` sale de EXTERNO, ASM-124)
sealed interface ReportLayer {
    val code: String
    val legend: String
    val style: LayerStyle

    data class Published(
        override val code: String,
        override val legend: String,
        override val style: LayerStyle,
        val objectName: String,
        val geometry: String,
        val label: String?,
        val filter: Pair<String, String>? = null
    ) : ReportLayer

    data class Inline(
        override val code: String,
        override val legend: String,
        override val style: LayerStyle,
        val confidential: Boolean,
        val query: (MapContext) -> MapLayerQuery?
    ) : ReportLayer
}

// el catálogo de capas de los mapas de informe (especificación de F8 §3.7 paso 2; decisión 8 del plan 8.2c). los predios nunca
// llevan etiqueta (ni titular ni partida en ningún nivel); las capas en línea solo etiquetan name o code
object ReportLayers {
    private val published = ReportLayerStyles.PUBLISHED
    private val inline = ReportLayerStyles.INLINE
    private const val COMPONENT = "project_component"
    private const val PARCEL = "land_parcel"
    private val COMPANY_PARCELS = setOf("PROPIEDAD DE LA EMPRESA")
    private val OTHER_PARCELS = setOf("PROPIEDAD", "POSESION", "COMUNAL")
    private val FACILITIES = setOf("PLATAFORMA", "CAMPAMENTO", "CAPTACION")

    // las concesiones del proyecto, en línea con estilo propio sobre la capa publicada de todas las concesiones (LYR-07)
    val PROJECT_CONCESSIONS: ReportLayer.Inline =
        ReportLayer.Inline("LYR-07", "Concesión del proyecto", inline.getValue("PROJECT-CONCESSION"), false) { ctx ->
            ctx.concessions.takeIf { it.isNotEmpty() }?.let { MapLayerQuery("mining_concession", "boundary", ids = it, label = "code") }
        }

    private val CATALOG: Map<String, List<ReportLayer>> =
        mapOf(
            "LYR-01" to
                listOf(ReportLayer.Published("LYR-01", "Límite distrital", published.getValue("sgspe-lyr-01"), "administrative_unit", "boundary", "name")),
            "LYR-02" to
                listOf(
                    ReportLayer.Published("LYR-02", "Centro poblado", published.getValue("sgspe-lyr-02-location"), "locality", "location", "name"),
                    ReportLayer.Published("LYR-02", "Centro poblado", published.getValue("sgspe-lyr-02-boundary"), "locality", "boundary", "name")
                ),
            "LYR-03" to
                listOf(
                    ReportLayer.Published(
                        "LYR-03",
                        "Capital provincial",
                        published.getValue("sgspe-lyr-03"),
                        "locality",
                        "location",
                        "name",
                        "locality_type" to "CIUDAD"
                    )
                ),
            "LYR-04" to listOf(ReportLayer.Published("LYR-04", "Vía", published.getValue("sgspe-lyr-04"), "road_access", "trace", null)),
            "LYR-05" to
                listOf(
                    ReportLayer.Published(
                        "LYR-05",
                        "Territorio de la comunidad campesina",
                        published.getValue("sgspe-lyr-05"),
                        "community",
                        "territory",
                        "name"
                    )
                ),
            "LYR-06" to
                listOf(
                    ReportLayer.Inline("LYR-06", "Sectores comunales", inline.getValue("LYR-06"), false) { ctx ->
                        ctx.community?.let {
                            MapLayerQuery(
                                "community_sector",
                                "boundary",
                                equals = mapOf("community" to setOf(it.toString())),
                                label = "name"
                            )
                        }
                    }
                ),
            "LYR-07" to
                listOf(
                    ReportLayer.Published("LYR-07", "Concesión minera", published.getValue("sgspe-lyr-07"), "mining_concession", "boundary", "code")
                ),
            "LYR-08" to
                listOf(
                    ReportLayer.Inline("LYR-08", "Ríos y quebradas", inline.getValue("LYR-08"), false) {
                        MapLayerQuery("environmental_feature", "trace", equals = mapOf("feature_type" to setOf("FUENTE DE AGUA")), label = "name")
                    }
                ),
            "LYR-09" to
                listOf(
                    ReportLayer.Inline("LYR-09", "Área de interés del proyecto", inline.getValue("LYR-09-area"), false) { ctx ->
                        MapLayerQuery("project", "interest_area", ids = listOf(ctx.project))
                    },
                    ReportLayer.Inline("LYR-09", "Ubicación del proyecto", inline.getValue("LYR-09-location"), false) { ctx ->
                        MapLayerQuery("project", "location", ids = listOf(ctx.project))
                    }
                ),
            "LYR-10" to
                listOf(
                    ReportLayer.Inline("LYR-10", "Pozos programados y sondajes", inline.getValue("LYR-10"), true) { ctx ->
                        MapLayerQuery(
                            COMPONENT,
                            "location",
                            equals = mapOf("component_type" to setOf("SONDAJE")),
                            project = ctx.project,
                            label = "code",
                            currentOnly = true
                        )
                    }
                ),
            "LYR-11" to
                listOf(
                    ReportLayer.Inline("LYR-11", "Plataformas, campamento y captaciones", inline.getValue("LYR-11"), true) { ctx ->
                        MapLayerQuery(COMPONENT, "footprint", equals = mapOf("component_type" to FACILITIES), project = ctx.project, currentOnly = true)
                    },
                    ReportLayer.Inline("LYR-11", "Plataformas, campamento y captaciones", inline.getValue("LYR-11-point"), true) { ctx ->
                        MapLayerQuery(COMPONENT, "location", equals = mapOf("component_type" to FACILITIES), project = ctx.project, currentOnly = true)
                    }
                ),
            "LYR-13" to
                listOf(
                    ReportLayer.Inline("LYR-13", "Área de influencia directa social", inline.getValue("LYR-13-AIDS"), false) { ctx ->
                        ctx.areas.takeIf { it.isNotEmpty() }?.let {
                            MapLayerQuery(
                                "area_of_influence",
                                "boundary",
                                equals = mapOf("area_type" to setOf("AIDS")),
                                ids = it
                            )
                        }
                    },
                    ReportLayer.Inline("LYR-13", "Área de influencia indirecta social", inline.getValue("LYR-13-AIIS"), false) { ctx ->
                        ctx.areas.takeIf { it.isNotEmpty() }?.let {
                            MapLayerQuery(
                                "area_of_influence",
                                "boundary",
                                equals = mapOf("area_type" to setOf("AIIS")),
                                ids = it
                            )
                        }
                    }
                ),
            "LYR-14" to
                listOf(
                    ReportLayer.Inline("LYR-14", "Predios de propiedad, posesión y comunales", inline.getValue("LYR-14"), false) {
                        MapLayerQuery(PARCEL, "boundary", equals = mapOf("tenure_type" to OTHER_PARCELS))
                    }
                ),
            "LYR-15" to
                listOf(
                    ReportLayer.Inline("LYR-15", "Predios de la empresa", inline.getValue("LYR-15"), true) {
                        MapLayerQuery(PARCEL, "boundary", equals = mapOf("tenure_type" to COMPANY_PARCELS))
                    }
                )
        )

    fun of(code: String): List<ReportLayer> = CATALOG[code] ?: throw IllegalArgumentException("La capa $code no está en los mapas de informe")

    // las capas del pedido en su orden y, si trae LYR-07 y concesiones, las del proyecto encima
    fun forRequest(request: MapRequest): List<ReportLayer> =
        request.layers.flatMap(::of) + listOfNotNull(PROJECT_CONCESSIONS.takeIf { "LYR-07" in request.layers && request.concessions.isNotEmpty() })

    // las capas de un mapa EXTERNO: sin las confidenciales (LYR-10, LYR-11 y LYR-15, ASM-124)
    // falla cerrado: solo pasan las capas de una lista explícita (todas menos LYR-10, LYR-11 y LYR-15); una capa nueva queda
    // fuera de EXTERNO hasta que alguien la agregue aquí
    val EXTERNAL_CODES: Set<String> =
        setOf("LYR-01", "LYR-02", "LYR-03", "LYR-04", "LYR-05", "LYR-06", "LYR-07", "LYR-08", "LYR-09", "LYR-13", "LYR-14")

    fun external(layers: List<ReportLayer>): List<ReportLayer> = layers.filter { it.code in EXTERNAL_CODES && !(it is ReportLayer.Inline && it.confidential) }

    // las geometrías que encuadran el mapa (decisión 9 del plan 8.2c)
    fun frameRefs(request: MapRequest): List<GeometryRef> {
        val project = GeometryRef("project", "location", listOf(request.project))
        val community = request.community?.let { GeometryRef("community", "territory", listOf(it)) }
        return when (request.frame) {
            MapFrameKind.COMMUNITY_AND_PROJECT -> listOfNotNull(community, project)
            MapFrameKind.COMMUNITY -> listOfNotNull(community)
            MapFrameKind.AREAS -> listOf(GeometryRef("area_of_influence", "boundary", request.areas), project)
            MapFrameKind.INTEREST_AND_CONCESSIONS ->
                listOf(
                    GeometryRef("project", "interest_area", listOf(request.project)),
                    project,
                    GeometryRef("mining_concession", "boundary", request.concessions)
                )
        }
    }
}
