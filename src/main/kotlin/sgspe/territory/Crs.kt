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

    // la zona UTM (17, 18 o 19) de un SRID UTM sur del catálogo, WGS 84 o PSAD56; null para 4326 y para todo SRID que no
    // sea del catálogo (por ejemplo 32716 o 32720)
    fun zone(srid: Int): Int? =
        when (srid) {
            in WORKING_ZONES -> srid - 32700
            in PSAD56 -> srid - 24860
            else -> null
        }

    // un SRID UTM del catálogo, en el que se puede medir: WGS 84 UTM 17S a 19S o PSAD56 UTM 17S a 19S
    fun isUtm(srid: Int): Boolean = zone(srid) != null

    // una zona de trabajo (WGS 84 UTM 17S a 19S): la única en la que se mide; PSAD56 es solo de entrada (ADR-0002)
    fun isWorkingZone(srid: Int): Boolean = srid in WORKING_ZONES

    // cualquier SRID del catálogo: los UTM y 4326
    fun isCatalogued(srid: Int): Boolean = srid in KNOWN

    private val PSAD56 = setOf(24877, 24878, 24879)
    private val KNOWN = WORKING_ZONES + PSAD56 + WGS84
}
