package sgspe.engagement

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
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Instant
import java.util.UUID

// los conteos de asistentes de una interacción (RF-ENG-004, RQ-DSH-012): suma de represented_people_count de los
// participantes con attended, en total, por sexo y por ámbito, calculada desde cero (domain-model §7.4). FIELDS es la
// única lista de los siete conteos: los tableros la leen de aquí
object AttendanceCounts {
    val FIELDS =
        listOf(
            "attendees_total_count",
            "attendees_female_count",
            "attendees_male_count",
            "attendees_direct_area_count",
            "attendees_indirect_area_count",
            "attendees_regional_count",
            "attendees_national_count"
        )

    // los campos del participante de los que dependen los conteos
    private val SOURCES = listOf("engagement", "attended", "represented_people_count", "sex", "participant_scope")

    fun of(participants: List<Map<String, Any?>>): Map<String, Long> {
        val attended = participants.filter { Values.bool(it["attended"]) == true }

        fun sum(predicate: (Map<String, Any?>) -> Boolean): Long =
            attended.filter(predicate).sumOf {
                (
                    Values.decimal(it["represented_people_count"])?.toLong()
                        ?: 1L
                ).coerceAtLeast(1L)
            }

        fun scope(value: String): (Map<String, Any?>) -> Boolean = { Values.text(it["participant_scope"]) == value }
        return linkedMapOf(
            "attendees_total_count" to sum { true },
            "attendees_female_count" to sum { Values.text(it["sex"]) == "FEMENINO" },
            "attendees_male_count" to sum { Values.text(it["sex"]) == "MASCULINO" },
            "attendees_direct_area_count" to sum(scope("AIDS")),
            "attendees_indirect_area_count" to sum(scope("AIIS")),
            "attendees_regional_count" to sum(scope("REGIONAL")),
            "attendees_national_count" to sum(scope("NACIONAL"))
        )
    }

    // los conteos que cambian, para escribir solo eso
    fun changes(
        engagement: Map<String, Any?>,
        counts: Map<String, Long>
    ): Map<String, Any?> = counts.filter { (field, value) -> !Values.same(engagement[field], value) }

    // si el cambio de un participante puede mover algún conteo: un alta y una baja siempre; un cambio solo si toca un campo
    // del que dependen (una nota o un cargo no los mueve)
    fun affects(
        before: Map<String, Any?>?,
        after: Map<String, Any?>?
    ): Boolean = before == null || after == null || SOURCES.any { !Values.same(before[it], after[it]) }
}

// recalcula y escribe los conteos de una interacción con Records (la marca SgspeWrite y el motivo «derivado: conteos de
// asistentes»; JOB-02 le añade el sufijo «(JOB-02 DerivedFieldsJob)»), como quien escribió o como la plataforma. escribe
// solo si algún conteo cambia: una escritura igual subiría updatedAt y dejaría una historia vacía, y el If-Match de la
// persona que edita la interacción fallaría sin conflicto real. la lectura y la escritura van dentro de Transactions.locked("engagement:<id>"): dos participantes que
// entran a la vez se cuentan los dos
@Component
class AttendanceCountsWriter(
    private val records: Records,
    private val transactions: Transactions
) {
    // true si escribió. reason: el motivo de la historia; el listener manda REASON y JOB-02 el que nombra al trabajo
    suspend fun refresh(
        engagement: UUID,
        reason: String
    ): Boolean =
        transactions.locked("${EngagementWriteGuard.ENGAGEMENT}:$engagement") {
            val current = records.find(EngagementWriteGuard.ENGAGEMENT, engagement)
            if (current == null) {
                false
            } else {
                val participants = records.all(EngagementWriteGuard.PARTICIPANT, filters = mapOf("engagement" to engagement.toString()))
                val changes = AttendanceCounts.changes(current.attributes, AttendanceCounts.of(participants.map { it.attributes }))
                if (changes.isEmpty()) {
                    false
                } else {
                    records.patch(EngagementWriteGuard.ENGAGEMENT, engagement, changes, reason)
                    true
                }
            }
        }

    companion object {
        const val REASON = "derivado: conteos de asistentes"
    }
}

// AttendanceCountsListener (domain-model §7.5): al crear, cambiar o borrar un participante, recalcula los conteos de su
// interacción como quien escribió (un listener corre dentro de la petición: no puede ser la plataforma, y los derivados
// los puede escribir todo rol que puede actualizar). si no puede, registra el error y no hace fallar la escritura:
// DerivedFieldsJob corrige el valor en la hora siguiente. su escritura lleva la marca, así que DerivedFieldsKeeper no la
// toma por un PUT y EditAnyGuard la deja pasar en una interacción ajena (ASM-069)
@Component
@Order(100)
class AttendanceCountsListener(
    private val writer: AttendanceCountsWriter
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != EngagementWriteGuard.PARTICIPANT || change.kind == RecordChangeKind.TRANSITIONED) return
        if (!AttendanceCounts.affects(change.before, change.after)) return
        val engagements = setOfNotNull(Records.uuid(change.before?.get("engagement")), Records.uuid(change.after?.get("engagement")))
        for (engagement in engagements) {
            try {
                writer.refresh(engagement, AttendanceCountsWriter.REASON)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("AttendanceCountsListener: no se actualizaron los conteos de la interacción {}", engagement, e)
            }
        }
    }
}

// el recalculador de JOB-02 para los conteos: las interacciones cuyos participantes cambiaron desde la vuelta anterior, o
// todas en la vuelta diaria (un participante borrado solo lo ve la vuelta diaria)
@Component
class AttendanceCountsRecalculator(
    private val records: Records,
    private val writer: AttendanceCountsWriter
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = EngagementWriteGuard.ENGAGEMENT

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val engagements =
            if (since == null) {
                records.all(EngagementWriteGuard.ENGAGEMENT).map { UUID.fromString(it.id) }
            } else {
                records
                    .all(EngagementWriteGuard.PARTICIPANT, criteria = listOf(Criteria.updatedSince(since)))
                    .mapNotNull { Records.uuid(it.attributes["engagement"]) }
                    .distinct()
            }
        var written = 0
        var failed = 0
        for (engagement in engagements) {
            try {
                if (writer.refresh(engagement, REASON)) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("AttendanceCountsRecalculator: no se recalcularon los conteos de la interacción {}", engagement, e)
            }
        }
        check(failed == 0) { "no se recalcularon los conteos de $failed de ${engagements.size} interacciones (se escribieron $written)" }
        return written
    }

    companion object {
        // JOB-02 firma sus escrituras: «derivado: conteos de asistentes (JOB-02 DerivedFieldsJob)»
        val REASON = DerivedFieldsJob.reason("conteos de asistentes")
    }
}
