package sgspe.territory

// los CRS de SGSPE (gis.md §3, ASM-002, ASM-064): los valores ENUM "EPSG 32717" de los catálogos "CRS de trabajo" y "CRS
// de origen" y su SRID. 327xx son WGS 84 UTM sur; 248xx, PSAD56 UTM sur, solo como origen al importar (RQ-GIS-007)
object Crs {
    const val WGS84 = 4326
    val WORKING_ZONES = setOf(32717, 32718, 32719)

    // "EPSG 32717" -> 32717; null si no es un valor del catálogo
    fun srid(value: String?): Int? =
        value
            ?.trim()
            ?.removePrefix("EPSG")
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in KNOWN }

    fun value(srid: Int): String = "EPSG $srid"

    // la zona UTM (17, 18 o 19) de un SRID UTM sur, WGS 84 o PSAD56; null para 4326
    fun zone(srid: Int): Int? =
        when (srid) {
            in 32701..32760 -> srid - 32700
            in 24877..24879 -> srid - 24860
            else -> null
        }

    private val KNOWN = setOf(32717, 32718, 32719, 24877, 24878, 24879, WGS84)
}
