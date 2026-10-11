package sgspe.territory

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.toCodedException
import wasichai.core.common.PageRequest
import wasichai.core.common.WasichaiException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

// los límites de las consultas espaciales (especificación de F6 §3.1, ASM-126), configurables por sgspe.territory.queries.*:
// el buffer en metros, la distancia en km, las filas de una consulta de la compuerta, los años de un periodo y las lecturas
// por relación (los conflictos de nearby-conflicts). fuera de rango, 400 OUT_OF_RANGE
@ConfigurationProperties("sgspe.territory.queries")
data class TerritoryQueryProperties(
    val minBufferM: Int = 1,
    val maxBufferM: Int = 20_000,
    val defaultBufferM: Int = 500,
    val minDistanceKm: BigDecimal = BigDecimal("0.1"),
    val maxDistanceKm: BigDecimal = BigDecimal("50"),
    val defaultDistanceKm: BigDecimal = BigDecimal("5"),
    val maxRows: Int = 5_000,
    val maxPeriodYears: Int = 5,
    val maxRelated: Int = 200
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TerritoryQueryProperties::class)
class TerritoryQueriesConfiguration

// un periodo de días de Lima, los dos extremos incluidos
data class Period(
    val start: LocalDate,
    val end: LocalDate
) {
    // el primer instante del periodo y el primero que ya no está, en Lima
    fun from(): Instant = start.atStartOfDay(LIMA).toInstant()

    fun until(): Instant = end.plusDays(1).atStartOfDay(LIMA).toInstant()
}

// los parámetros de las consultas espaciales: un valor que no se puede leer es 400 INVALID_VALUE y uno fuera de su rango, 400
// OUT_OF_RANGE, los dos con el parámetro en errors[0].field (P15)
object QueryParams {
    // null en `raw` toma `default` (el de la configuración, o null donde el buffer es opcional)
    fun bufferM(
        raw: String?,
        limits: TerritoryQueryProperties,
        default: Int? = limits.defaultBufferM
    ): Int? {
        val value = raw?.let { integer(BUFFER, it) } ?: return default
        if (value < limits.minBufferM || value > limits.maxBufferM) throw outOfRange(BUFFER, "de ${limits.minBufferM} a ${limits.maxBufferM} metros")
        return value
    }

    // distanceKm en metros
    fun distanceM(
        raw: String?,
        limits: TerritoryQueryProperties
    ): BigDecimal {
        val km = raw?.let { decimal(DISTANCE, it) } ?: limits.defaultDistanceKm
        if (km < limits.minDistanceKm || km > limits.maxDistanceKm) throw outOfRange(DISTANCE, "de ${limits.minDistanceKm} a ${limits.maxDistanceKm} km")
        return km.multiply(BigDecimal(1000))
    }

    fun limit(
        raw: String?,
        limits: TerritoryQueryProperties
    ): Int {
        val value = raw?.let { integer(LIMIT, it) } ?: return limits.maxRows
        if (value < 1 || value > limits.maxRows) throw outOfRange(LIMIT, "de 1 a ${limits.maxRows} filas")
        return value
    }

    // sin periodEnd, hoy; sin periodStart, un año antes del fin más un día (los 12 meses que terminan en él). a lo sumo
    // maxPeriodYears años
    fun period(
        start: String?,
        end: String?,
        today: LocalDate,
        limits: TerritoryQueryProperties
    ): Period {
        val to = end?.let { date(PERIOD_END, it) } ?: today
        val from = start?.let { date(PERIOD_START, it) } ?: to.minusYears(1).plusDays(1)
        if (from.isAfter(to)) throw outOfRange(PERIOD_START, "no puede ser posterior a periodEnd")
        if (!to.isBefore(
                from.plusYears(limits.maxPeriodYears.toLong())
            )
        ) {
            throw outOfRange(PERIOD_END, "el periodo no puede pasar de ${limits.maxPeriodYears} años")
        }
        return Period(from, to)
    }

    fun page(
        page: String?,
        size: String?
    ): PageRequest = PageRequest.of(page?.let { integer("page", it) }, size?.let { integer("size", it) })

    fun coordinate(
        name: String,
        raw: String?,
        min: Double,
        max: Double
    ): Double {
        val value = raw?.trim()?.toDoubleOrNull() ?: throw invalid(name, "debe ser un número")
        if (!value.isFinite() || value < min || value > max) throw outOfRange(name, "de $min a $max")
        return value
    }

    fun uuids(
        name: String,
        raws: List<String>?
    ): List<UUID> = raws.orEmpty().map { raw -> runCatching { UUID.fromString(raw.trim()) }.getOrElse { throw invalid(name, "debe ser un UUID") } }

    private fun integer(
        name: String,
        raw: String
    ): Int = raw.trim().toIntOrNull() ?: throw invalid(name, "debe ser un número entero")

    private fun decimal(
        name: String,
        raw: String
    ): BigDecimal = raw.trim().toBigDecimalOrNull() ?: throw invalid(name, "debe ser un número")

    private fun date(
        name: String,
        raw: String
    ): LocalDate =
        try {
            LocalDate.parse(raw.trim())
        } catch (_: DateTimeParseException) {
            throw invalid(name, "debe ser una fecha AAAA-MM-DD")
        }

    fun invalid(
        name: String,
        message: String
    ): WasichaiException = Finding("INVALID_VALUE", "$name $message", name, status = HttpStatus.BAD_REQUEST).toCodedException()

    fun outOfRange(
        name: String,
        message: String
    ): WasichaiException = Finding("OUT_OF_RANGE", "$name fuera de rango: $message", name, status = HttpStatus.BAD_REQUEST).toCodedException()

    private const val BUFFER = "bufferM"
    private const val DISTANCE = "distanceKm"
    private const val LIMIT = "limit"
    private const val PERIOD_START = "periodStart"
    private const val PERIOD_END = "periodEnd"
}
