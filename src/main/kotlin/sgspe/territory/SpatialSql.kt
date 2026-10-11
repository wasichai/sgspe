package sgspe.territory

object SpatialSql {
    // grados del margen del prefiltro en 4326: un grado de longitud mide al menos unos 105 km en el Perú (hasta 18.4° S), así
    // que contar 100 km por grado deja la envolvente un poco más grande de lo necesario, nunca más chica
    const val METERS_PER_DEGREE = 100_000.0

    fun degrees(meters: Number): Double = meters.toDouble() / METERS_PER_DEGREE
}
