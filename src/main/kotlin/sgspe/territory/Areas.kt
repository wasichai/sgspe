package sgspe.territory

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.WriteMark
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// el área oficial en ha (RQ-GIS-009, ASM-057, ASM-064): el área plana de la geometría, en el working_crs del proyecto
// para area_of_influence, y en la zona UTM del centroide para community, land_parcel y mining_concession, que no
// pertenecen a un solo proyecto. un área de influencia de un proyecto sin working_crs usa también la zona del centroide;
// un centroide fuera de las zonas 17S a 19S, o sin centroide (geometría vacía), deja el área vacía; una geometría que
// PostGIS no puede medir (InvalidGeometryException) no cambia el valor guardado. toda la cuenta pasa por GeoSql con la geometría enlazada
@Component
class Areas(
    private val records: Records,
    private val geo: GeoSql,
    private val transactions: Transactions
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun compute(
        objectName: String,
        record: RecordResponse
    ): BigDecimal? {
        val field = GEOMETRY[objectName] ?: return null
        val geometry = Records.geometriesOf(record)[field] ?: return null
        val srid = (if (objectName == AREA_OF_INFLUENCE) projectSrid(record) else null) ?: geo.centroidZone(geometry) ?: return null
        return geo.areaHa(geometry, srid)
    }

    // recalcula y escribe calculated_area_ha con Records (la marca y el motivo), como quien escribió o como la plataforma.
    // escribe solo si el valor cambia: una escritura igual subiría updatedAt y dejaría una historia vacía, y el If-Match de
    // quien edita el registro fallaría sin conflicto real. la lectura y la escritura van dentro de
    // Transactions.locked("<objeto>:<id>"), así el listener y JOB-02 no se pisan. true si escribió.
    // la transacción: la API genérica no abre ninguna (api.md §3.6), así que cuando corre el listener la escritura del
    // cliente ya se confirmó y `locked` abre la suya, aparte: un fallo de PostGIS al medir (InvalidGeometryException) sale de
    // ese bloque, la revierte sin haber escrito nada, y se captura afuera. NO sirve dentro de la transacción de un comando:
    // un fallo de PostGIS la deja abortada (25P02) y lo que siga falla con un 500. un comando mide antes de abrir su
    // transacción (`measure`) y dentro solo escribe el valor ya medido (`write`). el listener no corre dentro de un comando:
    // sus escrituras llevan la marca y se omite
    suspend fun refresh(
        objectName: String,
        id: UUID,
        reason: String = REASON
    ): Boolean =
        try {
            transactions.locked("$objectName:$id") {
                val record = records.find(objectName, id)
                if (record == null) {
                    false
                } else {
                    val area = compute(objectName, record)
                    if (Values.same(record.attributes[FIELD], area)) {
                        false
                    } else {
                        records.patch(objectName, id, mapOf(FIELD to area), reason)
                        true
                    }
                }
            }
        } catch (e: InvalidGeometryException) {
            // PostGIS no puede medir esa geometría: el valor guardado se deja como está y no se escribe uno equivocado
            log.warn("Areas: no se midió el área de {} {}: {}", objectName, id, e.message)
            false
        }

    // mide el área de un registro ya leído, SIN transacción: para un comando que escribe después, dentro de la suya.
    // null: PostGIS no puede medir la geometría (InvalidGeometryException): el comando sigue y no escribe el área, que queda
    // como estaba; un `Measured` con valor null es un área vacía (sin zona, sin geometría) y sí se escribe
    suspend fun measure(
        objectName: String,
        record: RecordResponse
    ): Measured? = measuring(objectName, record.id) { compute(objectName, record) }

    // escribe un área ya medida, solo si cambió (P11). quien llama tiene el candado `<objeto>:<id>` y su transacción
    suspend fun write(
        objectName: String,
        id: UUID,
        measured: Measured,
        reason: String = REASON
    ): Boolean {
        val record = records.find(objectName, id) ?: return false
        if (Values.same(record.attributes[FIELD], measured.value)) return false
        records.patch(objectName, id, mapOf(FIELD to measured.value), reason)
        return true
    }

    private suspend fun projectSrid(record: RecordResponse): Int? {
        val project = Records.uuid(record.attributes["project"]) ?: return null
        // solo un CRS UTM de trabajo: GeoSql.areaHa rechaza los demás, y un valor ajeno al catálogo cae a la zona del centroide
        return Crs.srid(Values.text(records.find("project", project)?.attributes?.get("working_crs")))?.takeIf { Crs.isWorkingZone(it) }
    }

    // el resultado de medir: el valor (null es un área vacía)
    class Measured(
        val value: BigDecimal?
    )

    companion object {
        const val FIELD = "calculated_area_ha"

        private val MEASURE_LOG = LoggerFactory.getLogger(Areas::class.java)

        // null si PostGIS no puede medir la geometría; cualquier otro fallo sale
        internal suspend fun measuring(
            objectName: String,
            id: String,
            block: suspend () -> BigDecimal?
        ): Measured? =
            try {
                Measured(block())
            } catch (e: InvalidGeometryException) {
                MEASURE_LOG.warn("Areas: no se midió el área de {} {}: {}", objectName, id, e.message)
                null
            }

        // el motivo del listener; JOB-02 firma el suyo con DerivedFieldsJob.reason (AreaRecalculator.REASON)
        const val REASON = "derivado: área en ha"
        const val AREA_OF_INFLUENCE = "area_of_influence"
        val GEOMETRY = mapOf(AREA_OF_INFLUENCE to "boundary", "community" to "territory", "land_parcel" to "boundary", "mining_concession" to "boundary")
    }
}

