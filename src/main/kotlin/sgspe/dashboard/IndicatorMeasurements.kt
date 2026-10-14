package sgspe.dashboard

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.CommandAnswer
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.LIMA
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// 409 INDICATOR_NOT_CLOSABLE (api.md §3.3): errors[0] es indicator/INDICATOR_NOT_CLOSABLE y el problem+json suma el miembro
// `reason` (STANCE, NO_SCALAR_VALUE, UNAVAILABLE o NOT_PERIOD_BOUND), que agrega el controlador
class IndicatorNotClosable(
    val reason: String,
    message: String
) : WasichaiException(HttpStatus.CONFLICT, message, listOf(FieldViolation("indicator", "INDICATOR_NOT_CLOSABLE")))

// el cierre de un periodo (api.md §4.11, dashboards.md §10, ASM-136): congela en indicator_measurement la cifra del KPI
// calculada como quien cierra (KpiReaders.person), o ninguna. los KPI que leen QCS se calculan sin las confidenciales
// (decisión 25, R4): la cifra oficial nunca revela cuántas se excluyen. todo como la persona, con la marca de escritura de
// Records; el cálculo va antes del candado, y el candado por indicador, alcance e inicio cubre solo la comprobación y el
// alta, lo que el único compuesto no ve (dos mediciones del tenant, porque los NULL son distintos, WAS-ADR-0037)
@Service
class IndicatorMeasurements(
    private val records: Records,
    private val dashboards: Dashboards,
    private val readers: KpiReaders,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val clock: Clock
) {
    suspend fun close(
        indicatorId: UUID,
        body: Map<String, Any?>
    ): RecordResponse {
        val period = ClosingPeriod.parse(body)
        val reader = readers.person()
        val user = reader.user
        permissions.require(user, "Cerrar un periodo", Actions.CREATE to MEASUREMENT)
        // todo el tenant: solo un administrador, por ahora (decisión 10, R2)
        if (period.project == null && !user.isAdmin) {
            throw Finding("ACTION_REQUIRED", "Cerrar un periodo de todo el tenant exige ser administrador", "project", status = HttpStatus.FORBIDDEN)
                .toCodedException()
        }
        period.project?.let { records.get("project", it) }
        val code = Values.text(records.get(INDICATOR, indicatorId).attributes["code"]).orEmpty()
        // hoy en Lima: el periodo terminado se exige antes que cualquier razón por el código
        val today = LocalDate.now(clock.withZone(LIMA))
        if (!period.end.isBefore(today)) {
            throw Finding(
                "PERIOD_NOT_ENDED",
                "El periodo termina el ${period.end}: se cierra desde el día siguiente",
                "attributes.period_end_date",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        Closability.before(code, period, today)?.let { throw notClosable(it, code) }
        val value = dashboards.value(code, period.project?.let { listOf(it) }, period.start, period.end, reader, excludeConfidential = true)
        Closability.after(value)?.let { throw notClosable(it, code) }
        return transactions.locked("$MEASUREMENT:$indicatorId:${period.project ?: "tenant"}:${period.start}") {
            if (exists(indicatorId, period)) {
                throw Finding(
                    "MEASUREMENT_EXISTS",
                    "El periodo que empieza el ${period.start} ya está cerrado para este indicador y este alcance",
                    "attributes.period_start_date"
                ).toCodedException()
            }
            records.create(
                MEASUREMENT,
                mapOf(
                    INDICATOR to indicatorId.toString(),
                    "project" to period.project?.toString(),
                    "period_start_date" to period.start.toString(),
                    "period_end_date" to period.end.toString(),
                    "measured_value" to value.value,
                    "numerator" to value.numerator,
                    "denominator" to value.denominator,
                    "breakdown" to Closability.breakdown(value),
                    "closed_by_user_id" to user.userId.toString(),
                    "closed_by_user_email" to user.email
                ).filterValues { it != null },
                null
            )
        }
    }

    // la medición del mismo indicador, alcance e inicio, leída como quien cierra (la de un proyecto suyo o la del tenant)
    private suspend fun exists(
        indicatorId: UUID,
        period: ClosingPeriod
    ): Boolean =
        records
            .all(MEASUREMENT, mapOf(INDICATOR to indicatorId.toString()), listOf(Criteria.dateBetween("period_start_date", period.start, period.start)))
            .any { Records.uuid(it.attributes["project"]) == period.project }

    private fun notClosable(
        reason: String,
        code: String
    ) = IndicatorNotClosable(reason, "$code no se cierra ($reason): una cifra oficial es completa o no existe (ASM-136)")

    companion object {
        const val INDICATOR = "indicator"
        const val MEASUREMENT = "indicator_measurement"
    }
}

// api.md §4.11: POST /api/sgspe/indicators/{id}/measurements, con Idempotency-Key: un reenvío con la misma clave devuelve el
// 201 de la primera vez sin cerrar otra vez
@RestController
@RequestMapping("/api/sgspe/indicators/{id}/measurements")
class IndicatorMeasurementsController(
    private val measurements: IndicatorMeasurements,
    private val idempotency: Idempotency,
    private val problems: GlobalExceptionHandler
) {
    @PostMapping
    suspend fun close(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            val record = measurements.close(id, body)
            CommandAnswer(HttpStatus.CREATED, record, record.updatedAt, "/api/objects/${IndicatorMeasurements.MEASUREMENT}/records/${record.id}")
        }
    }

    @ExceptionHandler(IndicatorNotClosable::class)
    fun notClosable(e: IndicatorNotClosable): ProblemDetail = problems.handleWasichai(e).apply { setProperty("reason", e.reason) }
}