// AreaListener (domain-model §7.5): cuando la API genérica crea o cambia uno de esos objetos, relee la geometría como quien
// escribió y escribe el área. las guardas y los listeners no ven geometrías (WAS-ADR-0040), por eso relee. si no puede,
// registra el error y deja el valor a DerivedFieldsJob; las escrituras con la marca (importaciones, el propio listener)
// ya traen su área. la escritura del listener no es la que el cliente condicionó con If-Match: IfMatchGuard la deja pasar
// por llevar la marca, y Versions tolera que el área sea lo único que cambió después de la versión que el cliente leyó
@Component
@Order(110)
class AreaListener(
    private val areas: Areas
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName !in Areas.GEOMETRY) return
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return
        if (change.after
                .orEmpty()
                .keys
                .any { it.startsWith("rel:") } || WriteMark.present()
        ) {
            return
        }
        try {
            areas.refresh(change.objectName, change.recordId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("AreaListener: no se actualizó el área de {} {}", change.objectName, change.recordId, e)
        }
    }
}

// el recalculador de JOB-02 para el área: los registros cambiados desde la vuelta anterior, o todos en la diaria (un
// cambio del working_crs de un proyecto lo recoge la vuelta diaria). un registro que falla no detiene a los demás; al final
// la vuelta lanza para que no se dé por buena (DerivedSweep)
@Component
class AreaRecalculator(
    private val records: Records,
    private val areas: Areas
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    // solo elige las organizaciones que recorre DerivedSweep (las que tienen este objeto), no qué se recalcula: eso lo
    // fija Areas.GEOMETRY en recalculate, que cubre los cuatro objetos
    override val sourceObject: String = "community"

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        var written = 0
        var failed = 0
        var seen = 0
        for (objectName in Areas.GEOMETRY.keys) {
            val criteria = since?.let { listOf(Criteria.updatedSince(it)) }.orEmpty()
            for (record in records.all(objectName, criteria = criteria)) {
                seen++
                try {
                    if (areas.refresh(objectName, UUID.fromString(record.id), REASON)) written++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    log.warn("AreaRecalculator: no se recalculó el área de {} {}", objectName, record.id, e)
                }
            }
        }
        check(failed == 0) { "no se recalculó el área de $failed de $seen registros (se escribieron $written)" }
        return written
    }

    companion object {
        // JOB-02 firma sus escrituras: «derivado: área en ha (JOB-02 DerivedFieldsJob)»
        val REASON = DerivedFieldsJob.reason("área en ha")
    }
}
